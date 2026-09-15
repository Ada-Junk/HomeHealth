package com.example.homehealth.data.remote

import android.util.Log
import com.example.homehealth.data.SettingsPrefs
import com.example.homehealth.data.remote.dto.AnthropicResponse
import com.example.homehealth.data.remote.dto.ChatCompletionRequest
import com.example.homehealth.data.remote.dto.ChatCompletionResponse
import com.example.homehealth.data.remote.dto.ChatMessage
import com.example.homehealth.data.remote.dto.ContentPart
import com.example.homehealth.data.remote.dto.ParsedRecord
import com.example.homehealth.domain.model.LlmCallRecord
import com.example.homehealth.domain.model.LlmScene
import com.example.homehealth.domain.model.ParseResult
import com.example.homehealth.domain.repository.LlmCallLogRepository
import com.example.homehealth.util.DateUtils
import com.example.homehealth.util.HealthTypes
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.net.SocketTimeoutException
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random

/**
 * 通用 LLM 客户端：
 * - 报告解析 → 视觉模型（visionModels，支持图片输入），配置取自「报告解析服务」；
 * - 健康问答 → 文本模型（chatModels），配置取自「健康问答服务」。
 * 支持两种协议：OpenAI 兼容 chat/completions、Anthropic Messages API。
 *
 * 每次调用会经 [LlmCallLogRepository] 落一条可观测性日志（供应商 / 模型 / 耗时 / 字符数 /
 * 重试次数 / 失败类型）。日志里**不含提示词与回复内容** —— 那是用户的体检数据。
 */
@Singleton
class LlmClient @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val settingsPrefs: SettingsPrefs,
    private val gson: Gson,
    private val callLogRepository: LlmCallLogRepository
) {

    /** 模型回复：正文 + 思考过程（深度思考模型才有，可为空） */
    data class LlmAnswer(
        val text: String,
        val thinking: String? = null
    )

    /** 解析后的调用配置 */
    private data class ResolvedConfig(
        val providerId: String,
        val baseUrl: String,
        val apiKey: String,
        val model: String,
        val protocol: String
    )

    // ---------- 配置解析 ----------

    /** 解析服务是否已配置（供应商 + Key） */
    fun parseConfigured(): Boolean {
        val provider = settingsPrefs.parseProvider
        if (provider == LlmProviders.LOCAL) return false
        return settingsPrefs.parseApiKey.isNotBlank()
    }

    /** 问答服务是否已配置 */
    fun qaConfigured(): Boolean {
        val provider = settingsPrefs.qaProvider
        if (provider == LlmProviders.LOCAL) return false
        return settingsPrefs.qaApiKey.isNotBlank()
    }

    /**
     * 读取并校验调用配置。
     * vision=true（报告解析）：必须使用供应商的视觉模型，纯文本模型会被拒绝；
     * vision=false（健康问答）：使用文本模型。
     */
    private fun resolveConfig(vision: Boolean): ResolvedConfig {
        val provider = if (vision) settingsPrefs.parseProvider else settingsPrefs.qaProvider
        val apiKey = (if (vision) settingsPrefs.parseApiKey else settingsPrefs.qaApiKey).trim()
        val customModel = if (vision) settingsPrefs.parseModel else settingsPrefs.qaModel

        val preset = LlmProviders.byId(provider)
            ?: throw IllegalStateException("服务供应商配置无效，请在「设置」中重新选择")
        val model = customModel.ifBlank {
            if (vision) preset.visionModels.firstOrNull() else preset.chatModels.firstOrNull()
        }.orEmpty()

        if (apiKey.isBlank()) {
            // 区分「从未填写」与「填过但解不开」：后者若提示"未填写"，
            // 用户会反复重填同一把 Key 而找不到原因
            val unreadable =
                if (vision) settingsPrefs.parseKeyUnreadable else settingsPrefs.qaKeyUnreadable
            throw IllegalStateException(
                if (unreadable) {
                    "已保存的 API Key 无法解密（系统密钥库已失效），请重新填写"
                } else {
                    "API Key 未填写，请在「设置」中配置"
                }
            )
        }

        // 视觉 / 文本模型区分校验：纯文本模型无法识别报告图片
        if (vision) {
            if (preset.visionModels.isEmpty()) {
                throw IllegalStateException(
                    "「${preset.name}」没有可用的视觉模型，请在「报告解析服务」中更换供应商"
                )
            }
            if (model in preset.chatModels && model !in preset.visionModels) {
                throw IllegalStateException(
                    "「$model」是文本模型，无法识别报告图片；请在「报告解析服务」中选择视觉模型"
                )
            }
        }

        return ResolvedConfig(
            providerId = provider,
            baseUrl = preset.baseUrl,
            apiKey = apiKey,
            model = model,
            protocol = preset.protocol
        )
    }

    // ---------- 核心调用 ----------

    /** 服务端返回非 2xx 时抛出，携带状态码用于判断是否值得重试 */
    private class HttpStatusException(val code: Int, message: String) : Exception(message)

    /** 仅超时/连接类 IO 异常、429、5xx 值得重试；其余 4xx 是配置错误，重试没有意义 */
    private fun isRetryable(e: Throwable): Boolean = when (e) {
        is HttpStatusException -> e.code == 429 || e.code in 500..599
        is IOException -> true
        else -> false
    }

    /**
     * 指数退避重试（含抖动）。
     * 用 delay 而非 Thread.sleep —— 调用方在协程中，阻塞线程会拖垮 Dispatchers.IO 线程池；
     * 协程取消（CancellationException）必须原样抛出，不能被重试逻辑吞掉。
     */
    private suspend fun <T> withRetry(maxAttempts: Int = 3, block: () -> T): T {
        var attempt = 0
        while (true) {
            try {
                return block()
            } catch (e: Throwable) {
                if (e is CancellationException) throw e
                attempt++
                if (attempt >= maxAttempts || !isRetryable(e)) throw e
                // 500ms → 1s → 2s，叠加 0-250ms 抖动，避免多个请求同时重试
                delay((500L shl (attempt - 1)) + Random.nextLong(0, 250))
                Log.w(TAG, "LLM 调用失败，第 $attempt 次重试（上限 $maxAttempts）：${e.message}")
            }
        }
    }

    /** 按协议分发调用，返回模型输出的正文与思考过程；同时落一条可观测性日志 */
    private suspend fun chatCompletion(
        baseUrl: String,
        apiKey: String,
        model: String,
        messages: List<ChatMessage>,
        temperature: Double,
        maxTokens: Int,
        protocol: String,
        scene: String,
        providerId: String,
        maxAttempts: Int = RETRY_ATTEMPTS_QA
    ): LlmAnswer = withContext(Dispatchers.IO) {
        // 提示词字符数只统计文本部分：图片 base64 会把统计完全淹没
        val promptChars = messages.sumOf { message ->
            when (val content = message.content) {
                is String -> content.length
                is List<*> -> content.filterIsInstance<ContentPart>().sumOf { it.text?.length ?: 0 }
                else -> 0
            }
        }
        val hasImage = messages.any { message ->
            (message.content as? List<*>)?.any { (it as? ContentPart)?.imageUrl != null } == true
        }
        val startedAt = System.currentTimeMillis()
        var attempts = 0
        var completionChars = 0

        try {
            val answer = withRetry(maxAttempts) {
                attempts++
                if (protocol == LlmProviders.PROTOCOL_ANTHROPIC) {
                    executeAnthropic(baseUrl, apiKey, model, messages, temperature, maxTokens)
                } else {
                    executeOpenAi(baseUrl, apiKey, model, messages, temperature, maxTokens)
                }
            }
            completionChars = answer.text.length
            recordCall(
                provider = providerId, model = model, scene = scene, startedAt = startedAt,
                promptChars = promptChars, completionChars = completionChars,
                hasImage = hasImage, attempts = attempts, ok = true, errorType = null
            )
            answer
        } catch (e: Throwable) {
            // 协程取消是用户主动行为，不该记成一次失败调用
            if (e !is CancellationException) {
                recordCall(
                    provider = providerId, model = model, scene = scene, startedAt = startedAt,
                    promptChars = promptChars, completionChars = completionChars,
                    hasImage = hasImage, attempts = attempts, ok = false,
                    errorType = classifyError(e)
                )
            }
            throw e
        }
    }

    /** 错误归类：只留可聚合的短标签，方便统计"失败集中在哪" */
    private fun classifyError(e: Throwable): String = when {
        e is HttpStatusException -> when {
            e.code == 429 -> "http_429"
            e.code in 500..599 -> "http_5xx"
            else -> "http_${e.code}"
        }
        e is SocketTimeoutException -> "timeout"
        e is IOException -> "io"
        e.message == TRUNCATED_HINT -> "truncated"
        else -> e.javaClass.simpleName
    }

    /** 落日志。观测组件坏了绝不能拖垮主流程 —— 解析/问答照常进行 */
    private suspend fun recordCall(
        provider: String,
        model: String,
        scene: String,
        startedAt: Long,
        promptChars: Int,
        completionChars: Int,
        hasImage: Boolean,
        attempts: Int,
        ok: Boolean,
        errorType: String?
    ) {
        runCatching {
            callLogRepository.record(
                LlmCallRecord(
                    provider = provider,
                    model = model,
                    scene = scene,
                    latencyMs = System.currentTimeMillis() - startedAt,
                    promptChars = promptChars,
                    completionChars = completionChars,
                    hasImage = hasImage,
                    attempts = attempts,
                    ok = ok,
                    errorType = errorType,
                    createdAt = System.currentTimeMillis()
                )
            )
        }
    }

    /** 配置类失败（Key 未填 / 供应商无效）发生在真正发请求之前，也记一条便于统计 */
    private suspend fun recordConfigFailure(scene: String, providerId: String, model: String, e: Exception) {
        recordCall(
            provider = providerId, model = model, scene = scene,
            startedAt = System.currentTimeMillis(),
            promptChars = 0, completionChars = 0, hasImage = false,
            attempts = 0, ok = false, errorType = "config"
        )
    }

    /** OpenAI 兼容协议：POST {base}/chat/completions */
    private fun executeOpenAi(
        baseUrl: String,
        apiKey: String,
        model: String,
        messages: List<ChatMessage>,
        temperature: Double,
        maxTokens: Int
    ): LlmAnswer {
        val url = baseUrl.trimEnd('/') + "/chat/completions"
        val body = gson.toJson(
            ChatCompletionRequest(model, messages, temperature, maxTokens)
        ).toRequestBody("application/json; charset=utf-8".toMediaType())

        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $apiKey")
            .post(body)
            .build()

        return okHttpClient.newCall(request).execute().use { resp ->
            val text = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                throw HttpStatusException(resp.code, "HTTP ${resp.code}：${extractErrMsg(text)}")
            }
            val parsed = runCatching {
                gson.fromJson(text, ChatCompletionResponse::class.java)
            }.getOrNull()
            val choice = parsed?.choices?.firstOrNull()
            val message = choice?.message
            val content = message?.content
                ?: throw IllegalStateException(
                    parsed?.error?.message ?: "模型响应为空，请检查 API Key 与模型名称是否正确"
                )
            // 输出被长度上限截断：JSON 必然不完整，必须给出可诊断的提示，
            // 否则上层只会报「未能识别指标」，把模型问题误报成用户拍照问题
            if (choice.finishReason == "length") {
                throw IllegalStateException(TRUNCATED_HINT)
            }
            // 深度思考模型：思考过程在 reasoning_content（DeepSeek/智谱）或 thinking 字段
            val thinking = message.reasoningContent?.takeIf { it.isNotBlank() }
                ?: message.thinking?.takeIf { it.isNotBlank() }
            LlmAnswer(content, thinking)
        }
    }

    /** Anthropic Messages API：POST {base}/v1/messages（system 独立传参，图片用 base64 source） */
    private fun executeAnthropic(
        baseUrl: String,
        apiKey: String,
        model: String,
        messages: List<ChatMessage>,
        temperature: Double,
        maxTokens: Int
    ): LlmAnswer {
        val url = baseUrl.trimEnd('/') + "/v1/messages"
        val system = messages.filter { it.role == "system" }
            .mapNotNull { it.content as? String }
            .joinToString("\n")
        val converted = messages.filter { it.role != "system" }.map { m ->
            mapOf("role" to m.role, "content" to toAnthropicContent(m.content))
        }

        fun requestBody(withTemperature: Boolean): RequestBody {
            val bodyMap = buildMap<String, Any?> {
                put("model", model)
                put("max_tokens", maxTokens)
                if (withTemperature) put("temperature", temperature)
                put("messages", converted)
                if (system.isNotBlank()) put("system", system)
            }
            return gson.toJson(bodyMap)
                .toRequestBody("application/json; charset=utf-8".toMediaType())
        }

        fun post(withTemperature: Boolean): Pair<Int, String> {
            val request = Request.Builder()
                .url(url)
                .header("x-api-key", apiKey)
                .header("anthropic-version", "2023-06-01")
                .post(requestBody(withTemperature))
                .build()
            return okHttpClient.newCall(request).execute().use { resp ->
                resp.code to resp.body?.string().orEmpty()
            }
        }

        var (code, text) = post(withTemperature = true)
        // Claude 启用扩展思考时要求 temperature 必须为 1（或不传），被拒时降级为不传该参数重试一次
        if (code == 400 && text.contains("temperature", ignoreCase = true)) {
            Log.w(TAG, "Anthropic 拒绝 temperature 参数，降级为不传该参数重试")
            val fallback = post(withTemperature = false)
            code = fallback.first
            text = fallback.second
        }

        if (code !in 200..299) {
            throw HttpStatusException(code, "HTTP $code：${extractErrMsg(text)}")
        }

        val parsed = runCatching {
            gson.fromJson(text, AnthropicResponse::class.java)
        }.getOrNull()
        // 输出被长度上限截断：JSON 必然不完整，给出可诊断的提示
        if (parsed?.stopReason == "max_tokens") {
            throw IllegalStateException(TRUNCATED_HINT)
        }
        val blocks = parsed?.content.orEmpty()
        // Anthropic 扩展思考：type=thinking 块为思考过程，text 块为正文
        val answer = blocks.filter { it.type == "text" }
            .joinToString("") { it.text.orEmpty() }
        val thinking = blocks.filter { it.type == "thinking" }
            .joinToString("\n") { it.text.orEmpty() }
            .takeIf { it.isNotBlank() }
        if (answer.isBlank()) {
            throw IllegalStateException(
                parsed?.error?.message ?: "模型响应为空，请检查 API Key 与模型名称是否正确"
            )
        }
        return LlmAnswer(answer, thinking)
    }

    /** 把 OpenAI 风格 content（String 或 ContentPart 列表）转为 Anthropic content 数组 */
    private fun toAnthropicContent(content: Any): List<Map<String, Any?>> = when (content) {
        is String -> listOf(mapOf("type" to "text", "text" to content))
        is List<*> -> content.filterIsInstance<ContentPart>().map { part ->
            if (part.type == "text") {
                mapOf("type" to "text", "text" to (part.text ?: ""))
            } else {
                val dataUri = part.imageUrl?.url.orEmpty()
                val mediaType = dataUri.removePrefix("data:")
                    .substringBefore(';')
                    .ifBlank { "image/jpeg" }
                val base64 = dataUri.substringAfter("base64,", "")
                mapOf(
                    "type" to "image",
                    "source" to mapOf(
                        "type" to "base64",
                        "media_type" to mediaType,
                        "data" to base64
                    )
                )
            }
        }
        else -> listOf(mapOf("type" to "text", "text" to content.toString()))
    }

    private fun extractErrMsg(body: String): String = try {
        val type = object : TypeToken<Map<String, Any?>>() {}.type
        val map: Map<String, Any?> = gson.fromJson(body, type)
        val err = map["error"] as? Map<*, *>
        (err?.get("message") as? String) ?: body.take(150)
    } catch (_: Exception) {
        body.take(150)
    }

    // ---------- 业务：报告解析 ----------

    /** 用视觉模型解析体检报告图片，提取全部支持的指标 */
    suspend fun parseHealthDocument(imageBase64: String): ParseResult =
        withContext(Dispatchers.IO) {
            val cfg = try {
                resolveConfig(vision = true)
            } catch (e: Exception) {
                recordConfigFailure(LlmScene.PARSE, "配置失败", "", e)
                throw e
            }
            val today = DateUtils.formatDate(System.currentTimeMillis())

            // 动态生成全部可识别指标清单（含分组）
            val metricList = HealthTypes.GROUPS.joinToString("\n") { (key, groupName) ->
                val items = HealthTypes.byGroup(key).joinToString("、") { "${it.type}(${it.label})" }
                "  $groupName：$items"
            }

            val prompt = """
                你是一名专业的体检报告解析助手。请从这张体检报告/化验单图片中提取所有能识别到的健康指标。

                可识别的指标类型（type 必须严格使用下列英文标识）：
                $metricList

                输出要求：
                1. 严格只输出一个 JSON 对象，禁止输出任何解释、前后缀或 Markdown 代码块标记，格式：
                   {"records":[{"type":"blood_pressure","value":"120/80","numeric_value":120,"unit":"mmHg","date":"$today"}]}
                2. value 保留报告原始读数，血压用 "收缩压/舒张压"（如 "120/80"）；numeric_value 取数值（血压取收缩压，比率类去掉 % 号）
                3. date 格式 yyyy-MM-dd，取报告上标注的日期；没有日期时用 "$today"
                4. unit 使用报告上的单位；报告未标注时用：血压 mmHg、血糖 mmol/L、血脂 mmol/L、体重 kg、心率 bpm、体温 ℃
                5. 报告中不存在的指标不要输出；整张图无可识别指标时输出 {"records":[]}
                6. 同一指标多次出现（如不同日期复查）只取最近一次
            """.trimIndent()

            val content = chatCompletion(
                baseUrl = cfg.baseUrl,
                apiKey = cfg.apiKey,
                model = cfg.model,
                protocol = cfg.protocol,
                messages = listOf(
                    ChatMessage(
                        role = "user",
                        content = listOf(
                            ContentPart.text(prompt),
                            ContentPart.image(imageBase64)
                        )
                    )
                ),
                temperature = 0.1,
                maxTokens = 4096,
                scene = LlmScene.PARSE,
                providerId = cfg.providerId,
                // 解析场景刻意减少重试：readTimeout 为 120s，3 次超时会让用户在解析页干等 6 分钟以上
                maxAttempts = RETRY_ATTEMPTS_PARSE
            )

            ParseResult(parseRecordsJson(stripCodeBlock(content.text)), content.text.trim())
        }

    // ---------- 业务：健康问答 ----------

    /** 用文本模型结合成员健康记录回答问题（含思考过程，深度思考模型才有） */
    suspend fun askHealthQuestion(
        memberName: String,
        recordsSummary: String,
        question: String
    ): LlmAnswer = withContext(Dispatchers.IO) {
        val cfg = try {
            resolveConfig(vision = false)
        } catch (e: Exception) {
            recordConfigFailure(LlmScene.QA, "配置失败", "", e)
            throw e
        }

        val system = """
            你是「家庭健康管家」应用的健康问答助手。请基于提供的家庭成员健康记录回答问题。
            规则：
            1. 只依据记录中的数据分析，不要编造不存在的数值
            2. 回答简洁实用，涉及趋势时做简单分析，涉及参考范围的解释要通俗
            3. 涉及疾病诊断、用药调整时，提醒用户咨询医生
            4. 回答末尾固定附上：「以上内容由 AI 基于已保存记录生成，仅供参考，不构成医疗建议。」
        """.trimIndent()

        val user = """
            家庭成员：$memberName
            健康记录摘要（含参考范围与偏高偏低标注）：
            $recordsSummary

            用户问题：$question
        """.trimIndent()

        chatCompletion(
            baseUrl = cfg.baseUrl,
            apiKey = cfg.apiKey,
            model = cfg.model,
            protocol = cfg.protocol,
            messages = listOf(
                ChatMessage(role = "system", content = system),
                ChatMessage(role = "user", content = user)
            ),
            temperature = 0.3,
            maxTokens = 2048,
            scene = LlmScene.QA,
            providerId = cfg.providerId
        )
    }

    // ---------- 工具 ----------

    /**
     * 解析模型返回的 JSON（容忍 ```json 包裹与前后杂文字）。
     *
     * 先按括号配平扫描出所有顶层 JSON 对象，再逐个尝试解析，取第一个含 records 的结果。
     * 相比「首个 { 到末个 }」：后者在模型输出两段 JSON、或正文中夹带花括号时会截出非法串；
     * 而只取首个候选同样不够——正文里先出现 `{注}` 这类片段就会整体失败。
     *
     * 日志只记录结构化信息（长度 / 异常类型），**不记录响应内容**：响应里是用户的体检指标。
     */
    private fun parseRecordsJson(text: String): List<ParsedRecord> {
        val candidates = extractJsonObjects(text)
        if (candidates.isEmpty()) {
            Log.w(TAG, "模型响应中未找到完整 JSON 对象（响应长度 ${text.length} 字符）")
            return emptyList()
        }
        val type = object : TypeToken<Map<String, List<ParsedRecord>>>() {}.type
        for (json in candidates) {
            try {
                val map: Map<String, List<ParsedRecord>>? = gson.fromJson(json, type)
                if (map != null && map.containsKey("records")) return map["records"].orEmpty()
            } catch (e: Exception) {
                Log.w(TAG, "候选 JSON 解析失败：${e.javaClass.simpleName}（长度 ${json.length} 字符）")
            }
        }
        Log.w(TAG, "${candidates.size} 个候选 JSON 均未解析出 records 字段")
        return emptyList()
    }

    /** 按括号配平扫描出文本中所有顶层 JSON 对象（正确处理字符串内的引号与反斜杠转义） */
    private fun extractJsonObjects(text: String): List<String> {
        val results = mutableListOf<String>()
        var i = 0
        while (i < text.length) {
            if (text[i] != '{') {
                i++
                continue
            }
            var depth = 0
            var inString = false
            var escaped = false
            var end = -1
            var j = i
            while (j < text.length) {
                val c = text[j]
                when {
                    escaped -> escaped = false
                    inString && c == '\\' -> escaped = true
                    c == '"' -> inString = !inString
                    inString -> Unit
                    c == '{' -> depth++
                    c == '}' -> {
                        depth--
                        if (depth == 0) {
                            end = j
                            break
                        }
                    }
                }
                j++
            }
            if (end < 0) break // 未闭合，后续也不可能再构成完整对象
            results += text.substring(i, end + 1)
            i = end + 1
        }
        return results
    }

    private fun stripCodeBlock(text: String): String =
        text.trim()
            .removePrefix("```json").removePrefix("```JSON").removePrefix("```")
            .removeSuffix("```")
            .trim()

    companion object {
        private const val TAG = "LlmClient"

        /** 输出被长度上限截断时的统一提示（区别于"图片不清晰"，避免用户反复重拍） */
        private const val TRUNCATED_HINT = "模型输出达到长度上限被截断，请分张拍摄或减少输入后重试"

        /** 重试上限（含首次请求）。解析场景刻意只用 2 次，理由见 chatCompletion 调用处 */
        private const val RETRY_ATTEMPTS_PARSE = 2
        private const val RETRY_ATTEMPTS_QA = 3
    }
}

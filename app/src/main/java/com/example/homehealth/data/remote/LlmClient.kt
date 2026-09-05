package com.example.homehealth.data.remote

import com.example.homehealth.data.SettingsPrefs
import com.example.homehealth.data.remote.dto.AnthropicResponse
import com.example.homehealth.data.remote.dto.ChatCompletionRequest
import com.example.homehealth.data.remote.dto.ChatCompletionResponse
import com.example.homehealth.data.remote.dto.ChatMessage
import com.example.homehealth.data.remote.dto.ContentPart
import com.example.homehealth.data.remote.dto.ParsedRecord
import com.example.homehealth.domain.model.ParseResult
import com.example.homehealth.util.DateUtils
import com.example.homehealth.util.HealthTypes
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 通用 LLM 客户端：
 * - 报告解析 → 视觉模型（visionModels，支持图片输入），配置取自「报告解析服务」；
 * - 健康问答 → 文本模型（chatModels），配置取自「健康问答服务」。
 * 支持两种协议：OpenAI 兼容 chat/completions、Anthropic Messages API。
 */
@Singleton
class LlmClient @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val settingsPrefs: SettingsPrefs,
    private val gson: Gson
) {

    /** 模型回复：正文 + 思考过程（深度思考模型才有，可为空） */
    data class LlmAnswer(
        val text: String,
        val thinking: String? = null
    )

    /** 解析后的调用配置 */
    private data class ResolvedConfig(
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
        val customBaseUrl = if (vision) settingsPrefs.parseBaseUrl else settingsPrefs.qaBaseUrl

        val preset = LlmProviders.byId(provider)
        val baseUrl = (preset?.baseUrl ?: customBaseUrl).ifBlank { customBaseUrl }
        val model = customModel.ifBlank {
            if (vision) preset?.visionModels?.firstOrNull() else preset?.chatModels?.firstOrNull()
        } ?: customModel

        if (baseUrl.isBlank()) {
            throw IllegalStateException("服务地址为空，请在「设置」中填写")
        }
        if (model.isBlank()) {
            throw IllegalStateException("模型名称为空，请在「设置」中选择或填写")
        }
        if (apiKey.isBlank()) {
            throw IllegalStateException("API Key 未填写，请在「设置」中配置")
        }

        // 视觉 / 文本模型区分校验（自定义服务无法预判能力，跳过）
        if (vision && preset != null && preset.id != LlmProviders.CUSTOM) {
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
            baseUrl = baseUrl,
            apiKey = apiKey,
            model = model,
            protocol = preset?.protocol ?: LlmProviders.PROTOCOL_OPENAI
        )
    }

    // ---------- 核心调用 ----------

    /** 按协议分发调用，返回模型输出的正文与思考过程 */
    private suspend fun chatCompletion(
        baseUrl: String,
        apiKey: String,
        model: String,
        messages: List<ChatMessage>,
        temperature: Double,
        maxTokens: Int,
        protocol: String
    ): LlmAnswer = withContext(Dispatchers.IO) {
        if (protocol == LlmProviders.PROTOCOL_ANTHROPIC) {
            executeAnthropic(baseUrl, apiKey, model, messages, maxTokens)
        } else {
            executeOpenAi(baseUrl, apiKey, model, messages, temperature, maxTokens)
        }
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
                throw IllegalStateException("HTTP ${resp.code}：${extractErrMsg(text)}")
            }
            val parsed = runCatching {
                gson.fromJson(text, ChatCompletionResponse::class.java)
            }.getOrNull()
            val message = parsed?.choices?.firstOrNull()?.message
            val content = message?.content
                ?: throw IllegalStateException(
                    parsed?.error?.message ?: "模型响应为空，请检查 API Key 与模型名称是否正确"
                )
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
        maxTokens: Int
    ): LlmAnswer {
        val url = baseUrl.trimEnd('/') + "/v1/messages"
        val system = messages.filter { it.role == "system" }
            .mapNotNull { it.content as? String }
            .joinToString("\n")
        val converted = messages.filter { it.role != "system" }.map { m ->
            mapOf("role" to m.role, "content" to toAnthropicContent(m.content))
        }
        val bodyMap = buildMap<String, Any?> {
            put("model", model)
            put("max_tokens", maxTokens)
            put("messages", converted)
            if (system.isNotBlank()) put("system", system)
        }
        val body = gson.toJson(bodyMap)
            .toRequestBody("application/json; charset=utf-8".toMediaType())

        val request = Request.Builder()
            .url(url)
            .header("x-api-key", apiKey)
            .header("anthropic-version", "2023-06-01")
            .post(body)
            .build()

        return okHttpClient.newCall(request).execute().use { resp ->
            val text = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                throw IllegalStateException("HTTP ${resp.code}：${extractErrMsg(text)}")
            }
            val parsed = runCatching {
                gson.fromJson(text, AnthropicResponse::class.java)
            }.getOrNull()
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
            LlmAnswer(answer, thinking)
        }
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
            val cfg = resolveConfig(vision = true)
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
                maxTokens = 4096
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
        val cfg = resolveConfig(vision = false)

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
            maxTokens = 2048
        )
    }

    // ---------- 工具 ----------

    /** 解析模型返回的 JSON（容忍 ```json 包裹与前后杂文字） */
    private fun parseRecordsJson(text: String): List<ParsedRecord> {
        return try {
            val start = text.indexOf('{')
            val end = text.lastIndexOf('}')
            if (start < 0 || end <= start) return emptyList()
            val json = text.substring(start, end + 1)
            val type = object : TypeToken<Map<String, List<ParsedRecord>>>() {}.type
            val map: Map<String, List<ParsedRecord>> = gson.fromJson(json, type)
            map["records"].orEmpty()
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun stripCodeBlock(text: String): String =
        text.trim()
            .removePrefix("```json").removePrefix("```JSON").removePrefix("```")
            .removeSuffix("```")
            .trim()
}

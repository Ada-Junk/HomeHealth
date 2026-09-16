package com.example.homehealth.domain.tool

import com.example.homehealth.data.local.entity.FamilyMember
import com.google.gson.Gson
import com.google.gson.JsonObject

/**
 * 一个可被 ReAct 循环调用的工具。
 *
 * [description] 不是给开发者看的文档，而是**给模型看的调用依据**：
 * 写清"何时该调用"比写清"它做什么"更重要 —— 描述含糊时模型会跳过工具、凭记忆作答，
 * 而对健康数据来说，一段编出来的数值比一句"我不知道"危险得多。
 */
interface HealthTool {

    /** 工具名（两种协议共用，必须与 schema 里的 name 一致） */
    val name: String

    val description: String

    /** JSON Schema 字符串。两种协议的请求结构不同，但 schema 本身共用 */
    val parametersJsonSchema: String

    /**
     * 执行工具。
     *
     * **实现方必须自行容错**：入参解析失败、数据源异常都请在内部转成
     * [ToolResult.fail]，不要让异常冒泡 —— 循环会把失败结果作为 observation 交回模型，
     * 模型才有机会换个查法；直接抛出则会让整轮提问失败。
     */
    suspend fun execute(context: ToolContext, argsJson: String): ToolResult
}

/**
 * 本轮提问的上下文。
 *
 * 成员信息由这里传入而**不是让模型在参数里指定** —— 模型选错成员会答成另一个人的数据，
 * 而用户完全无从察觉。身份这类不可让渡的事实必须由代码绑定。
 *
 * @param imageBase64 本轮附带的报告图片（可为空；读图工具依赖它）
 * @param question 用户原始问题（部分工具用它做检索查询）
 */
data class ToolContext(
    val member: FamilyMember,
    val question: String,
    val imageBase64: String?
)

/** 工具执行结果。失败也带文本：模型需要看到原因才能换个方式重试 */
data class ToolResult(val ok: Boolean, val text: String) {
    companion object {
        fun ok(text: String) = ToolResult(ok = true, text = text)
        fun fail(text: String) = ToolResult(ok = false, text = text)
    }
}

/**
 * 工具集合的提供者。
 *
 * 抽成接口是为了让 `ReActAgent` 能在单测里用假工具跑完整循环 ——
 * "轮数用尽如何降级""工具连续失败如何禁用"这些策略只能靠循环级测试覆盖，
 * 而它们恰恰是最难用真机验证的部分。
 */
interface ToolProvider {
    val all: List<HealthTool>

    /** 本轮实际可用的工具（例如没有附图时不暴露读图工具） */
    fun availableFor(context: ToolContext): List<HealthTool>

    fun byName(name: String): HealthTool?
}

/**
 * 读图能力（由 LLM 层实现）。
 *
 * 抽成窄接口而不是让工具直接依赖整个 LLM 客户端：一来让 `ReadReportImageTool`
 * 能在 JVM 单测里构造（否则要凑齐 OkHttpClient / SettingsPrefs / 日志仓库…），
 * 二来这里需要的语义只是"把一张图读成文字"，不该暴露其余能力。
 */
interface VisionReader {
    suspend fun readImageText(imageBase64: String, focus: String?, question: String): String
}

/**
 * 工具入参的容错读取。
 *
 * 模型给出的 JSON 经常"差一点"：缺字段、类型不符、数组里混进 null。
 * 这里一律返回 null / 空集合而不抛异常 —— 参数错是模型可以被纠正的行为，
 * 不是需要中断流程的异常。
 */
object ToolArgs {

    private val gson = Gson()

    fun parse(argsJson: String): JsonObject? =
        runCatching { gson.fromJson(argsJson, JsonObject::class.java) }
            .getOrNull()
            ?.takeIf { it.isJsonObject }

    fun str(args: JsonObject?, key: String): String? =
        args?.get(key)
            ?.takeIf { it.isJsonPrimitive }
            ?.asString
            ?.trim()
            ?.takeIf { it.isNotEmpty() }

    fun int(args: JsonObject?, key: String): Int? =
        args?.get(key)
            ?.takeIf { it.isJsonPrimitive }
            ?.let { runCatching { it.asInt }.getOrNull() }

    fun strList(args: JsonObject?, key: String): List<String> =
        args?.get(key)
            ?.takeIf { it.isJsonArray }
            ?.asJsonArray
            ?.mapNotNull { element ->
                if (element.isJsonPrimitive) {
                    element.asString.trim().takeIf { it.isNotEmpty() }
                } else {
                    null
                }
            }
            .orEmpty()
}

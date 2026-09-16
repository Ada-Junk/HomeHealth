package com.example.homehealth.data.remote

import com.example.homehealth.data.remote.dto.AnthropicStreamDelta
import com.example.homehealth.data.remote.dto.AnthropicStreamEvent
import com.example.homehealth.data.remote.dto.ChatStreamChunk
import com.google.gson.Gson

/**
 * SSE（Server-Sent Events）流式响应解析。
 *
 * 单独抽成纯函数对象有两个理由：
 * 1. **可测**：协议解析是最容易出错、又最容易用单测覆盖的一层（无 Android / 网络依赖）。
 *    "某家供应商少回一个字段"这类问题不该靠真机抓包排查；
 * 2. **协议知识集中**：SSE 行格式、`[DONE]` 哨兵、两家供应商各自的增量字段名只在这一个文件里。
 *
 * 解析约定：
 * - 忽略 `event:` / `id:` / `retry:` 字段与注释行（以 `:` 开头），只取 `data:` 负载；
 * - Anthropic 的 `event:` 名与负载里的 `type` 字段是重复信息，按负载 `type` 分派即可，
 *   不需要维护「当前事件名 → 含义」的状态机 —— 少一处状态就少一类 bug；
 * - 假定一条 SSE 事件的数据在**单行**内（7 家供应商的流式响应均如此）。SSE 规范允许
 *   多行 `data:` 拼接，但为它引入行缓冲会让状态机复杂化，而收益为零；
 * - 任一负载解析失败都返回空结果而非抛异常：单个畸形分块不应中断整条回答。
 */
internal object LlmStreamParser {

    /** 一个 `data:` 负载解析出的增量。同一负载可能同时产出多项（故返回 List） */
    sealed interface Delta {
        /** 正文增量 */
        data class Content(val text: String) : Delta

        /** 思考过程增量（深度思考模型） */
        data class Thinking(val text: String) : Delta

        /**
         * 工具调用的一片。
         *
         * 两家协议都是**分片**的，不能"看到一段就当完整参数解析"：
         * OpenAI 在 `delta.tool_calls[]` 里按 `index` 分组，`arguments` 逐片拼接；
         * Anthropic 先用 `content_block_start` 给出 id / name，再用
         * `input_json_delta.partial_json` 逐片补参数。
         * 所有分片必须由 [ToolCallAssembler] 累积到流结束才构成合法 JSON。
         */
        data class ToolCallFragment(
            val index: Int,
            val id: String? = null,
            val name: String? = null,
            val argsFragment: String? = null
        ) : Delta

        /** 服务端在流内报错（HTTP 200 但负载里带 error） */
        data class Failure(val message: String) : Delta

        /** 输出被长度上限截断：已生成内容仍然有效，不应抛弃 */
        object Truncated : Delta

        /** 流正常结束 */
        object Done : Delta
    }

    private const val DATA_PREFIX = "data:"
    private const val COMMENT_PREFIX = ":"
    private const val DONE_SENTINEL = "[DONE]"

    private const val FINISH_LENGTH = "length"
    private const val TYPE_CONTENT_BLOCK_START = "content_block_start"
    private const val TYPE_CONTENT_BLOCK_DELTA = "content_block_delta"
    private const val TYPE_MESSAGE_DELTA = "message_delta"
    private const val TYPE_MESSAGE_STOP = "message_stop"
    private const val DELTA_TEXT = "text_delta"
    private const val DELTA_THINKING = "thinking_delta"
    private const val DELTA_INPUT_JSON = "input_json_delta"
    private const val BLOCK_TOOL_USE = "tool_use"
    private const val STOP_MAX_TOKENS = "max_tokens"

    private val SSE_FIELD_PREFIXES = listOf("event:", "id:", "retry:")

    /** 无状态解析，单例即可（构造 Gson 不便宜，不必每个分块新建） */
    private val gson = Gson()

    /**
     * 取出 `data:` 负载。
     *
     * @return 负载字符串；`null` 表示这一行不是数据行（空行 / 注释 / `event:` 等字段 / 空负载）
     */
    fun payloadOf(line: String): String? {
        val trimmed = line.trim()
        if (!trimmed.startsWith(DATA_PREFIX)) return null
        // SSE 允许 `data:xxx` 与 `data: xxx` 两种写法
        return trimmed.removePrefix(DATA_PREFIX).trim().ifEmpty { null }
    }

    /**
     * 该行是否属于**正常**的 SSE 帧结构（空行 / 注释 / `event:` / `id:` / `retry:`）。
     *
     * 调用方用它区分「结构性行」与「意外内容」：后者意味着服务端按 `text/event-stream`
     * 返回了别的东西（常见于 HTTP 200 但正文是 JSON 错误对象），值得在报错里带出来。
     */
    fun isStructuralLine(line: String): Boolean {
        val trimmed = line.trim()
        return trimmed.isEmpty() ||
            trimmed.startsWith(COMMENT_PREFIX) ||
            SSE_FIELD_PREFIXES.any { trimmed.startsWith(it) }
    }

    /** OpenAI 兼容 `chat/completions` 的流式分块 */
    fun parseOpenAi(payload: String): List<Delta> {
        if (payload == DONE_SENTINEL) return listOf(Delta.Done)

        val chunk = runCatching { gson.fromJson(payload, ChatStreamChunk::class.java) }
            .getOrNull() ?: return emptyList()
        chunk.error?.message?.takeIf { it.isNotBlank() }?.let { return listOf(Delta.Failure(it)) }

        val choice = chunk.choices?.firstOrNull() ?: return emptyList()
        val delta = choice.delta
        val out = ArrayList<Delta>(2)

        delta?.content?.takeIf { it.isNotEmpty() }?.let { out.add(Delta.Content(it)) }
        // 深度思考模型的思考过程：DeepSeek / 智谱用 reasoning_content，智谱另有 thinking
        val reasoning = delta?.reasoningContent?.takeIf { it.isNotEmpty() }
            ?: delta?.thinking?.takeIf { it.isNotEmpty() }
        if (reasoning != null) out.add(Delta.Thinking(reasoning))

        // 工具调用分片：id / name 通常只在首片出现，arguments 会跨多片
        delta?.toolCalls?.forEach { call ->
            out.add(
                Delta.ToolCallFragment(
                    index = call.index ?: 0,
                    id = call.id,
                    name = call.function?.name,
                    argsFragment = call.function?.arguments
                )
            )
        }

        // 截断标记与内容可能出现在同一分块，因此不能"二选一"返回
        if (choice.finishReason == FINISH_LENGTH) out.add(Delta.Truncated)
        return out
    }

    /** Anthropic Messages API 的流式事件 */
    fun parseAnthropic(payload: String): List<Delta> {
        val event = runCatching { gson.fromJson(payload, AnthropicStreamEvent::class.java) }
            .getOrNull() ?: return emptyList()
        event.error?.message?.takeIf { it.isNotBlank() }?.let { return listOf(Delta.Failure(it)) }

        val index = event.index ?: 0
        return when (event.type) {
            TYPE_CONTENT_BLOCK_START -> {
                val block = event.contentBlock
                // 工具块的 id / name 只在这里出现一次，后面全是参数分片
                if (block?.type == BLOCK_TOOL_USE && !block.name.isNullOrBlank()) {
                    listOf(Delta.ToolCallFragment(index = index, id = block.id, name = block.name))
                } else {
                    emptyList()
                }
            }
            TYPE_CONTENT_BLOCK_DELTA -> contentBlockDelta(index, event.delta)
            TYPE_MESSAGE_DELTA ->
                if (event.delta?.stopReason == STOP_MAX_TOKENS) listOf(Delta.Truncated) else emptyList()
            TYPE_MESSAGE_STOP -> listOf(Delta.Done)
            // message_start / content_block_stop / ping：与问答无关
            else -> emptyList()
        }
    }

    private fun contentBlockDelta(index: Int, d: AnthropicStreamDelta?): List<Delta> {
        if (d == null) return emptyList()
        return when (d.type) {
            DELTA_TEXT -> d.text?.takeIf { it.isNotEmpty() }?.let { listOf(Delta.Content(it)) }.orEmpty()
            DELTA_THINKING -> d.thinking?.takeIf { it.isNotEmpty() }?.let { listOf(Delta.Thinking(it)) }.orEmpty()
            DELTA_INPUT_JSON -> d.partialJson?.takeIf { it.isNotEmpty() }
                ?.let { listOf(Delta.ToolCallFragment(index = index, argsFragment = it)) }
                .orEmpty()
            // signature_delta 等与问答无关
            else -> emptyList()
        }
    }
}

/**
 * 工具调用分片累积器。
 *
 * 两家协议都按 `index` 分组返回分片：同一个 index 的多次出现要拼成一次调用，
 * id / name 只在首片给一次，`arguments` 逐片拼接。**必须累积到流结束**才能得到合法 JSON ——
 * 这是流式 function calling 最容易写错的地方。
 */
internal class ToolCallAssembler {

    private val ids = LinkedHashMap<Int, String>()
    private val names = LinkedHashMap<Int, String>()
    private val args = LinkedHashMap<Int, StringBuilder>()

    fun accept(fragment: LlmStreamParser.Delta.ToolCallFragment) {
        val index = fragment.index
        fragment.id?.takeIf { it.isNotBlank() }?.let { ids[index] = it }
        fragment.name?.takeIf { it.isNotBlank() }?.let { names[index] = it }
        fragment.argsFragment?.takeIf { it.isNotEmpty() }
            ?.let { args.getOrPut(index) { StringBuilder() }.append(it) }
    }

    fun isEmpty(): Boolean = names.isEmpty()

    /**
     * 产出完整调用。参数为空或拼不完整时退化成 `{}` ——
     * 工具侧的容错解析会把"缺参数"当成一次可纠正的失败，而不是让整轮提问崩掉。
     */
    fun build(): List<AgentToolCall> = names.entries
        .sortedBy { it.key }
        .map { (index, name) ->
            AgentToolCall(
                id = ids[index] ?: "call_$index",
                name = name,
                argsJson = args[index]?.toString()?.takeIf { it.isNotBlank() } ?: "{}"
            )
        }
}

package com.example.homehealth.data.remote

import com.example.homehealth.domain.tool.HealthTool

/**
 * 循环内累积的对话条目（**协议无关**）。
 *
 * 为什么不直接维护 `List<ChatMessage>`：两家协议回填工具结果的结构完全不同
 * —— OpenAI 是一条独立的 `role:"tool"` 消息并携带 `tool_call_id`；
 * Anthropic 则要求把 `tool_result` 块塞进 **user** 消息里。
 * 循环只表达"我调了哪个工具、结果是什么"，格式翻译全部由网关负责，
 * 这样循环本体不掺协议细节，也才能用假网关单测。
 */
sealed interface AgentEntry {
    data class System(val text: String) : AgentEntry
    data class User(val text: String, val imageBase64: String? = null) : AgentEntry
    data class AssistantToolCalls(val calls: List<AgentToolCall>, val text: String?) : AgentEntry
    data class ToolResults(val results: List<AgentToolResultEntry>) : AgentEntry
}

/** 模型请求的一次工具调用 */
data class AgentToolCall(val id: String, val name: String, val argsJson: String)

/** 一次工具执行结果，回填给模型 */
data class AgentToolResultEntry(
    val toolCallId: String,
    val name: String,
    val text: String,
    val ok: Boolean
)

/** 一轮 LLM 的产出 */
data class AgentTurnResult(
    /** 本轮产出的正文（可能是最终回答，也可能只是"我先查一下…"） */
    val text: String,
    val thinking: String?,
    /** 非空表示本轮要求调用工具，正文不是最终答案 */
    val toolCalls: List<AgentToolCall>,
    val truncated: Boolean
)

/** 循环对外的增量事件 */
sealed interface AgentEvent {
    data class Answer(val delta: String) : AgentEvent
    data class Thinking(val delta: String) : AgentEvent

    /** 开始执行某工具 */
    data class ToolCallStarted(val name: String, val argsSummary: String) : AgentEvent

    /** 工具返回；[summary] 是给界面看的短摘要（完整结果已回填给模型） */
    data class ToolCallFinished(val name: String, val ok: Boolean, val summary: String) : AgentEvent

    /**
     * 循环结束。
     * @param evidence 检索类工具返回的明细原文 —— 最终回答的「数据依据」直接复用它们，
     *   不再走第二条数据通路，保证依据与模型看到的内容逐字一致
     */
    data class Completed(
        val text: String,
        val thinking: String?,
        val truncated: Boolean,
        val evidence: List<String>,
        val toolNames: List<String>
    ) : AgentEvent
}

/**
 * 单轮 LLM 交互的抽象。
 *
 * 抽出接口的唯一目的是**让循环本体可测**：轮数用尽如何降级、工具连续失败如何禁用、
 * observation 如何截断，这些策略恰恰是最需要测试、又最难靠真机验证的部分。
 */
interface AgentLlmGateway {
    /**
     * 执行一轮。正文 / 思考通过回调上报；返回值给出本轮是否要求调用工具。
     * 抛异常表示本轮请求失败，由调用方决定是否整体降级。
     */
    suspend fun turn(
        entries: List<AgentEntry>,
        tools: List<HealthTool>,
        onDelta: suspend (String) -> Unit,
        onThinking: suspend (String) -> Unit
    ): AgentTurnResult
}

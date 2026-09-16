package com.example.homehealth.data.remote.dto

import com.google.gson.annotations.SerializedName

/**
 * OpenAI 兼容 chat/completions 请求（智谱/OpenAI/Gemini/DeepSeek 通用）。
 *
 * [stream] 为 true 时服务端以 `text/event-stream` 分块返回；为 null 时 Gson 不序列化该字段，
 * 报告解析走的仍是非流式单次请求-响应。
 */
data class ChatCompletionRequest(
    val model: String,
    val messages: List<ChatMessage>,
    val temperature: Double? = null,
    @SerializedName("max_tokens") val maxTokens: Int? = null,
    val stream: Boolean? = null
)

/** 消息（content 为 String 或 List<ContentPart>） */
data class ChatMessage(
    val role: String,
    val content: Any
)

/** 多模态内容片段 */
data class ContentPart(
    val type: String,
    val text: String? = null,
    @SerializedName("image_url") val imageUrl: ImageUrl? = null
) {
    companion object {
        fun text(text: String) = ContentPart(type = "text", text = text)

        fun image(base64: String) = ContentPart(
            type = "image_url",
            imageUrl = ImageUrl("data:image/jpeg;base64,$base64")
        )
    }
}

data class ImageUrl(val url: String)

/** 响应 */
data class ChatCompletionResponse(
    val choices: List<ChatChoice>? = null,
    val error: ChatError? = null
)

data class ChatChoice(
    val index: Int? = null,
    val message: ChatRespMessage? = null,
    @SerializedName("finish_reason") val finishReason: String? = null
)

data class ChatRespMessage(
    val role: String? = null,
    val content: String? = null,
    /** 深度思考模型的思考过程（DeepSeek / 智谱 GLM 深度思考返回） */
    @SerializedName("reasoning_content") val reasoningContent: String? = null,
    /** 部分供应商（如智谱）使用 thinking 字段返回思考过程 */
    val thinking: String? = null,
    /**
     * 工具调用（function calling）。
     * 非流式落在 `message.tool_calls`；流式落在 `delta.tool_calls`，
     * 且 `function.arguments` 是**按 index 分片**返回的字符串，必须累积到流结束才是合法 JSON。
     */
    @SerializedName("tool_calls") val toolCalls: List<ChatToolCall>? = null
)

data class ChatToolCall(
    /** 流式下用它把分片归组；非流式一般为空 */
    val index: Int? = null,
    val id: String? = null,
    val type: String? = null,
    val function: ChatToolFunction? = null
)

data class ChatToolFunction(
    val name: String? = null,
    val arguments: String? = null
)

data class ChatError(
    val code: String? = null,
    val message: String? = null
)

/** Anthropic Messages API 响应（content 为分块数组，拼接 type=text 的块） */
data class AnthropicResponse(
    val content: List<AnthropicContentPart>? = null,
    /** 停止原因：max_tokens 表示输出被长度上限截断，需与本应用已解析出的字段区分处理 */
    @SerializedName("stop_reason") val stopReason: String? = null,
    val error: ChatError? = null
)

data class AnthropicContentPart(
    val type: String? = null,
    val text: String? = null
)

// ---------- 流式（SSE）分块 ----------

/**
 * OpenAI 兼容流式分块。与 [ChatCompletionResponse] 的差别：
 * 内容在 `choices[0].delta` 而非 `choices[0].message`，且每个分块可能只带一个字段。
 * delta 复用 [ChatRespMessage]（同为 role/content/reasoning_content/thinking 结构）。
 */
data class ChatStreamChunk(
    val choices: List<ChatStreamChoice>? = null,
    val error: ChatError? = null
)

data class ChatStreamChoice(
    val index: Int? = null,
    val delta: ChatRespMessage? = null,
    /** 末块携带；"length" 表示输出被长度上限截断 */
    @SerializedName("finish_reason") val finishReason: String? = null
)

/**
 * Anthropic 流式事件。Anthropic 的 `event:` 行与 `data:` 行内容重复（type 也在负载里），
 * 因此解析只读 `data:` 负载、按 [type] 分派即可，无需维护事件名状态机。
 *
 * 与问答相关的三类：
 * - `content_block_delta` → [delta] 的 `text_delta` / `thinking_delta`
 * - `message_delta` → [delta] 的 `stop_reason`（`max_tokens` 表示截断）
 * - `error` → [error]
 * `message_start` / `content_block_start` / `ping` 等与纯文本问答无关，忽略。
 */
data class AnthropicStreamEvent(
    val type: String? = null,
    val index: Int? = null,
    val delta: AnthropicStreamDelta? = null,
    /** `content_block_start` 携带：工具调用的 id 与 name 从这里来 */
    @SerializedName("content_block") val contentBlock: AnthropicContentBlock? = null,
    val error: ChatError? = null
)

/** `content_block_start` 的内容块（工具调用时 type=tool_use） */
data class AnthropicContentBlock(
    val type: String? = null,
    val id: String? = null,
    val name: String? = null
)

/** 同时承载 content_block_delta 的 text/thinking/partial_json 与 message_delta 的 stop_reason */
data class AnthropicStreamDelta(
    val type: String? = null,
    val text: String? = null,
    val thinking: String? = null,
    /** 工具调用参数的增量（`type=input_json_delta`），需按 index 累积 */
    @SerializedName("partial_json") val partialJson: String? = null,
    @SerializedName("stop_reason") val stopReason: String? = null
)

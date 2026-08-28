package com.example.homehealth.data.remote.dto

import com.google.gson.annotations.SerializedName

/** OpenAI 兼容 chat/completions 请求（智谱/OpenAI/Gemini/DeepSeek 通用） */
data class ChatCompletionRequest(
    val model: String,
    val messages: List<ChatMessage>,
    val temperature: Double? = null,
    @SerializedName("max_tokens") val maxTokens: Int? = null
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
    val thinking: String? = null
)

data class ChatError(
    val code: String? = null,
    val message: String? = null
)

/** Anthropic Messages API 响应（content 为分块数组，拼接 type=text 的块） */
data class AnthropicResponse(
    val content: List<AnthropicContentPart>? = null,
    val error: ChatError? = null
)

data class AnthropicContentPart(
    val type: String? = null,
    val text: String? = null
)

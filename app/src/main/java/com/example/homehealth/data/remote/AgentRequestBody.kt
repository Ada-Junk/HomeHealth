package com.example.homehealth.data.remote

import com.example.homehealth.domain.tool.HealthTool
import com.google.gson.Gson
import com.google.gson.JsonObject

/**
 * 把协议无关的 [AgentEntry] 翻译成某家协议的请求体。
 *
 * 做成**纯函数**（不碰网络）是有意的：这类结构如果写错，表现只是"模型不调用工具"
 * 或"第二轮请求 400"，真机排查成本极高；而纯函数可以在 JVM 单测里逐字段断言。
 *
 * 两家协议的关键差异（也是这套翻译存在的全部理由）：
 *
 * | | OpenAI 兼容 | Anthropic Messages |
 * |---|---|---|
 * | 工具声明字段 | `tools:[{type:"function", function:{name, description, parameters}}]` | `tools:[{name, description, input_schema}]` |
 * | 助手请求调用 | 消息上的 `tool_calls[]` | content 数组里的 `tool_use` 块 |
 * | 回填工具结果 | 独立的 `role:"tool"` 消息 + `tool_call_id` | **user** 消息里的 `tool_result` 块 |
 * | system 提示 | 普通 system 消息 | 顶层 `system` 参数 |
 */
internal object AgentRequestBody {

    fun build(
        gson: Gson,
        protocol: String,
        model: String,
        entries: List<AgentEntry>,
        tools: List<HealthTool>,
        temperature: Double?,
        maxTokens: Int
    ): String = if (protocol == LlmProviders.PROTOCOL_ANTHROPIC) {
        gson.toJson(anthropicBody(gson, model, entries, tools, temperature, maxTokens))
    } else {
        gson.toJson(openAiBody(gson, model, entries, tools, temperature, maxTokens))
    }

    // ---------- OpenAI 兼容 ----------

    private fun openAiBody(
        gson: Gson,
        model: String,
        entries: List<AgentEntry>,
        tools: List<HealthTool>,
        temperature: Double?,
        maxTokens: Int
    ): Map<String, Any?> = buildMap {
        put("model", model)
        put("stream", true)
        // temperature 为 null 时不带该参数：Claude 扩展思考要求它必须为 1，被拒后要能降级重发
        if (temperature != null) put("temperature", temperature)
        put("max_tokens", maxTokens)
        put("messages", entries.flatMap { openAiMessages(gson, it) })
        // 最后一轮不传工具时，tools 字段整体省略（传空数组在部分供应商上会被判为非法）
        if (tools.isNotEmpty()) {
            put("tools", tools.map { openAiTool(gson, it) })
        }
    }

    private fun openAiTool(gson: Gson, tool: HealthTool): Map<String, Any?> = mapOf(
        "type" to "function",
        "function" to mapOf(
            "name" to tool.name,
            "description" to tool.description,
            // parameters 必须是 JSON 对象本身，不能是字符串 —— 传字符串会被直接 400
            "parameters" to schemaObject(gson, tool.parametersJsonSchema)
        )
    )

    private fun openAiMessages(gson: Gson, entry: AgentEntry): List<Map<String, Any?>> = when (entry) {
        is AgentEntry.System -> listOf(mapOf("role" to "system", "content" to entry.text))

        is AgentEntry.User -> listOf(
            mapOf(
                "role" to "user",
                "content" to if (entry.imageBase64.isNullOrBlank()) {
                    entry.text
                } else {
                    listOf(
                        mapOf("type" to "text", "text" to entry.text),
                        mapOf(
                            "type" to "image_url",
                            "image_url" to mapOf("url" to "data:image/jpeg;base64,${entry.imageBase64}")
                        )
                    )
                }
            )
        )

        is AgentEntry.AssistantToolCalls -> listOf(
            buildMap {
                put("role", "assistant")
                put("content", entry.text.orEmpty())
                put(
                    "tool_calls",
                    entry.calls.map { call ->
                        mapOf(
                            "id" to call.id,
                            "type" to "function",
                            "function" to mapOf("name" to call.name, "arguments" to call.argsJson)
                        )
                    }
                )
            }
        )

        // OpenAI 要求每个 tool_call 都有一条独立的 tool 消息与之配对，缺一条就 400
        is AgentEntry.ToolResults -> entry.results.map { result ->
            mapOf(
                "role" to "tool",
                "tool_call_id" to result.toolCallId,
                "content" to result.text
            )
        }
    }

    // ---------- Anthropic Messages ----------

    private fun anthropicBody(
        gson: Gson,
        model: String,
        entries: List<AgentEntry>,
        tools: List<HealthTool>,
        temperature: Double?,
        maxTokens: Int
    ): Map<String, Any?> = buildMap {
        put("model", model)
        put("stream", true)
        if (temperature != null) put("temperature", temperature)
        put("max_tokens", maxTokens)
        put("messages", entries.mapNotNull { anthropicMessage(gson, it) })
        // system 在 Anthropic 是顶层参数，不能混进 messages
        val system = entries.filterIsInstance<AgentEntry.System>()
            .joinToString("\n") { it.text }
        if (system.isNotBlank()) put("system", system)
        if (tools.isNotEmpty()) {
            put(
                "tools",
                tools.map { tool ->
                    mapOf(
                        "name" to tool.name,
                        "description" to tool.description,
                        "input_schema" to schemaObject(gson, tool.parametersJsonSchema)
                    )
                }
            )
        }
    }

    private fun anthropicMessage(gson: Gson, entry: AgentEntry): Map<String, Any?>? = when (entry) {
        // Anthropic 的 system 走顶层参数，这里丢弃
        is AgentEntry.System -> null

        is AgentEntry.User -> mapOf(
            "role" to "user",
            "content" to buildList {
                add(mapOf("type" to "text", "text" to entry.text))
                if (!entry.imageBase64.isNullOrBlank()) {
                    add(
                        mapOf(
                            "type" to "image",
                            "source" to mapOf(
                                "type" to "base64",
                                "media_type" to "image/jpeg",
                                "data" to entry.imageBase64
                            )
                        )
                    )
                }
            }
        )

        is AgentEntry.AssistantToolCalls -> mapOf(
            "role" to "assistant",
            "content" to entry.calls.map { call ->
                mapOf(
                    "type" to "tool_use",
                    "id" to call.id,
                    "name" to call.name,
                    "input" to schemaObject(gson, call.argsJson)
                )
            }
        )

        // Anthropic 的工具结果要作为 **user** 消息里的 tool_result 块回填
        is AgentEntry.ToolResults -> mapOf(
            "role" to "user",
            "content" to entry.results.map { result ->
                mapOf(
                    "type" to "tool_result",
                    "tool_use_id" to result.toolCallId,
                    "content" to result.text,
                    "is_error" to !result.ok
                )
            }
        )
    }

    /** 把 JSON 字符串解析成对象；解析不出来时退化成空对象而不是抛异常 */
    private fun schemaObject(gson: Gson, json: String): JsonObject =
        runCatching { gson.fromJson(json, JsonObject::class.java) }
            .getOrNull()
            ?.takeIf { it.isJsonObject }
            ?: JsonObject()
}

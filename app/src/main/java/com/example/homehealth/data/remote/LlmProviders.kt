package com.example.homehealth.data.remote

import com.example.homehealth.data.remote.LlmProviders.PROTOCOL_OPENAI

/**
 * LLM 供应商预设（模型清单更新于 2026-08-28，来源：各供应商官网 / 开放平台文档）。
 *
 * 两类模型严格区分、独立配置：
 * - visionModels 视觉模型：支持图片输入，用于体检报告 / 化验单图片解析；
 * - chatModels   文本模型：用于健康问答等纯文本任务。
 * LlmClient.resolveConfig 会校验：报告解析必须使用视觉模型，防止误用纯文本模型。
 *
 * 协议：OpenAI 兼容 chat/completions（绝大多数供应商），或 Anthropic Messages API。
 */
data class LlmProvider(
    val id: String,                    // 标识
    val name: String,                  // 显示名
    val baseUrl: String,               // API base
    val visionModels: List<String>,    // 视觉模型（报告解析，支持图片输入）
    val chatModels: List<String>,      // 文本模型（健康问答）
    val keyHint: String,               // Key 获取提示
    val note: String? = null,          // 备注
    val protocol: String = PROTOCOL_OPENAI  // 请求协议
)

object LlmProviders {

    const val LOCAL = "local"        // 本地模式

    /** OpenAI 兼容 chat/completions 协议 */
    const val PROTOCOL_OPENAI = "openai"

    /** Anthropic Messages API（/v1/messages）协议 */
    const val PROTOCOL_ANTHROPIC = "anthropic"

    /** 是否为「供应商直连」模式（预设 LLM 供应商），走 LlmClient */
    fun isDirect(id: String): Boolean = id != LOCAL

    // ---------- 智谱 GLM（open.bigmodel.cn）----------

    val ZHIPU = LlmProvider(
        id = "zhipu",
        name = "智谱 GLM",
        baseUrl = "https://open.bigmodel.cn/api/paas/v4/",
        visionModels = listOf(
            "glm-5.3-flash",     // 原生多模态，轻量高速
            "glm-4.6v",          // 旗舰视觉推理（106B MoE）
            "glm-4.6v-flash",    // 轻量视觉（9B）
            "glm-5v-turbo",      // 多模态 Coding 基座
            "glm-4v-plus",
            "glm-4v-flash"
        ),
        chatModels = listOf(
            "glm-5.3-flash",
            "glm-5.3",
            "glm-5.2",
            "glm-5.1",
            "glm-5",
            "glm-5-turbo",
            "glm-4.7",
            "glm-4.6",
            "glm-4.5-air",
            "glm-4.5-airx",
            "glm-4.5-flash"
        ),
        keyHint = "在 open.bigmodel.cn 的「API Keys」页面创建",
        note = "请区分视觉模型和大语言模型"
    )

    // ---------- OpenAI（platform.openai.com）----------
    // 官方说明：现行 GPT 系列全部型号原生支持文本 + 图片输入，视觉 / 文本列表一致

    private val OPENAI_MODELS = listOf(
        "gpt-5.6-astra",
        "gpt-5.6-sol",        // 旗舰推理
        "gpt-5.6-terra",      // 均衡
        "gpt-5.6-luna",       // 低成本高吞吐
        "gpt-5.5",
        "gpt-5.5-pro",
        "gpt-5.4",
        "gpt-5.4-mini",
        "gpt-5.3-codex",
        "gpt-5.3-chat-latest",
        "gpt-5.2",
        "gpt-5-mini",
        "gpt-5-nano"
    )

    val OPENAI = LlmProvider(
        id = "openai",
        name = "OpenAI",
        baseUrl = "https://api.openai.com/v1/",
        visionModels = OPENAI_MODELS,
        chatModels = OPENAI_MODELS,
        keyHint = "在 platform.openai.com 的「API Keys」页面创建",
        note = "需可访问 OpenAI 的网络环境；GPT-5 起全部型号支持图文输入"
    )

    // ---------- Google Gemini（aistudio.google.com，OpenAI 兼容端点）----------
    // 全系多模态，视觉 / 文本列表一致

    private val GEMINI_MODELS = listOf(
        "gemini-3.8-flash",
        "gemini-3.7-flash",
        "gemini-3.6-flash",
        "gemini-3.5-flash",
        "gemini-3.5-flash-lite",
        "gemini-3.1-flash-lite",
        "gemini-3.1-pro-preview",
        "gemini-3-pro-preview",
        "gemini-2.5-pro",
        "gemini-2.5-flash",
        "gemini-2.5-flash-lite",
        "gemini-2.0-flash"
    )

    val GEMINI = LlmProvider(
        id = "gemini",
        name = "Google Gemini",
        baseUrl = "https://generativelanguage.googleapis.com/v1beta/openai/",
        visionModels = GEMINI_MODELS,
        chatModels = GEMINI_MODELS,
        keyHint = "在 aistudio.google.com 的「Get API Key」创建",
        note = "需可访问 Google 的网络环境；全系多模态"
    )

    // ---------- DeepSeek（platform.deepseek.com）----------

    val DEEPSEEK = LlmProvider(
        id = "deepseek",
        name = "DeepSeek",
        baseUrl = "https://api.deepseek.com/",
        visionModels = listOf(
            "deepseek-flash"  // 实验性视觉模型（2026-08-21 上线）
        ),
        chatModels = listOf(
            "deepseek-flash",
            "deepseek-v4-pro",
            "deepseek-v4-flash"
        ),
        keyHint = "在 platform.deepseek.com 的「API Keys」页面创建",
        note = "deepseek-flash支持多模态"
    )

    // ---------- Kimi 月之暗面（platform.moonshot.cn）----------
    // K3 / K2.6 / K2.7-Code 均原生支持视觉输入

    private val KIMI_MODELS = listOf(
        "kimi-k3",                  // 旗舰：2.8T 参数、1M 上下文、原生视觉
        "kimi-k2.6",
        "kimi-k2.7-code",
        "kimi-k2.7-code-highspeed"
    )

    val KIMI = LlmProvider(
        id = "kimi",
        name = "Kimi（月之暗面）",
        baseUrl = "https://api.moonshot.cn/v1/",
        visionModels = KIMI_MODELS,
        chatModels = KIMI_MODELS,
        keyHint = "在 platform.moonshot.cn 的「API Key 管理」创建",
        note = "kimi-k3 原生视觉；moonshot-v1 与 kimi-k2.5 已临近下线，未收录"
    )

    // ---------- 通义千问 阿里云百炼（bailian.console.aliyun.com，OpenAI 兼容模式）----------

    val QWEN = LlmProvider(
        id = "qwen",
        name = "通义千问（阿里云百炼）",
        baseUrl = "https://dashscope.aliyuncs.com/compatible-mode/v1/",
        visionModels = listOf(
            "qwen3.8-max",
            "qwen3.8-flash",
            "qwen3.7-plus",
            "qwen3.7-flash",
            "qwen3.6-plus",
            "qwen3.6-flash",
            "qwen3-vl-plus",
            "qwen3-vl-flash",
            "qwen-vl-max",
            "qwen-vl-ocr"
        ),
        chatModels = listOf(
            "qwen3.8-max",
            "qwen3.8-flash",
            "qwen3.8-27b",
            "qwen3.7-plus",
            "qwen3.7-flash",
            "qwen3.6-plus",
            "qwen3.6-flash",
            "qwen3-max",
            "qwen3-coder-plus",
            "qwq-plus",
            "qwen-turbo",
            "qwen-long"
        ),
        keyHint = "在 bailian.console.aliyun.com 的「API-KEY 管理」创建",
        note = "Qwen3.6 起旗舰系列原生支持图文输入；qwen-vl-ocr 适合纯文字提取"
    )

    // ---------- Anthropic Claude（console.anthropic.com，Messages API）----------
    // 全部现行型号支持文本 + 图片输入

    private val ANTHROPIC_MODELS = listOf(
        "claude-opus-5",
        "claude-fable-5",
        "claude-sonnet-5",
        "claude-haiku-4-5",
        "claude-opus-4-6",
        "claude-sonnet-4-6"
    )

    val ANTHROPIC = LlmProvider(
        id = "anthropic",
        name = "Anthropic Claude",
        baseUrl = "https://api.anthropic.com/",
        visionModels = ANTHROPIC_MODELS,
        chatModels = ANTHROPIC_MODELS,
        keyHint = "在 console.anthropic.com 的「API Keys」页面创建",
        note = "使用 Anthropic Messages API（非 OpenAI 协议）；需海外网络环境",
        protocol = PROTOCOL_ANTHROPIC
    )

    /** 全部供应商（本地为伪供应商，只用于 UI 展示与分流） */
    val ALL: List<LlmProvider> = listOf(
        ZHIPU, OPENAI, GEMINI, DEEPSEEK, KIMI, QWEN, ANTHROPIC
    )

    fun byId(id: String): LlmProvider? = ALL.firstOrNull { it.id == id }

    /** 供应商显示名 */
    fun nameOf(id: String): String = when (id) {
        LOCAL -> "本地模式"
        else -> byId(id)?.name ?: id
    }
}

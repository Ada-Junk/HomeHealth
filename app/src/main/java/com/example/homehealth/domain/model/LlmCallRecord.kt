package com.example.homehealth.domain.model

/**
 * 一次 LLM 调用的观测记录。
 *
 * 刻意**不含提示词与回复内容**：那里面是用户的体检指标，把日志做成可读内容的代价是
 * 敏感数据在设备上多存一份，收益却只是"调试时少复现一次"。
 */
data class LlmCallRecord(
    val provider: String,
    val model: String,
    val scene: String,
    val latencyMs: Long,
    val promptChars: Int,
    val completionChars: Int,
    val hasImage: Boolean,
    val attempts: Int,
    val ok: Boolean,
    val errorType: String?,
    val createdAt: Long
)

/** 调用场景 */
object LlmScene {
    const val PARSE = "parse"
    const val QA = "qa"
}

/** 调用统计聚合 */
data class LlmCallStats(
    val total: Int,
    val failures: Int,
    val avgLatencyMs: Long,
    val totalPromptChars: Long,
    val totalCompletionChars: Long,
    val byProvider: List<LlmCallProviderStat>
)

/** 单个供应商的统计 */
data class LlmCallProviderStat(
    val provider: String,
    val total: Int,
    val failures: Int
)

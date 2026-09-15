package com.example.homehealth.domain.repository

import com.example.homehealth.domain.model.LlmCallRecord
import com.example.homehealth.domain.model.LlmCallStats

/**
 * LLM 调用日志仓库（可观测性）。
 *
 * 用途：AI 功能出问题时能回答三个问题 —— 失败了没有、慢在哪、失败是否集中在某一家供应商。
 * 之前这些信息只在 logcat 里，重启即失。
 */
interface LlmCallLogRepository {
    suspend fun record(record: LlmCallRecord)
    suspend fun recent(limit: Int): List<LlmCallRecord>
    suspend fun stats(sinceMs: Long): LlmCallStats
    suspend fun clear()
}

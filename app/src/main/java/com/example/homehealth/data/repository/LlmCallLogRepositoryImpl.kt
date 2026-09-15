package com.example.homehealth.data.repository

import com.example.homehealth.data.local.dao.LlmCallLogDao
import com.example.homehealth.data.local.entity.LlmCallLog
import com.example.homehealth.domain.model.LlmCallProviderStat
import com.example.homehealth.domain.model.LlmCallRecord
import com.example.homehealth.domain.model.LlmCallStats
import com.example.homehealth.domain.repository.LlmCallLogRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LlmCallLogRepositoryImpl @Inject constructor(
    private val dao: LlmCallLogDao
) : LlmCallLogRepository {

    override suspend fun record(record: LlmCallRecord) {
        dao.insert(record.toEntity())
        // 保留窗口：日志只用于排障，无需长期留存，也避免无限增长。
        // 表很小（一次调用一行），每次插入顺带清理的代价可以忽略。
        dao.deleteOlderThan(System.currentTimeMillis() - RETENTION_MS)
    }

    override suspend fun recent(limit: Int): List<LlmCallRecord> =
        dao.recent(limit).map { it.toDomain() }

    override suspend fun stats(sinceMs: Long): LlmCallStats {
        val since = System.currentTimeMillis() - sinceMs
        val agg = dao.aggregateSince(since)
        return LlmCallStats(
            total = agg.total,
            failures = agg.failures,
            avgLatencyMs = agg.avgLatencyMs?.toLong() ?: 0L,
            totalPromptChars = agg.totalPromptChars ?: 0L,
            totalCompletionChars = agg.totalCompletionChars ?: 0L,
            byProvider = dao.aggregateByProviderSince(since).map {
                LlmCallProviderStat(it.provider, it.total, it.failures)
            }
        )
    }

    override suspend fun clear() = dao.clear()

    private fun LlmCallRecord.toEntity() = LlmCallLog(
        provider = provider,
        model = model,
        scene = scene,
        latencyMs = latencyMs,
        promptChars = promptChars,
        completionChars = completionChars,
        hasImage = hasImage,
        attempts = attempts,
        ok = ok,
        errorType = errorType,
        createdAt = createdAt
    )

    private fun LlmCallLog.toDomain() = LlmCallRecord(
        provider = provider,
        model = model,
        scene = scene,
        latencyMs = latencyMs,
        promptChars = promptChars,
        completionChars = completionChars,
        hasImage = hasImage,
        attempts = attempts,
        ok = ok,
        errorType = errorType,
        createdAt = createdAt
    )

    private companion object {
        /** 日志保留时长：30 天足够定位"最近是不是变慢了" */
        const val RETENTION_MS = 30L * 24 * 60 * 60 * 1000
    }
}

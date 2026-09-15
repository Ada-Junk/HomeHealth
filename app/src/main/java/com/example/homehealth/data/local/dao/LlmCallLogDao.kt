package com.example.homehealth.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.example.homehealth.data.local.entity.LlmCallLog

/** 聚合统计（用于设置页的调用统计卡片） */
data class LlmCallAggregate(
    val total: Int,
    val failures: Int,
    val avgLatencyMs: Double?,
    val totalPromptChars: Long?,
    val totalCompletionChars: Long?
)

@Dao
interface LlmCallLogDao {

    @Insert
    suspend fun insert(log: LlmCallLog)

    @Query("SELECT * FROM llm_call_logs ORDER BY createdAt DESC LIMIT :limit")
    suspend fun recent(limit: Int): List<LlmCallLog>

    @Query(
        "SELECT COUNT(*) AS total, " +
            "COALESCE(SUM(CASE WHEN ok = 0 THEN 1 ELSE 0 END), 0) AS failures, " +
            "AVG(latencyMs) AS avgLatencyMs, " +
            "SUM(promptChars) AS totalPromptChars, " +
            "SUM(completionChars) AS totalCompletionChars " +
            "FROM llm_call_logs WHERE createdAt >= :since"
    )
    suspend fun aggregateSince(since: Long): LlmCallAggregate

    /** 按供应商聚合失败数，用于定位"是某一家的问题还是普遍问题" */
    @Query(
        "SELECT provider AS provider, COUNT(*) AS total, " +
            "COALESCE(SUM(CASE WHEN ok = 0 THEN 1 ELSE 0 END), 0) AS failures " +
            "FROM llm_call_logs WHERE createdAt >= :since GROUP BY provider ORDER BY total DESC"
    )
    suspend fun aggregateByProviderSince(since: Long): List<LlmCallProviderStats>

    @Query("DELETE FROM llm_call_logs WHERE createdAt < :before")
    suspend fun deleteOlderThan(before: Long)

    @Query("DELETE FROM llm_call_logs")
    suspend fun clear()
}

/** 按供应商的统计行 */
data class LlmCallProviderStats(
    val provider: String,
    val total: Int,
    val failures: Int
)

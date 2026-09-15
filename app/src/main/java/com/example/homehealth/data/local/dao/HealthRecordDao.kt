package com.example.homehealth.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.example.homehealth.data.local.entity.HealthRecord
import kotlinx.coroutines.flow.Flow

@Dao
interface HealthRecordDao {

    @Query(
        "SELECT * FROM health_records WHERE memberId = :memberId AND type = :type " +
            "ORDER BY recordDate DESC LIMIT :limit"
    )
    suspend fun getRecentRecords(memberId: String, type: String, limit: Int): List<HealthRecord>

    /**
     * 每个指标各取最近 perTypeLimit 条（供问答摘要构建使用）。
     *
     * 用相关子查询实现「每组 Top-N」，而不是窗口函数：
     * minSdk 26 的设备上 Android 平台自带 SQLite 为 3.19，窗口函数需 SQLite 3.25+
     * （平台 SQLite 到 API 30 才是 3.28），`ROW_NUMBER() OVER (...)` 会在 API 26-29 上直接语法报错。
     *
     * 结果集上限为「指标数 × perTypeLimit」，避免把成员的全部历史记录读进内存。
     */
    @Query(
        "SELECT * FROM health_records h WHERE h.memberId = :memberId AND h.id IN (" +
            "SELECT h2.id FROM health_records h2 " +
            "WHERE h2.memberId = h.memberId AND h2.type = h.type " +
            "ORDER BY h2.recordDate DESC LIMIT :perTypeLimit) " +
            "ORDER BY h.recordDate DESC"
    )
    suspend fun getRecentPerTypeByMember(memberId: String, perTypeLimit: Int): List<HealthRecord>

    @Query(
        "SELECT * FROM health_records WHERE memberId = :memberId AND type = :type " +
            "ORDER BY recordDate DESC LIMIT 1"
    )
    suspend fun getLatest(memberId: String, type: String): HealthRecord?

    @Query(
        "SELECT * FROM health_records WHERE memberId = :memberId " +
            "ORDER BY recordDate DESC LIMIT :limit"
    )
    suspend fun getRecentByMember(memberId: String, limit: Int): List<HealthRecord>

    @Query(
        "SELECT * FROM health_records WHERE memberId = :memberId AND type = :type " +
            "ORDER BY recordDate DESC"
    )
    fun observeRecordsByType(memberId: String, type: String): Flow<List<HealthRecord>>

    @Query("SELECT * FROM health_records WHERE memberId = :memberId ORDER BY recordDate DESC")
    fun observeAllByMember(memberId: String): Flow<List<HealthRecord>>

    @Query("SELECT * FROM health_records ORDER BY recordDate DESC")
    fun observeAll(): Flow<List<HealthRecord>>

    @Query("SELECT * FROM health_records ORDER BY recordDate DESC")
    suspend fun getAll(): List<HealthRecord>

    @Insert
    suspend fun insert(record: HealthRecord)

    @Insert
    suspend fun insertAll(records: List<HealthRecord>)

    @Update
    suspend fun update(record: HealthRecord)

    @Delete
    suspend fun delete(record: HealthRecord)

    @Query("DELETE FROM health_records WHERE memberId = :memberId")
    suspend fun deleteByMember(memberId: String)
}

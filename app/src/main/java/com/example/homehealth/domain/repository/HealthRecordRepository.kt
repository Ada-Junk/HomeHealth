package com.example.homehealth.domain.repository

import com.example.homehealth.data.local.entity.HealthRecord
import kotlinx.coroutines.flow.Flow

/** 健康记录仓库 */
interface HealthRecordRepository {
    fun observeRecordsByType(memberId: String, type: String): Flow<List<HealthRecord>>
    fun observeAllByMember(memberId: String): Flow<List<HealthRecord>>
    fun observeAllRecords(): Flow<List<HealthRecord>>
    suspend fun getRecentRecords(memberId: String, type: String, limit: Int): List<HealthRecord>
    suspend fun getRecentByMember(memberId: String, limit: Int): List<HealthRecord>

    /**
     * 成员的全部记录（时间倒序）。
     * 检索必须看到全量历史才能回答「一直以来怎么样」，也才能在旧指标被问到时召回；
     * 与 [getRecentRecords] 的「每组 Top-N」是两条并存的路径。
     */
    suspend fun getAllByMember(memberId: String): List<HealthRecord>

    suspend fun getLatest(memberId: String, type: String): HealthRecord?
    suspend fun addRecord(record: HealthRecord)
    suspend fun addRecords(records: List<HealthRecord>)
    suspend fun updateRecord(record: HealthRecord)
    suspend fun deleteRecord(record: HealthRecord)
    suspend fun getAllRecords(): List<HealthRecord>
}

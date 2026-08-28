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
    suspend fun getLatest(memberId: String, type: String): HealthRecord?
    suspend fun addRecord(record: HealthRecord)
    suspend fun addRecords(records: List<HealthRecord>)
    suspend fun deleteRecord(record: HealthRecord)
    suspend fun getAllRecords(): List<HealthRecord>
}

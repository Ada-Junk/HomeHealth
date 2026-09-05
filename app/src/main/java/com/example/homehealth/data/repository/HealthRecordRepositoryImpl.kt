package com.example.homehealth.data.repository

import com.example.homehealth.data.local.dao.HealthRecordDao
import com.example.homehealth.data.local.entity.HealthRecord
import com.example.homehealth.domain.repository.HealthRecordRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HealthRecordRepositoryImpl @Inject constructor(
    private val healthRecordDao: HealthRecordDao
) : HealthRecordRepository {

    override fun observeRecordsByType(memberId: String, type: String): Flow<List<HealthRecord>> =
        healthRecordDao.observeRecordsByType(memberId, type)

    override fun observeAllByMember(memberId: String): Flow<List<HealthRecord>> =
        healthRecordDao.observeAllByMember(memberId)

    override fun observeAllRecords(): Flow<List<HealthRecord>> = healthRecordDao.observeAll()

    override suspend fun getRecentRecords(memberId: String, type: String, limit: Int): List<HealthRecord> =
        healthRecordDao.getRecentRecords(memberId, type, limit)

    override suspend fun getRecentByMember(memberId: String, limit: Int): List<HealthRecord> =
        healthRecordDao.getRecentByMember(memberId, limit)

    override suspend fun getLatest(memberId: String, type: String): HealthRecord? =
        healthRecordDao.getLatest(memberId, type)

    override suspend fun addRecord(record: HealthRecord) = healthRecordDao.insert(record)

    override suspend fun addRecords(records: List<HealthRecord>) = healthRecordDao.insertAll(records)

    override suspend fun updateRecord(record: HealthRecord) = healthRecordDao.update(record)

    override suspend fun deleteRecord(record: HealthRecord) = healthRecordDao.delete(record)

    override suspend fun getAllRecords(): List<HealthRecord> = healthRecordDao.getAll()
}

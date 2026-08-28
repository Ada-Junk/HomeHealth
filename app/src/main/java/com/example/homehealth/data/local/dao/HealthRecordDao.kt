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

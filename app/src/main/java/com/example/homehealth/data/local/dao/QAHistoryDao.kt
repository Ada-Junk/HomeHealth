package com.example.homehealth.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.example.homehealth.data.local.entity.QAHistory
import kotlinx.coroutines.flow.Flow

@Dao
interface QAHistoryDao {

    @Query("SELECT * FROM qa_history WHERE memberId = :memberId ORDER BY timestamp")
    fun observeByMember(memberId: String): Flow<List<QAHistory>>

    @Query("SELECT * FROM qa_history ORDER BY timestamp DESC")
    suspend fun getAll(): List<QAHistory>

    @Insert
    suspend fun insert(history: QAHistory)

    @Query("DELETE FROM qa_history WHERE memberId = :memberId")
    suspend fun clearByMember(memberId: String)

    @Query("DELETE FROM qa_history WHERE memberId = :memberId")
    suspend fun deleteByMember(memberId: String)
}

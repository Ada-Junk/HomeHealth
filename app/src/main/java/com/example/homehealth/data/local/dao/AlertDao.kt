package com.example.homehealth.data.local.dao

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.Query
import com.example.homehealth.data.local.entity.Alert
import kotlinx.coroutines.flow.Flow

/** 预警 + 成员姓名联表查询结果 */
data class AlertWithMemberName(
    @Embedded val alert: Alert,
    val memberName: String
)

@Dao
interface AlertDao {

    @Insert
    suspend fun insert(alert: Alert)

    @Query(
        "SELECT a.*, m.name AS memberName FROM alerts a " +
            "INNER JOIN family_members m ON a.memberId = m.id " +
            "ORDER BY CASE a.severity WHEN 'HIGH' THEN 0 WHEN 'MEDIUM' THEN 1 ELSE 2 END, " +
            "a.createdDate DESC"
    )
    fun observeAll(): Flow<List<AlertWithMemberName>>

    @Query(
        "SELECT a.*, m.name AS memberName FROM alerts a " +
            "INNER JOIN family_members m ON a.memberId = m.id " +
            "WHERE a.isRead = 0 " +
            "ORDER BY CASE a.severity WHEN 'HIGH' THEN 0 WHEN 'MEDIUM' THEN 1 ELSE 2 END, " +
            "a.createdDate DESC"
    )
    fun observeUnread(): Flow<List<AlertWithMemberName>>

    @Query("SELECT COUNT(*) FROM alerts WHERE memberId = :memberId AND isRead = 0")
    fun observeUnreadCount(memberId: String): Flow<Int>

    @Query("SELECT * FROM alerts WHERE memberId = :memberId AND isRead = 0")
    suspend fun getUnreadByMember(memberId: String): List<Alert>

    @Query("SELECT * FROM alerts ORDER BY createdDate DESC")
    suspend fun getAll(): List<Alert>

    @Query("UPDATE alerts SET isRead = 1 WHERE id = :id")
    suspend fun markRead(id: String)

    @Query("UPDATE alerts SET isRead = 1")
    suspend fun markAllRead()

    @Query("DELETE FROM alerts WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM alerts WHERE memberId = :memberId")
    suspend fun deleteByMember(memberId: String)
}

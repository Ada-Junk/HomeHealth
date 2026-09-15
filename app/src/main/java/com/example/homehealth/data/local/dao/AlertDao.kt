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

    /**
     * 取某成员在指定时间之后生成的预警（用于预警去重的时间窗口判断）。
     * 只带上时间下界、不取全量历史：避免"报过一次就永远不再报"，
     * 也避免预警表随时间无限膨胀后被整表读入内存。
     */
    @Query("SELECT * FROM alerts WHERE memberId = :memberId AND createdDate >= :since")
    suspend fun getByMemberSince(memberId: String, since: Long): List<Alert>

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

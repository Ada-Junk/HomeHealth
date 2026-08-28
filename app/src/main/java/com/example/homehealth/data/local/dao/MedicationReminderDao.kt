package com.example.homehealth.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.example.homehealth.data.local.entity.MedicationReminder
import kotlinx.coroutines.flow.Flow

/** 用药提醒 + 成员姓名联表查询结果 */
data class ReminderWithMemberName(
    @Embedded val reminder: MedicationReminder,
    val memberName: String
)

@Dao
interface MedicationReminderDao {

    @Query(
        "SELECT r.*, m.name AS memberName FROM medication_reminders r " +
            "INNER JOIN family_members m ON r.memberId = m.id " +
            "ORDER BY r.active DESC, r.medicationName"
    )
    fun observeAll(): Flow<List<ReminderWithMemberName>>

    @Query("SELECT * FROM medication_reminders WHERE active = 1")
    suspend fun getActive(): List<MedicationReminder>

    @Query("SELECT * FROM medication_reminders ORDER BY medicationName")
    suspend fun getAll(): List<MedicationReminder>

    @Insert
    suspend fun insert(reminder: MedicationReminder)

    @Update
    suspend fun update(reminder: MedicationReminder)

    @Delete
    suspend fun delete(reminder: MedicationReminder)

    @Query("DELETE FROM medication_reminders WHERE memberId = :memberId")
    suspend fun deleteByMember(memberId: String)
}

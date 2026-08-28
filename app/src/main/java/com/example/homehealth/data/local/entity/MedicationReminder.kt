package com.example.homehealth.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/** 用药提醒 */
@Entity(tableName = "medication_reminders")
data class MedicationReminder(
    @PrimaryKey val id: String,
    val memberId: String,
    val medicationName: String,
    val dosage: String,
    val schedule: String, // 如 "daily:08:00,20:00"
    val startDate: Long,
    val endDate: Long? = null,
    val active: Boolean = true
) {
    /** 解析出每日时间点列表，如 ["08:00", "20:00"] */
    fun dailyTimes(): List<String> =
        schedule.removePrefix("daily:")
            .split(",")
            .map { it.trim() }
            .filter { it.matches(Regex("\\d{1,2}:\\d{2}")) }

    companion object {
        fun buildSchedule(times: List<String>): String = "daily:" + times.joinToString(",")
    }
}

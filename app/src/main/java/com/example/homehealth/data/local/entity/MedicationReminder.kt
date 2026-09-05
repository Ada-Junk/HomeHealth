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
    val active: Boolean = true,
    /** 已写入系统日历的事件 ID（逗号分隔），用于删除提醒时同步清理日历 */
    val calendarEventIds: String? = null
) {
    /** 解析出每日时间点列表，如 ["08:00", "20:00"] */
    fun dailyTimes(): List<String> =
        schedule.removePrefix("daily:")
            .split(",")
            .map { it.trim() }
            .filter { it.matches(Regex("\\d{1,2}:\\d{2}")) }

    /** 已写入日历的事件 ID 列表 */
    fun calendarEventIdList(): List<Long> =
        calendarEventIds?.split(",")
            ?.mapNotNull { it.trim().toLongOrNull() }
            ?: emptyList()

    companion object {
        fun buildSchedule(times: List<String>): String = "daily:" + times.joinToString(",")

        fun buildCalendarEventIds(ids: List<Long>): String? =
            ids.takeIf { it.isNotEmpty() }?.joinToString(",")
    }
}

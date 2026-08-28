package com.example.homehealth.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/** 健康预警 */
@Entity(tableName = "alerts")
data class Alert(
    @PrimaryKey val id: String,
    val memberId: String,
    val type: String, // 如 "trend_anomaly", "out_of_range", "medication_reminder"
    val title: String,
    val description: String,
    val severity: AlertSeverity, // LOW, MEDIUM, HIGH
    val createdDate: Long,
    val isRead: Boolean = false
)

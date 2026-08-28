package com.example.homehealth.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** 健康指标记录 */
@Entity(
    tableName = "health_records",
    foreignKeys = [
        ForeignKey(
            entity = FamilyMember::class,
            parentColumns = ["id"],
            childColumns = ["memberId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("memberId"), Index("type")]
)
data class HealthRecord(
    @PrimaryKey val id: String,
    val memberId: String,
    val type: String, // 如 "blood_pressure", "blood_glucose", "cholesterol", "weight"
    val value: String, // 存储原始值或结构化值，如 "120/80"
    val numericValue: Double?, // 用于趋势分析的主数值，如收缩压
    val unit: String, // 如 "mmHg", "mmol/L"
    val recordDate: Long, // timestamp
    val sourceDocumentId: String? = null, // 关联的文档 ID
    val notes: String? = null
)

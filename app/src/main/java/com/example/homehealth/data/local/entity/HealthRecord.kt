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
    indices = [
        Index("memberId"),
        Index("type"),
        // 复合索引：(memberId, type, recordDate) 覆盖「某成员某指标的最近 N 条」查询
        Index(value = ["memberId", "type", "recordDate"])
    ]
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
    val notes: String? = null,
    /**
     * 比较符：null = 精确值，"LT" = 小于（报告中写作 `<0.1`），"GT" = 大于（`>100`）。
     *
     * 体检报告里相当一部分结果是区间型或定性表述（"<0.1"、">100"、"阴性"）。
     * 只存 [numericValue] 会让前两者退化成"无值"而被趋势与预警直接跳过；
     * 记下比较符后，才能在**保守前提下**参与判断：只有当边界值本身已经越界时才报警
     * （如 `<0.1` 而下限是 3.0），避免把"不确定是否越界"误报成确定结论。
     *
     * 纯定性结果（"阴性"、滴度 "1:80"）仍无数值语义，此字段为 null，由 [value] 承载文本。
     */
    val comparator: String? = null
)

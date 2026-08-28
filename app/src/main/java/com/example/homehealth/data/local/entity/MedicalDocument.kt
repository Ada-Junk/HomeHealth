package com.example.homehealth.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/** 医疗文档（体检报告 / 化验单 / 处方等） */
@Entity(tableName = "medical_documents")
data class MedicalDocument(
    @PrimaryKey val id: String,
    val memberId: String,
    val fileName: String,
    val filePath: String, // 本地文件路径
    val uploadDate: Long,
    val documentType: String? = null, // 如 "lab_report", "prescription", "discharge_summary"
    val parseStatus: ParseStatus = ParseStatus.PENDING, // PENDING, PROCESSING, COMPLETED, FAILED
    val extractedJson: String? = null, // 解析出的结构化数据 JSON
    val errorMessage: String? = null
)

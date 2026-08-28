package com.example.homehealth.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/** 问答历史 */
@Entity(tableName = "qa_history")
data class QAHistory(
    @PrimaryKey val id: String,
    val memberId: String,
    val question: String,
    val answer: String,
    val timestamp: Long,
    val sources: String? = null, // 引用知识库来源（换行分隔）
    val thinking: String? = null // 模型思考过程（深度思考模型返回，可展示）
)

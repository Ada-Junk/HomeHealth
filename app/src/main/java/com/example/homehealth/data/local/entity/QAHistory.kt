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
    val thinking: String? = null, // 模型思考过程（深度思考模型返回，可展示）
    /**
     * 本轮提问附带的报告图片路径（可为空）。
     *
     * 为什么随提问存图：影像、病理、出院小结这类**叙述性报告**没有对应的结构化指标，
     * 解析流程接不住，只能以图片形式提问；不存路径的话历史里会留下「一条提问但看不到图」。
     * 图片落在 `filesDir/qa_images`（与报告原图 `documents/` 分开），删成员时一并清理。
     */
    val imagePath: String? = null
)

package com.example.homehealth.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * LLM 调用日志（可观测性）。
 *
 * 目的：回答"AI 功能出问题时到底发生了什么" —— 以前只能靠 logcat，重启就没了。
 * 记录粒度是**每次调用一行**，重试不改行、只在 [attempts] 上累计（否则一次请求失败会
 * 变成 3 行，统计口径就乱了）。
 *
 * 隐私：只记录**结构化指标**（供应商、模型、耗时、字符数、是否含图、是否成功、错误类型），
 * **不记录任何提示词或回复内容** —— 那里面是用户的体检数据。
 */
@Entity(tableName = "llm_call_logs", indices = [Index("createdAt")])
data class LlmCallLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** 供应商 id（zhipu / openai / …） */
    val provider: String,
    /** 实际使用的模型名 */
    val model: String,
    /** 场景：parse（报告解析）/ qa（健康问答） */
    val scene: String,
    /** 总耗时（含重试等待），毫秒 */
    val latencyMs: Long,
    /** 提示词字符数（**不含图片 base64**，否则会把统计淹没） */
    val promptChars: Int,
    /** 回复字符数；失败时为 0 */
    val completionChars: Int,
    /** 是否为视觉请求（含图片） */
    val hasImage: Boolean,
    /** 实际发起的请求次数（含首次）；1 表示一次成功 */
    val attempts: Int,
    val ok: Boolean,
    /** 失败类型：http_429 / http_5xx / timeout / io / truncated / <异常类名>；成功时为 null */
    val errorType: String? = null,
    val createdAt: Long
)

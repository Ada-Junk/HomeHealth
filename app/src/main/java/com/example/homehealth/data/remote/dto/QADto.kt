package com.example.homehealth.data.remote.dto

/** 健康问答请求 */
data class QARequest(
    val member_id: String,
    val question: String
)

/** 健康问答响应 */
data class QAResponse(
    val answer: String? = null,
    val sources: List<String>? = null,
    val references: List<String>? = null,
    val message: String? = null
)

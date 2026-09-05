package com.example.homehealth.data.remote.dto

/** 解析出的单条健康记录 */
data class ParsedRecord(
    val type: String,
    val value: String,
    val numeric_value: Double? = null,
    val unit: String = "",
    val date: String? = null
)

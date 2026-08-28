package com.example.homehealth.data.remote.dto

/** 文档解析请求 */
data class ParseDocumentRequest(
    val image_base64: String,
    val member_id: String,
    val document_type: String? = null
)

/** 解析出的单条健康记录 */
data class ParsedRecord(
    val type: String,
    val value: String,
    val numeric_value: Double? = null,
    val unit: String = "",
    val date: String? = null
)

data class ExtractedData(
    val records: List<ParsedRecord>? = null,
    val raw_text: String? = null
)

/** 文档解析响应 */
data class ParseDocumentResponse(
    val status: String? = null,
    val extracted_data: ExtractedData? = null,
    val message: String? = null
)

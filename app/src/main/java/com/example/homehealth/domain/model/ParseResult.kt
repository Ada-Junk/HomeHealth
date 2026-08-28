package com.example.homehealth.domain.model

import com.example.homehealth.data.remote.dto.ParsedRecord

/** 文档解析结果 */
data class ParseResult(
    val records: List<ParsedRecord>,
    val rawText: String
)

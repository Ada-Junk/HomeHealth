package com.example.homehealth.data.local.entity

/** 文档解析状态 */
enum class ParseStatus {
    PENDING, PROCESSING, COMPLETED, FAILED
}

/** 预警严重程度 */
enum class AlertSeverity {
    LOW, MEDIUM, HIGH
}

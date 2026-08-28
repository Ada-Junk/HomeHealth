package com.example.homehealth.data.local

import androidx.room.TypeConverter
import com.example.homehealth.data.local.entity.AlertSeverity
import com.example.homehealth.data.local.entity.ParseStatus

/** Room 枚举类型转换器 */
class Converters {

    @TypeConverter
    fun fromParseStatus(status: ParseStatus): String = status.name

    @TypeConverter
    fun toParseStatus(value: String): ParseStatus =
        runCatching { ParseStatus.valueOf(value) }.getOrDefault(ParseStatus.PENDING)

    @TypeConverter
    fun fromAlertSeverity(severity: AlertSeverity): String = severity.name

    @TypeConverter
    fun toAlertSeverity(value: String): AlertSeverity =
        runCatching { AlertSeverity.valueOf(value) }.getOrDefault(AlertSeverity.LOW)
}

package com.example.homehealth.util

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** 日期工具 */
object DateUtils {

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.CHINA)
    private val dateTimeFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA)

    fun formatDate(timestamp: Long): String = dateFormat.format(Date(timestamp))

    fun formatDateTime(timestamp: Long): String = dateTimeFormat.format(Date(timestamp))

    fun today(): Long = System.currentTimeMillis()

    /** 解析日期字符串，支持 yyyy-MM-dd / yyyy/MM/dd / yyyy.MM.dd，失败返回 null */
    fun parseDate(text: String): Long? {
        val t = text.trim()
        if (t.isEmpty()) return null
        val patterns = listOf("yyyy-MM-dd", "yyyy/MM/dd", "yyyy.MM.dd", "yyyyMMdd")
        for (p in patterns) {
            try {
                val sdf = SimpleDateFormat(p, Locale.CHINA)
                sdf.isLenient = false
                val date = sdf.parse(t) ?: continue
                return date.time
            } catch (_: Exception) {
                // 尝试下一个格式
            }
        }
        return null
    }

    /** 相对时间：今天 / 昨天 / N天前 */
    fun relative(timestamp: Long): String {
        val now = Calendar.getInstance()
        val target = Calendar.getInstance().apply { timeInMillis = timestamp }
        val todayStart = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        val diffDays = ((todayStart.timeInMillis -
                (target.apply {
                    set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
                }.timeInMillis)) / (24 * 3600 * 1000L)).toInt()
        return when {
            diffDays <= 0 -> "今天"
            diffDays == 1 -> "昨天"
            diffDays in 2..30 -> "${diffDays}天前"
            else -> formatDate(timestamp)
        }
    }

    /** 根据出生日期计算年龄 */
    fun age(dateOfBirth: String?): Int? {
        val dob = parseDate(dateOfBirth ?: return null) ?: return null
        val birth = Calendar.getInstance().apply { timeInMillis = dob }
        val now = Calendar.getInstance()
        var age = now.get(Calendar.YEAR) - birth.get(Calendar.YEAR)
        if (now.get(Calendar.DAY_OF_YEAR) < birth.get(Calendar.DAY_OF_YEAR)) age--
        return if (age >= 0) age else null
    }
}

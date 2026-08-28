package com.example.homehealth.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.homehealth.data.local.entity.AlertSeverity
import com.example.homehealth.data.local.entity.ParseStatus
import com.example.homehealth.ui.theme.HighSeverity
import com.example.homehealth.ui.theme.LowSeverity
import com.example.homehealth.ui.theme.MediumSeverity
import com.example.homehealth.util.DateUtils

/** 严重程度徽章 */
@Composable
fun SeverityBadge(severity: AlertSeverity, modifier: Modifier = Modifier) {
    val (color, label) = when (severity) {
        AlertSeverity.HIGH -> HighSeverity to "高危"
        AlertSeverity.MEDIUM -> MediumSeverity to "中危"
        AlertSeverity.LOW -> LowSeverity to "提示"
    }
    Box(
        modifier = modifier
            .background(color.copy(alpha = 0.14f), RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 2.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = color,
            fontWeight = FontWeight.SemiBold
        )
    }
}

/** 文档解析状态徽章 */
@Composable
fun ParseStatusBadge(status: ParseStatus, modifier: Modifier = Modifier) {
    val (color, label) = when (status) {
        ParseStatus.PENDING -> Color(0xFF757575) to "待解析"
        ParseStatus.PROCESSING -> MediumSeverity to "解析中"
        ParseStatus.COMPLETED -> Color(0xFF2E7D32) to "已完成"
        ParseStatus.FAILED -> HighSeverity to "失败"
    }
    Box(
        modifier = modifier
            .background(color.copy(alpha = 0.14f), RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 2.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = color,
            fontWeight = FontWeight.Medium
        )
    }
}

/** 成员头像：首字圆形 */
@Composable
fun MemberAvatar(
    name: String,
    modifier: Modifier = Modifier,
    size: Int = 48
) {
    val palette = listOf(
        Color(0xFF00897B), Color(0xFF5C6BC0), Color(0xFF8D6E63),
        Color(0xFF43A047), Color(0xFFF4511E), Color(0xFF6D4C41)
    )
    val color = palette[((name.hashCode() % palette.size) + palette.size) % palette.size]
    Box(
        modifier = modifier
            .size(size.dp)
            .background(color, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = name.firstOrNull()?.toString() ?: "?",
            color = Color.White,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold
        )
    }
}

/** 趋势变化指示（上升/下降/持平） */
@Composable
fun TrendIndicator(
    delta: Double?,
    higherIsWorse: Boolean = true,
    showText: Boolean = true,
    modifier: Modifier = Modifier
) {
    if (delta == null || abs(delta) < 1e-9) {
        if (showText) Text(
            "—",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = modifier
        )
        return
    }
    val up = delta > 0
    val bad = if (higherIsWorse) up else !up
    val color = if (bad) HighSeverity else Color(0xFF2E7D32)
    val arrow = if (up) "↑" else "↓"
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = if (showText) "$arrow ${"%.1f".format(abs(delta))}" else arrow,
            style = MaterialTheme.typography.labelMedium,
            color = color,
            fontWeight = FontWeight.SemiBold
        )
    }
}

/** 信息统计卡片 */
@Composable
fun StatItem(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** 格式化时间戳为「今天 / 昨天 / N天前」 */
fun relativeTime(ts: Long): String = DateUtils.relative(ts)

private fun abs(x: Int): Int = if (x < 0) -x else x
private fun abs(x: Double): Double = if (x < 0) -x else x

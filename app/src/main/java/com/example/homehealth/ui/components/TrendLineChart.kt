package com.example.homehealth.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.example.homehealth.util.DateUtils
import java.text.DecimalFormat
import kotlin.math.abs

/**
 * 简易趋势折线图：x 轴为时间（等距），y 轴为数值。
 * points 需按时间升序传入。
 */
@Composable
fun TrendLineChart(
    points: List<Pair<Long, Double>>,
    modifier: Modifier = Modifier,
    lineColor: Color = MaterialTheme.colorScheme.primary,
    pointColor: Color = MaterialTheme.colorScheme.primary,
    gridColor: Color = MaterialTheme.colorScheme.outlineVariant
) {
    val textMeasurer: TextMeasurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelMedium.copy(
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    val valueFormat = remember { DecimalFormat("#.##") }

    Canvas(modifier = modifier.fillMaxWidth().height(200.dp)) {
        if (points.isEmpty()) return@Canvas

        val values = points.map { it.second }
        val minV = values.min()
        val maxV = values.max()
        val range = (maxV - minV).takeIf { abs(it) > 1e-9 } ?: 1.0

        val leftPad = 44.dp.toPx()
        val topPad = 16.dp.toPx()
        val rightPad = 16.dp.toPx()
        val bottomPad = 26.dp.toPx()
        val chartW = size.width - leftPad - rightPad
        val chartH = size.height - topPad - bottomPad

        // 网格线 + y 轴标签
        for (i in 0..3) {
            val y = topPad + chartH * i / 3f
            drawLine(
                color = gridColor,
                start = Offset(leftPad, y),
                end = Offset(size.width - rightPad, y),
                strokeWidth = 1.dp.toPx()
            )
            val labelValue = minV + range * (3 - i) / 3.0
            drawText(
                textMeasurer = textMeasurer,
                text = valueFormat.format(labelValue),
                style = labelStyle,
                topLeft = Offset(0f, y - labelStyle.fontSize.toPx() / 2)
            )
        }

        // 数据点坐标
        val xs = if (points.size == 1) {
            listOf(leftPad + chartW / 2)
        } else {
            points.indices.map { leftPad + chartW * it / (points.size - 1).toFloat() }
        }
        val ys = points.map {
            topPad + chartH * (1f - (((it.second - minV) / range).toFloat()))
        }

        // 折线
        if (points.size >= 2) {
            val path = Path()
            xs.zip(ys).forEachIndexed { i, (x, y) ->
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(
                path = path,
                color = lineColor,
                style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
            )
        }

        // 数据点
        xs.zip(ys).forEach { (x, y) ->
            drawCircle(color = pointColor, radius = 4.dp.toPx(), center = Offset(x, y))
        }

        // x 轴首尾日期标签
        val firstLabel = DateUtils.formatDate(points.first().first)
        val lastLabel = DateUtils.formatDate(points.last().first)
        val firstMeasure = textMeasurer.measure(firstLabel, labelStyle)
        val lastMeasure = textMeasurer.measure(lastLabel, labelStyle)
        drawText(
            firstMeasure,
            topLeft = Offset(
                (xs.first() - firstMeasure.size.width / 2).coerceIn(0f, size.width - firstMeasure.size.width.toFloat()),
                size.height - firstMeasure.size.height.toFloat()
            )
        )
        drawText(
            lastMeasure,
            topLeft = Offset(
                (xs.last() - lastMeasure.size.width / 2).coerceIn(0f, size.width - lastMeasure.size.width.toFloat()),
                size.height - lastMeasure.size.height.toFloat()
            )
        )

        // 最新值标签
        val lastValueText = valueFormat.format(points.last().second)
        val valueMeasure = textMeasurer.measure(lastValueText, labelStyle)
        drawText(
            valueMeasure,
            topLeft = Offset(
                (xs.last() - valueMeasure.size.width / 2).coerceIn(0f, size.width - valueMeasure.size.width.toFloat()),
                (ys.last() - valueMeasure.size.height - 6.dp.toPx()).coerceAtLeast(0f)
            )
        )
    }
}

/** 空状态提示 */
@Composable
fun EmptyState(message: String, modifier: Modifier = Modifier) {
    Text(
        text = message,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier
    )
}

package com.example.homehealth.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 轻量 Markdown 渲染（问答回答专用）。
 *
 * **为什么自己实现而不用第三方库**：只需要 LLM 回答中出现的高频子集
 * （标题 / 列表 / 粗体 / 行内代码 / 引用 / 分隔线 / 围栏代码块），
 * 引整库要拖着依赖兼容与体积，自己实现约 200 行且离线规则引擎的
 * "• 列表" 同样受益；表格与图片按纯文本降级即可。
 *
 * 解析（[parseMarkdownBlocks] / [parseInline]）是纯 Kotlin 数据变换，
 * 与 Compose 解耦，可直接 JVM 单测；渲染只做数据 → Composable 的映射。
 */

/** 行内样式区间：纯文本 [start, end) 区间，bold/code 各自生效 */
data class MdSpan(val start: Int, val end: Int, val bold: Boolean = false, val code: Boolean = false)

/** 一行去除 MD 标记后的行内内容：可见文本 + 样式区间 */
data class MdInline(val text: String, val spans: List<MdSpan>)

/** 块级元素 */
sealed interface MdBlock {
    data class Heading(val level: Int, val inline: MdInline) : MdBlock
    data class Paragraph(val inline: MdInline) : MdBlock
    data class Bullet(val inline: MdInline) : MdBlock
    data class Ordered(val marker: String, val inline: MdInline) : MdBlock
    data class Quote(val inline: MdInline) : MdBlock
    data class CodeBlock(val lines: List<String>) : MdBlock
    data object Rule : MdBlock
}

private val BULLET_REGEX = Regex("^\\s*[-*•]\\s+(.+)$")
// "1. xxx" 点号后习惯有空格；"2、xxx" 顿号后习惯无空格——分开匹配，且避免把 "6.2 mmol" 这类小数误判为编号
private val ORDERED_REGEX = Regex("^\\s*(\\d{1,3})[.)]\\s+(.+)$")
private val ORDERED_DUNHAO_REGEX = Regex("^\\s*(\\d{1,3})、\\s*(.+)$")
private val HEADING_REGEX = Regex("^(#{1,6})\\s+(.+)$")
private val QUOTE_REGEX = Regex("^\\s*>\\s?(.*)$")
private val RULE_REGEX = Regex("^\\s*(-{3,}|\\*{3,}|—{3,})\\s*$")

/**
 * 把 LLM 回答解析为块级元素列表。
 * 兼容离线规则引擎的 "• " 列表与中文顿号编号（"1、xxx"）。
 */
fun parseMarkdownBlocks(text: String): List<MdBlock> {
    val blocks = mutableListOf<MdBlock>()
    val lines = text.replace("\r\n", "\n").split('\n')
    var i = 0
    while (i < lines.size) {
        val line = lines[i]
        when {
            line.isBlank() -> i++
            line.trimStart().startsWith("```") -> {
                // 围栏代码块：收集到收尾 ``` 为止（缺失收尾时取到文末）
                val code = mutableListOf<String>()
                i++
                while (i < lines.size && !lines[i].trimStart().startsWith("```")) {
                    code.add(lines[i]); i++
                }
                if (i < lines.size) i++
                blocks.add(MdBlock.CodeBlock(code))
            }
            RULE_REGEX.matches(line.trim()) -> { blocks.add(MdBlock.Rule); i++ }
            HEADING_REGEX.matches(line) -> {
                val m = HEADING_REGEX.find(line)!!
                blocks.add(MdBlock.Heading(m.groupValues[1].length, parseInline(m.groupValues[2])))
                i++
            }
            BULLET_REGEX.matches(line) -> {
                val m = BULLET_REGEX.find(line)!!
                blocks.add(MdBlock.Bullet(parseInline(m.groupValues[1])))
                i++
            }
            ORDERED_REGEX.matches(line) || ORDERED_DUNHAO_REGEX.matches(line) -> {
                val m = ORDERED_REGEX.find(line) ?: ORDERED_DUNHAO_REGEX.find(line)!!
                blocks.add(MdBlock.Ordered(m.groupValues[1] + ".", parseInline(m.groupValues[2])))
                i++
            }
            QUOTE_REGEX.matches(line) -> {
                val m = QUOTE_REGEX.find(line)!!
                blocks.add(MdBlock.Quote(parseInline(m.groupValues[1])))
                i++
            }
            else -> { blocks.add(MdBlock.Paragraph(parseInline(line.trim()))); i++ }
        }
    }
    return blocks
}

/**
 * 行内解析：识别 **粗体** 与 `行内代码`，去掉标记并返回样式区间。
 * 未闭合的标记作用到行尾（LLM 输出截断时的兜底表现）。
 */
fun parseInline(line: String): MdInline {
    val out = StringBuilder()
    val spans = mutableListOf<MdSpan>()
    var bold = false
    var code = false
    var runStart = 0
    var i = 0
    fun closeRun() {
        if (out.length > runStart) spans.add(MdSpan(runStart, out.length, bold, code))
    }
    while (i < line.length) {
        when {
            line.startsWith("**", i) -> {
                closeRun(); bold = !bold; i += 2; runStart = out.length
            }
            line[i] == '`' -> {
                closeRun(); code = !code; i += 1; runStart = out.length
            }
            else -> { out.append(line[i]); i += 1 }
        }
    }
    closeRun()
    return MdInline(out.toString(), spans)
}

/** 把行内内容转成带样式的 AnnotatedString；codeBackground 为行内代码底色（可空） */
private fun MdInline.toAnnotatedString(codeBackground: Color?): AnnotatedString = buildAnnotatedString {
    append(text)
    spans.forEach { span ->
        addStyle(
            SpanStyle(
                fontWeight = if (span.bold) FontWeight.Bold else null,
                fontFamily = if (span.code) FontFamily.Monospace else null,
                background = if (span.code) codeBackground ?: Color.Unspecified else Color.Unspecified
            ),
            span.start, span.end
        )
    }
}

/** Markdown 渲染：块间 4dp 间距，标题按级别加粗放大，代码块等宽灰底 */
@Composable
fun MarkdownText(
    markdown: String,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodyMedium,
) {
    val blocks = remember(markdown) { parseMarkdownBlocks(markdown) }
    val onSurface = MaterialTheme.colorScheme.onSurface
    val codeBackground = onSurface.copy(alpha = 0.08f)

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        blocks.forEach { block ->
            when (block) {
                is MdBlock.Heading -> Text(
                    text = block.inline.toAnnotatedString(null),
                    style = style.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = when (block.level) { 1 -> 17.sp; 2 -> 16.sp; else -> 15.sp }
                    ),
                    color = onSurface,
                    modifier = Modifier.padding(top = 4.dp)
                )
                is MdBlock.Paragraph -> Text(
                    text = block.inline.toAnnotatedString(codeBackground),
                    style = style,
                    color = onSurface
                )
                is MdBlock.Bullet -> Row {
                    Text(
                        text = "•",
                        style = style,
                        color = onSurface.copy(alpha = 0.7f),
                        modifier = Modifier.padding(end = 8.dp)
                    )
                    Text(
                        text = block.inline.toAnnotatedString(codeBackground),
                        style = style,
                        color = onSurface,
                        modifier = Modifier.weight(1f)
                    )
                }
                is MdBlock.Ordered -> Row {
                    Text(
                        text = block.marker,
                        style = style,
                        color = onSurface.copy(alpha = 0.7f),
                        modifier = Modifier.padding(end = 6.dp)
                    )
                    Text(
                        text = block.inline.toAnnotatedString(codeBackground),
                        style = style,
                        color = onSurface,
                        modifier = Modifier.weight(1f)
                    )
                }
                is MdBlock.Quote -> Row(Modifier.height(IntrinsicSize.Min)) {
                    Box(
                        Modifier
                            .fillMaxHeight()
                            .width(3.dp)
                            .background(onSurface.copy(alpha = 0.25f))
                    )
                    Text(
                        text = block.inline.toAnnotatedString(codeBackground),
                        style = style,
                        color = onSurface.copy(alpha = 0.85f),
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
                is MdBlock.CodeBlock -> Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .background(codeBackground)
                        .padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    block.lines.forEach { line ->
                        Text(
                            text = line,
                            style = style.copy(fontFamily = FontFamily.Monospace, fontSize = 12.sp),
                            color = onSurface,
                            // 长代码行横向滚动，不撑破气泡
                            modifier = Modifier.horizontalScroll(rememberScrollState())
                        )
                    }
                }
                MdBlock.Rule -> HorizontalDivider(
                    color = onSurface.copy(alpha = 0.15f),
                    modifier = Modifier.padding(vertical = 2.dp)
                )
            }
        }
    }
}

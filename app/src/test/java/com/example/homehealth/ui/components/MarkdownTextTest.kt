package com.example.homehealth.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 轻量 Markdown 解析器单测（纯 JVM，不依赖 Compose 运行时）：
 * 块级识别（标题/列表/引用/分隔线/围栏代码）与行内样式（**粗体**、`代码`）。
 * 覆盖 LLM 回答与离线规则引擎（"• 列表"）两种输出风格。
 */
class MarkdownTextTest {

    @Test
    fun `标题按井号数量分级并去除标记`() {
        val blocks = parseMarkdownBlocks("## 血糖分析\n### 详细指标")
        assertEquals(2, blocks.size)
        val h1 = blocks[0] as MdBlock.Heading
        assertEquals(2, h1.level)
        assertEquals("血糖分析", h1.inline.text)
        val h2 = blocks[1] as MdBlock.Heading
        assertEquals(3, h2.level)
        assertEquals("详细指标", h2.inline.text)
    }

    @Test
    fun `短横线与圆点列表都识别为 Bullet`() {
        val blocks = parseMarkdownBlocks("- 第一条\n• 第二条\n* 第三条")
        assertEquals(3, blocks.size)
        assertTrue(blocks.all { it is MdBlock.Bullet })
        assertEquals("第一条", (blocks[0] as MdBlock.Bullet).inline.text)
        assertEquals("第二条", (blocks[1] as MdBlock.Bullet).inline.text)
    }

    @Test
    fun `数字编号识别为 Ordered 且兼容顿号`() {
        val blocks = parseMarkdownBlocks("1. 第一步\n2、第二步")
        assertTrue(blocks[0] is MdBlock.Ordered)
        assertTrue(blocks[1] is MdBlock.Ordered)
        assertEquals("1.", (blocks[0] as MdBlock.Ordered).marker)
        assertEquals("2.", (blocks[1] as MdBlock.Ordered).marker)
    }

    @Test
    fun `日期开头的普通行不会被误判为编号列表`() {
        val blocks = parseMarkdownBlocks("2026-08-01：6.2 mmol/L")
        assertTrue(blocks[0] is MdBlock.Paragraph)
        assertEquals("2026-08-01：6.2 mmol/L", (blocks[0] as MdBlock.Paragraph).inline.text)
    }

    @Test
    fun `引用与分隔线`() {
        val blocks = parseMarkdownBlocks("> 医生建议复查\n———")
        assertTrue(blocks[0] is MdBlock.Quote)
        assertEquals("医生建议复查", (blocks[0] as MdBlock.Quote).inline.text)
        assertEquals(MdBlock.Rule, blocks[1])
    }

    @Test
    fun `围栏代码块整体收集且不解析行内标记`() {
        val blocks = parseMarkdownBlocks("说明：\n```\n**这不是粗体**\n血糖=6.2\n```\n结束")
        val code = blocks[1] as MdBlock.CodeBlock
        assertEquals(listOf("**这不是粗体**", "血糖=6.2"), code.lines)
        assertEquals(3, blocks.size)
    }

    @Test
    fun `粗体与行内代码去掉标记并携带样式区间`() {
        val inline = parseInline("**血糖** 偏高，参考 `3.9-6.1`")
        assertEquals("血糖 偏高，参考 3.9-6.1", inline.text)
        // 标记外的普通文本也是一个 run（无样式 span），共 3 段
        assertEquals(3, inline.spans.size)
        val bold = inline.spans.first { it.bold }
        assertEquals(0, bold.start)
        assertEquals(2, bold.end)
        val code = inline.spans.first { it.code }
        assertEquals(9, code.start)
        assertEquals(16, code.end)
    }

    @Test
    fun `未闭合的粗体标记作用到行尾`() {
        val inline = parseInline("结论：**血糖偏高")
        assertEquals("结论：血糖偏高", inline.text)
        val bold = inline.spans.filter { it.bold }
        assertEquals(1, bold.size)
        assertEquals(3, bold[0].start)
        assertEquals(7, bold[0].end)
    }

    @Test
    fun `引用来源编号行按普通段落处理`() {
        val blocks = parseMarkdownBlocks("[1] 空腹血糖 · 2026-08-01 · 6.2 mmol/L")
        assertTrue(blocks[0] is MdBlock.Paragraph)
    }

    @Test
    fun `离线引擎风格的多行输出逐行成块`() {
        val text = "根据已保存的记录，为测试成员整理如下：\n\n【空腹血糖】共 2 条记录\n  • 2026-08-01：6.2 mmol/L"
        val blocks = parseMarkdownBlocks(text)
        assertEquals(3, blocks.size)
        assertTrue(blocks[0] is MdBlock.Paragraph)
        assertTrue(blocks[1] is MdBlock.Paragraph)
        assertTrue(blocks[2] is MdBlock.Bullet)
        assertEquals("2026-08-01：6.2 mmol/L", (blocks[2] as MdBlock.Bullet).inline.text)
    }
}

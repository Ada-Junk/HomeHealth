package com.example.homehealth.data.remote

import com.example.homehealth.data.remote.LlmStreamParser.Delta
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * SSE 流式解析单测。
 *
 * 这一层最值得测：它同时是「最容易写错」和「最容易测」的代码 —— 纯函数、无 Android 依赖，
 * 而线上出问题（某家供应商少回一个字段、错误藏在 200 响应里）时真机抓包成本很高。
 * 因此这里把两家协议的分支逐个钉死，包括"畸形负载不能崩"这条底线。
 */
class LlmStreamParserTest {

    // ---------- 行级：SSE 帧结构 ----------

    @Test
    fun `payloadOf 兼容有无空格两种 data 写法`() {
        assertEquals("""{"a":1}""", LlmStreamParser.payloadOf("""data: {"a":1}"""))
        assertEquals("""{"a":1}""", LlmStreamParser.payloadOf("""data:{"a":1}"""))
    }

    @Test
    fun `payloadOf 对非数据行与空负载返回 null`() {
        assertNull(LlmStreamParser.payloadOf(""))
        assertNull(LlmStreamParser.payloadOf("   "))
        assertNull(LlmStreamParser.payloadOf("event: content_block_delta"))
        assertNull(LlmStreamParser.payloadOf(": keep-alive"))
        assertNull(LlmStreamParser.payloadOf("id: 42"))
        assertNull(LlmStreamParser.payloadOf("data:"))
        assertNull(LlmStreamParser.payloadOf("data:   "))
        // 只有前缀匹配，不接受 "database:" 这类误命中
        assertNull(LlmStreamParser.payloadOf("database: x"))
    }

    @Test
    fun `isStructuralLine 区分正常 SSE 帧与意外内容`() {
        // 正常结构：空行 / 注释 / 三种字段
        assertTrue(LlmStreamParser.isStructuralLine(""))
        assertTrue(LlmStreamParser.isStructuralLine("   "))
        assertTrue(LlmStreamParser.isStructuralLine(": ping"))
        assertTrue(LlmStreamParser.isStructuralLine("event: message_stop"))
        assertTrue(LlmStreamParser.isStructuralLine("id: 7"))
        assertTrue(LlmStreamParser.isStructuralLine("retry: 1000"))
        // 意外内容：供应商把 JSON 错误对象直接当正文返回
        assertFalse(LlmStreamParser.isStructuralLine("""{"error":{"message":"invalid api key"}}"""))
        // data 行由 payloadOf 负责，不属于"结构性行"
        assertFalse(LlmStreamParser.isStructuralLine("""data: {"a":1}"""))
    }

    // ---------- OpenAI 兼容协议 ----------

    @Test
    fun `parseOpenAi 解析正文增量`() {
        val payload = """{"choices":[{"index":0,"delta":{"content":"血糖"},"finish_reason":null}]}"""
        assertEquals(listOf(Delta.Content("血糖")), LlmStreamParser.parseOpenAi(payload))
    }

    @Test
    fun `parseOpenAi 识别 DONE 哨兵`() {
        assertEquals(listOf(Delta.Done), LlmStreamParser.parseOpenAi("[DONE]"))
    }

    @Test
    fun `parseOpenAi 解析 reasoning_content 思考增量`() {
        val payload = """{"choices":[{"delta":{"reasoning_content":"先看血糖"},"finish_reason":null}]}"""
        assertEquals(listOf(Delta.Thinking("先看血糖")), LlmStreamParser.parseOpenAi(payload))
    }

    @Test
    fun `parseOpenAi 兼容 thinking 字段的思考增量`() {
        val payload = """{"choices":[{"delta":{"thinking":"步骤一"},"finish_reason":null}]}"""
        assertEquals(listOf(Delta.Thinking("步骤一")), LlmStreamParser.parseOpenAi(payload))
    }

    @Test
    fun `parseOpenAi 首块仅含 role 时不产出增量`() {
        val payload = """{"choices":[{"delta":{"role":"assistant"},"finish_reason":null}]}"""
        assertTrue(LlmStreamParser.parseOpenAi(payload).isEmpty())
    }

    @Test
    fun `parseOpenAi 空 content 不产出增量`() {
        val payload = """{"choices":[{"delta":{"content":""},"finish_reason":null}]}"""
        assertTrue(LlmStreamParser.parseOpenAi(payload).isEmpty())
    }

    @Test
    fun `parseOpenAi 识别 finish_reason 截断`() {
        val payload = """{"choices":[{"delta":{},"finish_reason":"length"}]}"""
        assertEquals(listOf(Delta.Truncated), LlmStreamParser.parseOpenAi(payload))
    }

    @Test
    fun `parseOpenAi 同块同时给出正文与截断时两者都不丢`() {
        val payload = """{"choices":[{"delta":{"content":"6.2"},"finish_reason":"length"}]}"""
        assertEquals(
            listOf(Delta.Content("6.2"), Delta.Truncated),
            LlmStreamParser.parseOpenAi(payload)
        )
    }

    @Test
    fun `parseOpenAi 负载内 error 转 Failure`() {
        val payload = """{"error":{"code":"1234","message":"余额不足"}}"""
        assertEquals(listOf(Delta.Failure("余额不足")), LlmStreamParser.parseOpenAi(payload))
    }

    @Test
    fun `parseOpenAi 畸形负载不抛异常`() {
        // 单个坏分块不能中断整条回答，也不该把用户的问题变成崩溃
        assertTrue(LlmStreamParser.parseOpenAi("{not json").isEmpty())
        assertTrue(LlmStreamParser.parseOpenAi("").isEmpty())
        assertTrue(LlmStreamParser.parseOpenAi("null").isEmpty())
        // 合法 JSON 但结构不对（缺 choices）
        assertTrue(LlmStreamParser.parseOpenAi("""{"id":"x"}""").isEmpty())
        assertTrue(LlmStreamParser.parseOpenAi("""{"choices":[]}""").isEmpty())
    }

    // ---------- Anthropic Messages API ----------

    @Test
    fun `parseAnthropic 解析 text_delta 正文`() {
        val payload = """{"type":"content_block_delta","index":0,"delta":{"type":"text_delta","text":"血糖"}}"""
        assertEquals(listOf(Delta.Content("血糖")), LlmStreamParser.parseAnthropic(payload))
    }

    @Test
    fun `parseAnthropic 解析 thinking_delta 思考`() {
        val payload = """{"type":"content_block_delta","index":0,"delta":{"type":"thinking_delta","thinking":"先看"}}"""
        assertEquals(listOf(Delta.Thinking("先看")), LlmStreamParser.parseAnthropic(payload))
    }

    @Test
    fun `parseAnthropic 忽略与问答无关的 delta 类型`() {
        val signature = """{"type":"content_block_delta","index":0,"delta":{"type":"signature_delta","signature":"abc"}}"""
        assertTrue(LlmStreamParser.parseAnthropic(signature).isEmpty())
        // delta 缺失时也不能崩
        assertTrue(LlmStreamParser.parseAnthropic("""{"type":"content_block_delta"}""").isEmpty())
    }

    @Test
    fun `parseAnthropic 把工具调用拆成 id 名与参数分片`() {
        // id / name 只在 content_block_start 出现一次
        val start = """{"type":"content_block_start","index":1,"content_block":{"type":"tool_use","id":"toolu_1","name":"search_records"}}"""
        assertEquals(
            listOf(Delta.ToolCallFragment(index = 1, id = "toolu_1", name = "search_records")),
            LlmStreamParser.parseAnthropic(start)
        )
        // 参数是逐片来的 partial_json，必须按 index 累积
        val arg1 = """{"type":"content_block_delta","index":1,"delta":{"type":"input_json_delta","partial_json":"{\"qu"}}"""
        val arg2 = """{"type":"content_block_delta","index":1,"delta":{"type":"input_json_delta","partial_json":"ery\":\"血糖\"}"}}"""
        assertEquals(
            listOf(Delta.ToolCallFragment(index = 1, argsFragment = """{"qu""")),
            LlmStreamParser.parseAnthropic(arg1)
        )
        assertEquals(
            listOf(Delta.ToolCallFragment(index = 1, argsFragment = """ery":"血糖"}""")),
            LlmStreamParser.parseAnthropic(arg2)
        )
        // 纯文本内容块不该被当成工具
        val textBlock = """{"type":"content_block_start","index":0,"content_block":{"type":"text","text":""}}"""
        assertTrue(LlmStreamParser.parseAnthropic(textBlock).isEmpty())
    }

    @Test
    fun `parseOpenAi 把工具调用拆成按 index 分组的分片`() {
        val first = """{"choices":[{"index":0,"delta":{"tool_calls":[{"index":0,"id":"call_1","type":"function","function":{"name":"search_records","arguments":""}}]},"finish_reason":null}]}"""
        val fragment = """{"choices":[{"index":0,"delta":{"tool_calls":[{"index":0,"function":{"arguments":"{\"query\":\"血糖\"}"}}]},"finish_reason":null}]}"""
        assertEquals(
            listOf(Delta.ToolCallFragment(index = 0, id = "call_1", name = "search_records", argsFragment = "")),
            LlmStreamParser.parseOpenAi(first)
        )
        assertEquals(
            listOf(Delta.ToolCallFragment(index = 0, argsFragment = """{"query":"血糖"}""")),
            LlmStreamParser.parseOpenAi(fragment)
        )
    }

    @Test
    fun `ToolCallAssembler 把分片拼成一次完整调用`() {
        val assembler = ToolCallAssembler()
        assertTrue(assembler.isEmpty())
        assembler.accept(Delta.ToolCallFragment(index = 0, id = "call_1", name = "search_records"))
        assembler.accept(Delta.ToolCallFragment(index = 0, argsFragment = """{"query":"""))
        assembler.accept(Delta.ToolCallFragment(index = 0, argsFragment = """"血糖"}"""))
        // 两个并行的调用按 index 分组，不能串到一起
        assembler.accept(Delta.ToolCallFragment(index = 1, id = "call_2", name = "get_alerts"))

        val calls = assembler.build()
        assertEquals(2, calls.size)
        assertEquals("search_records", calls[0].name)
        assertEquals("""{"query":"血糖"}""", calls[0].argsJson)
        assertEquals("call_2", calls[1].id)
        // 参数缺失退化成空对象：工具侧容错解析会把它当成一次可纠正的失败，而不是让整轮崩掉
        assertEquals("{}", calls[1].argsJson)
    }

    @Test
    fun `ToolCallAssembler 无名称时视为没有工具调用`() {
        val assembler = ToolCallAssembler()
        // 只收到参数分片（缺 content_block_start）时不该凭空造出一个无名工具
        assembler.accept(Delta.ToolCallFragment(index = 0, argsFragment = """{"a":1}"""))
        assertTrue(assembler.isEmpty())
        assertTrue(assembler.build().isEmpty())
    }

    @Test
    fun `parseAnthropic 识别 message_delta 的 max_tokens 截断`() {
        val payload = """{"type":"message_delta","delta":{"stop_reason":"max_tokens"},"usage":{"output_tokens":2048}}"""
        assertEquals(listOf(Delta.Truncated), LlmStreamParser.parseAnthropic(payload))
    }

    @Test
    fun `parseAnthropic 正常结束原因不报截断`() {
        val endTurn = """{"type":"message_delta","delta":{"stop_reason":"end_turn"}}"""
        val stopSeq = """{"type":"message_delta","delta":{"stop_reason":"stop_sequence"}}"""
        assertTrue(LlmStreamParser.parseAnthropic(endTurn).isEmpty())
        assertTrue(LlmStreamParser.parseAnthropic(stopSeq).isEmpty())
    }

    @Test
    fun `parseAnthropic 以 message_stop 结束`() {
        assertEquals(listOf(Delta.Done), LlmStreamParser.parseAnthropic("""{"type":"message_stop"}"""))
    }

    @Test
    fun `parseAnthropic 忽略 message_start 与 ping`() {
        val start = """{"type":"message_start","message":{"id":"msg_1","role":"assistant"}}"""
        val blockStart = """{"type":"content_block_start","index":0,"content_block":{"type":"text","text":""}}"""
        assertTrue(LlmStreamParser.parseAnthropic(start).isEmpty())
        assertTrue(LlmStreamParser.parseAnthropic(blockStart).isEmpty())
        assertTrue(LlmStreamParser.parseAnthropic("""{"type":"ping"}""").isEmpty())
    }

    @Test
    fun `parseAnthropic 负载内 error 转 Failure`() {
        val payload = """{"type":"error","error":{"type":"overloaded_error","message":"Overloaded"}}"""
        assertEquals(listOf(Delta.Failure("Overloaded")), LlmStreamParser.parseAnthropic(payload))
    }

    @Test
    fun `parseAnthropic 不认 OpenAI 的 DONE 哨兵`() {
        // Anthropic 用 message_stop 结束，没有 [DONE]；这里必须退化成"听不懂就忽略"，
        // 而不是被哨兵字符串带崩（两家协议共用一个读取循环，错认会导致提前收尾或异常）
        assertTrue(LlmStreamParser.parseAnthropic("[DONE]").isEmpty())
        assertTrue(LlmStreamParser.parseAnthropic("{not json").isEmpty())
        assertTrue(LlmStreamParser.parseAnthropic("null").isEmpty())
    }
}

package com.example.homehealth.data.remote

import com.example.homehealth.data.local.entity.FamilyMember
import com.example.homehealth.domain.tool.HealthTool
import com.example.homehealth.domain.tool.ToolContext
import com.example.homehealth.domain.tool.ToolProvider
import com.example.homehealth.domain.tool.ToolResult
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * ReAct 循环的策略测试（假网关 + 假工具，不碰网络与设备）。
 *
 * 这个文件是整个 A5 里最该存在的测试：循环的价值全在"什么时候该停、失败了怎么办"，
 * 而这些分支靠真机验证要凑齐网络超时、供应商抽风、模型乱调工具等场景，
 * 成本高且不可复现。把 LLM 决策抽象成 [AgentLlmGateway] 之后，这些都能脚本化。
 */
class ReActLoopTest {

    private val member = FamilyMember(id = "m1", name = "妈妈", relationship = "母亲", gender = "female")
    private val context = ToolContext(member = member, question = "我最近身体有什么问题吗", imageBase64 = null)

    // ---------- 正常收敛 ----------

    @Test
    fun `第一轮调工具第二轮给出回答时按顺序产出事件`() {
        val search = FakeTool("search_records") { ToolResult.ok("[1] 血糖 · 2026-09-10 · 6.2 mmol/L") }
        val gateway = FakeGateway(
            listOf(
                toolTurn(AgentToolCall("c1", "search_records", """{"query":"血糖"}""")),
                answerTurn("血糖 6.2 mmol/L，略高于参考范围。")
            )
        )

        val events = runAgent(gateway, listOf(search))

        assertEquals(1, search.calls)
        assertTrue(events.any { it is AgentEvent.ToolCallStarted && it.name == "search_records" })
        assertTrue(events.any { it is AgentEvent.ToolCallFinished && it.ok })
        val completed = events.filterIsInstance<AgentEvent.Completed>().single()
        assertTrue(completed.text.contains("6.2"))
        // 检索类工具的返回要进入「数据依据」，且原文保留
        assertEquals(1, completed.evidence.size)
        assertTrue(completed.evidence.first().contains("mmol/L"))
        assertEquals(listOf("search_records"), completed.toolNames)
    }

    // ---------- 最后一轮的护栏 ----------

    @Test
    fun `最后一轮不再向模型提供工具`() {
        val search = FakeTool("search_records") { ToolResult.ok("2 条记录") }
        // 前四轮都在调工具，第五轮才回答（这是模型配合时的正常轨迹）
        val script = List(AgentConfig.MAX_TURNS - 1) {
            toolTurn(AgentToolCall("c$it", "search_records", """{"query":"血糖"}"""))
        } + answerTurn("结论")

        val gateway = FakeGateway(script)
        val events = runAgent(gateway, listOf(search))

        assertEquals(AgentConfig.MAX_TURNS, gateway.declaredTools.size)
        // 前几轮有工具可用
        assertTrue(gateway.declaredTools.first().isNotEmpty())
        // 最后一轮撤掉工具：逼模型用已有证据作答，避免"轮数用尽 + 还有未回填的工具调用"这种死角
        assertTrue("最后一轮仍在提供工具", gateway.declaredTools.last().isEmpty())
        assertTrue(events.any { it is AgentEvent.Completed })
    }

    // ---------- 工具轮的正文不算答案 ----------

    @Test
    fun `工具轮产出的正文进入思考轨迹而不是答案`() {
        val search = FakeTool("search_records") { ToolResult.ok("1 条记录") }
        val gateway = FakeGateway(
            listOf(
                AgentTurnResult(
                    text = "我先查一下血糖记录。",
                    thinking = null,
                    toolCalls = listOf(AgentToolCall("c1", "search_records", "{}")),
                    truncated = false
                ),
                answerTurn("最终结论")
            )
        )

        val events = runAgent(gateway, listOf(search))

        val answers = events.filterIsInstance<AgentEvent.Answer>()
        // 只有最终回答进 Answer 通道；中间轮的推理绝不能先上屏再撤回
        assertEquals(1, answers.size)
        assertEquals("最终结论", answers.single().delta)
        assertTrue(events.filterIsInstance<AgentEvent.Thinking>().any { it.delta.contains("我先查一下") })
    }

    // ---------- 失败处理 ----------

    @Test
    fun `工具抛异常时转为失败观测且循环继续`() {
        val broken = FakeTool("search_records") { throw IllegalStateException("数据库锁了") }
        val gateway = FakeGateway(
            listOf(
                toolTurn(AgentToolCall("c1", "search_records", "{}")),
                answerTurn("暂时查不到数据。")
            )
        )

        val events = runAgent(gateway, listOf(broken))

        val finished = events.filterIsInstance<AgentEvent.ToolCallFinished>().single()
        assertFalse(finished.ok)
        assertTrue(finished.summary.contains("数据库锁了"))
        assertTrue(events.any { it is AgentEvent.Completed })
    }

    @Test
    fun `同一工具连续失败两次后被禁用并不再出现在工具声明里`() {
        val broken = FakeTool("search_records") { ToolResult.fail("参数不合法") }
        val healthy = FakeTool("get_alerts") { ToolResult.ok("无告警") }
        val gateway = FakeGateway(
            listOf(
                toolTurn(AgentToolCall("c1", "search_records", "{}")),
                toolTurn(AgentToolCall("c2", "search_records", "{}")),
                answerTurn("结论")
            )
        )

        runAgent(gateway, listOf(broken, healthy))

        // 第 1、2 轮还能看到它；累计失败达上限后，第 3 轮的工具声明里应当已经没有它
        assertTrue("search_records" in gateway.declaredTools[0])
        assertTrue("search_records" in gateway.declaredTools[1])
        assertFalse("连续失败后仍向模型提供该工具", "search_records" in gateway.declaredTools[2])
        // 别的工具不受影响
        assertTrue("get_alerts" in gateway.declaredTools[2])
    }

    @Test
    fun `网关失败且没有任何证据时抛出以便上层回退`() {
        val gateway = object : AgentLlmGateway {
            override suspend fun turn(
                entries: List<AgentEntry>,
                tools: List<HealthTool>,
                onDelta: suspend (String) -> Unit,
                onThinking: suspend (String) -> Unit
            ): AgentTurnResult = throw IllegalStateException("网络异常")
        }

        val error = runCatching { runAgent(gateway, emptyList()) }.exceptionOrNull()

        // 本类不自己编答案：交给上层回退本地规则引擎，比丢一份半截结果给用户更有用
        assertTrue(error is IllegalStateException)
    }

    // ---------- 观测预算 ----------

    @Test
    fun `超预算的工具结果被截断并标注省略`() {
        val huge = "血糖记录 " + "x".repeat(AgentConfig.OBSERVATION_CHAR_BUDGET * 2)
        val search = FakeTool("search_records") { ToolResult.ok(huge) }
        val gateway = FakeGateway(
            listOf(
                toolTurn(AgentToolCall("c1", "search_records", "{}")),
                answerTurn("结论")
            )
        )

        runAgent(gateway, listOf(search))

        // 第 2 轮拿到的对话里，工具结果应当已被截断（否则上下文会被一次查询撑爆）
        val results = gateway.entriesSeen[1].filterIsInstance<AgentEntry.ToolResults>().single()
        val text = results.results.single().text
        assertTrue(text.length < huge.length)
        assertTrue(text.contains("因上下文预算省略"))
    }

    // ---------- 辅助 ----------

    private fun runAgent(gateway: AgentLlmGateway, tools: List<HealthTool>): List<AgentEvent> =
        runBlocking {
            ReActAgent(gateway, FakeRegistry(tools)).run(context, "3 条记录").toList()
        }

    private fun toolTurn(call: AgentToolCall) = AgentTurnResult(
        text = "",
        thinking = null,
        toolCalls = listOf(call),
        truncated = false
    )

    private fun answerTurn(text: String) = AgentTurnResult(
        text = text,
        thinking = null,
        toolCalls = emptyList(),
        truncated = false
    )

    private class FakeTool(
        override val name: String,
        private val result: () -> ToolResult
    ) : HealthTool {
        override val description = "$name：测试用工具，需要时调用"
        override val parametersJsonSchema = """{"type":"object","properties":{}}"""
        var calls = 0

        override suspend fun execute(context: ToolContext, argsJson: String): ToolResult {
            calls++
            return result()
        }
    }

    private class FakeRegistry(override val all: List<HealthTool>) : ToolProvider {
        override fun availableFor(context: ToolContext): List<HealthTool> = all
        override fun byName(name: String): HealthTool? = all.firstOrNull { it.name == name }
    }

    private class FakeGateway(private val scripted: List<AgentTurnResult>) : AgentLlmGateway {
        val declaredTools = mutableListOf<List<String>>()
        val entriesSeen = mutableListOf<List<AgentEntry>>()
        private var index = 0

        override suspend fun turn(
            entries: List<AgentEntry>,
            tools: List<HealthTool>,
            onDelta: suspend (String) -> Unit,
            onThinking: suspend (String) -> Unit
        ): AgentTurnResult {
            entriesSeen += entries
            declaredTools += tools.map { it.name }
            val turn = scripted.getOrNull(index++) ?: error("脚本轮次已用尽（第 ${index} 轮）")
            if (turn.text.isNotEmpty()) onDelta(turn.text)
            return turn
        }
    }
}

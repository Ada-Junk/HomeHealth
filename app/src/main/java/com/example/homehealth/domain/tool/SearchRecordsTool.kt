package com.example.homehealth.domain.tool

import com.example.homehealth.data.local.entity.FamilyMember
import com.example.homehealth.data.local.entity.HealthRecord
import com.example.homehealth.data.remote.QaRetriever
import com.example.homehealth.domain.repository.HealthRecordRepository
import com.example.homehealth.util.DateUtils
import com.example.homehealth.util.HealthTypes
import com.example.homehealth.util.SchemaNormalizer
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 工具 ①：检索健康记录。
 *
 * 两条查询路径，对应两类提问方式：
 * - 有 `metric_types`：直接按指标类型取最近 N 条（模型已经知道要看什么指标）；
 * - 只有 `query`：走 A1 的 BM25 检索（复用 `QaRetriever`，与快路径同一套召回逻辑，
 *   避免"Agent 路径和快路径搜出不同结果"这种难以解释的差异）。
 */
@Singleton
class SearchRecordsTool @Inject constructor(
    private val healthRecordRepository: HealthRecordRepository
) : HealthTool {

    override val name = NAME

    override val description =
        "检索该成员已保存的健康记录，返回带编号的记录明细（含单位与参考范围）。" +
            "回答任何涉及具体数值、「某项指标多少」「最近怎么样」「有没有变化」的问题前都应先调用本工具，" +
            "不要凭记忆或推测回答。"

    override val parametersJsonSchema = """
        {
          "type": "object",
          "properties": {
            "query": {
              "type": "string",
              "description": "自然语言关键词，如「血糖」「LDL」「最近的血脂」"
            },
            "metric_types": {
              "type": "array",
              "items": { "type": "string" },
              "description": "限定指标类型（英文键，如 blood_glucose、ldl）或其中文名；不确定时不要传"
            },
            "limit": {
              "type": "integer",
              "description": "最多返回条数，默认 10，上限 20"
            }
          },
          "required": ["query"]
        }
    """.trimIndent()

    override suspend fun execute(context: ToolContext, argsJson: String): ToolResult {
        val args = ToolArgs.parse(argsJson)
        val query = ToolArgs.str(args, "query") ?: context.question
        val limit = (ToolArgs.int(args, "limit") ?: DEFAULT_LIMIT).coerceIn(1, MAX_LIMIT)
        // 指标类型先过归一化，再要求能对上字典：模型很容易把中文当类型传（type="血脂"），
        // 直接当英文键查会一条都查不到。解析不出来的**忽略而不是报错** ——
        // 报错会让模型反复重试同一个错参数，白白烧掉轮数。
        val types = ToolArgs.strList(args, "metric_types")
            .map { SchemaNormalizer.normalizeType(it) }
            .filter { HealthTypes.def(it) != null }
            .distinct()

        if (query.isBlank() && types.isEmpty()) {
            return ToolResult.fail("缺少检索条件：query 与 metric_types 至少要提供一个。")
        }

        return runCatching {
            val member = context.member
            val records = if (types.isEmpty()) {
                val corpus = healthRecordRepository.getAllByMember(member.id)
                if (corpus.isEmpty()) {
                    return@runCatching ToolResult.ok("该成员还没有任何健康记录，请先上传体检报告或手动录入。")
                }
                QaRetriever.index(corpus)
                    .search(
                        query = query,
                        topK = limit,
                        // 强指标词闸门与快路径保持一致；泛化问句（无强词）则不限制，交给 BM25 打分
                        restrictTo = QaRetriever.strongTermsOf(query).takeIf { it.isNotEmpty() }
                    )
                    .map { it.record }
            } else {
                types
                    .flatMap { type -> healthRecordRepository.getRecentRecords(member.id, type, limit) }
                    .sortedByDescending { it.recordDate }
                    .take(limit)
            }

            if (records.isEmpty()) {
                return@runCatching ToolResult.ok(
                    "没有找到与「$query」相关的记录。可以换一个说法再查一次，" +
                        "或先确认该指标是否已经录入。"
                )
            }
            ToolResult.ok(render(member, records))
        }.getOrElse { e ->
            ToolResult.fail("检索记录失败：${e.message ?: e.javaClass.simpleName}")
        }
    }

    /** 与问答上下文同一套明细口径：指标 / 日期 / 值 / 单位 / 参考范围 / 备注 */
    private fun render(member: FamilyMember, records: List<HealthRecord>): String = buildString {
        appendLine("已找到 ${records.size} 条记录（成员：${member.name}）：")
        records.forEachIndexed { index, record ->
            val unit = record.unit.trim().ifBlank { HealthTypes.unit(record.type) }
            append("[${index + 1}] ${HealthTypes.label(record.type)} · ")
            append("${DateUtils.formatDate(record.recordDate)} · ${record.value}")
            if (unit.isNotBlank()) append(" $unit")
            HealthTypes.def(record.type)?.rangeFor(member.gender)?.let { append("（参考 ${it.text}）") }
            record.notes?.trim()?.takeIf { it.isNotBlank() }?.let { append("；备注：$it") }
            appendLine()
        }
    }

    companion object {
        const val NAME = "search_records"
        private const val DEFAULT_LIMIT = 10
        private const val MAX_LIMIT = 20
    }
}

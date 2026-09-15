package com.example.homehealth.data.repository

import android.util.Log
import com.example.homehealth.data.SettingsPrefs
import com.example.homehealth.data.local.dao.HealthRecordDao
import com.example.homehealth.data.local.dao.QAHistoryDao
import com.example.homehealth.data.local.entity.FamilyMember
import com.example.homehealth.data.local.entity.HealthRecord
import com.example.homehealth.data.local.entity.QAHistory
import com.example.homehealth.data.remote.LlmClient
import com.example.homehealth.data.remote.LlmProviders
import com.example.homehealth.data.remote.LocalQaEngine
import com.example.homehealth.data.remote.QaRetriever
import com.example.homehealth.domain.repository.QARepository
import com.example.homehealth.util.DateUtils
import com.example.homehealth.util.HealthTypes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class QARepositoryImpl @Inject constructor(
    private val qaHistoryDao: QAHistoryDao,
    private val healthRecordDao: HealthRecordDao,
    private val settingsPrefs: SettingsPrefs,
    private val localQaEngine: LocalQaEngine,
    private val llmClient: LlmClient
) : QARepository {

    override fun observeHistory(memberId: String): Flow<List<QAHistory>> =
        qaHistoryDao.observeByMember(memberId)

    override suspend fun getAllHistory(): List<QAHistory> = qaHistoryDao.getAll()

    override suspend fun clearHistory(memberId: String) = qaHistoryDao.clearByMember(memberId)

    override suspend fun ask(member: FamilyMember, question: String): QAHistory =
        withContext(Dispatchers.IO) {
            var answer: String? = null
            var sources: List<String> = emptyList()
            var thinking: String? = null // 深度思考模型的思考过程
            var llmFailed = false

            // 汇总成员健康记录（供 AI 上下文与本地引擎共用）：
            // 单次查询取「每指标最近 N 条」+ 内存分组，避免逐指标 N+1；
            // 查询本身带每组 Top-N 限制，控制结果集上限。
            // 注意：**不按 HealthTypes.def 过滤**——解析确认页入库的都是用户核对过的真实数据，
            // 字典外指标（少见项目）被静默丢弃曾导致「导入多个指标、问答只见一个」；
            // label / 单位 / 参考范围对未知类型均有兜底渲染（显示原始类型名、参考范围"—"）。
            val recordsByType = healthRecordDao
                .getRecentPerTypeByMember(member.id, RECORDS_PER_TYPE)
                .groupBy { it.type }
                .mapValues { (_, list) -> list.take(RECORDS_PER_TYPE) }

            // ---- A1 真实检索：BM25 Top-K 召回 + 引用溯源 ----
            // 闸门：问题里必须出现「强指标词」（指标名 / 别名 / 成组词，见 QaRetriever.strongTermsOf）
            // 才走检索。泛化总结类问题（"整体健康状况怎么样"）没有可靠的指标词，若放行检索，
            // 弱字匹配（"身体"的"体"撞上体重记录）会把上下文窄化成一两个指标——
            // 此时回退旧的「每指标最近 N 条」全量摘要，行为与检索上线前完全一致。
            val strongTerms = QaRetriever.strongTermsOf(question)
            val corpus = healthRecordDao.getAllByMember(member.id)
            val hits = if (corpus.isEmpty() || strongTerms.isEmpty()) {
                emptyList()
            } else {
                QaRetriever.index(corpus).search(question, RETRIEVE_TOP_K, restrictTo = strongTerms)
            }
            val summary = if (hits.isEmpty()) {
                buildRecordsSummary(member, recordsByType)
            } else {
                buildRetrievedSummary(member, hits)
            }
            // 引用明细追加在答案末尾（本地引擎与 LLM 两条路径统一），编号与检索结果一一对应
            val citations = if (hits.isEmpty()) "" else buildCitationBlock(hits)

            // LLM 供应商直连：智谱 / OpenAI / Gemini / DeepSeek / Kimi / 通义千问 / Anthropic（失败回退本地）
            if (LlmProviders.isDirect(settingsPrefs.qaProvider) &&
                llmClient.qaConfigured() && recordsByType.isNotEmpty()
            ) {
                try {
                    val llmAnswer = llmClient.askHealthQuestion(
                        memberName = member.name,
                        recordsSummary = summary,
                        question = question
                    )
                    answer = llmAnswer.text + citations
                    thinking = llmAnswer.thinking
                    sources = listOf(
                        "${LlmProviders.nameOf(settingsPrefs.qaProvider)} · 基于已保存的健康记录"
                    )
                } catch (e: Exception) {
                    llmFailed = true
                    Log.w(
                        TAG,
                        "LLM(" + LlmProviders.nameOf(settingsPrefs.qaProvider) + ") 调用失败，已回退本地规则引擎",
                        e
                    )
                }
            } // 本地模式 LOCAL 不走远程

            // 本地规则引擎回退（本地模式 / 远程失败 / 无记录）
            if (answer == null) {
                val (text, src) = localQaEngine.answer(member.name, recordsByType, question)
                answer = text + citations
                sources = if (llmFailed) {
                    // 明确标注：回答并非来自 AI，避免用户误以为读到的是大模型结论
                    src + "本地规则引擎（AI 服务暂不可用，以下为离线兜底回答）"
                } else {
                    src
                }
            }

            val history = QAHistory(
                id = UUID.randomUUID().toString(),
                memberId = member.id,
                question = question,
                answer = answer,
                timestamp = System.currentTimeMillis(),
                sources = sources.joinToString("\n").ifBlank { null },
                thinking = thinking?.takeIf { it.isNotBlank() }
            )
            qaHistoryDao.insert(history)
            history
        }

    /**
     * 把健康记录整理为 AI 可读的文本摘要：
     * 优先使用每条记录实际保存的单位（报告原始单位，如维生素 D 的 nmol/L），
     * 仅在记录无单位时回退到预设单位；两者不一致时明确提示参考范围不可直接比较，
     * 防止模型把 nmol/L 读数当成 ng/mL 来解读。
     * 受 [SUMMARY_CHAR_BUDGET] 字符预算约束：超预算时停止追加并显式标注省略，
     * 避免 49 项 × 10 条记录一次性超出模型上下文窗口。
     */
    private fun buildRecordsSummary(
        member: FamilyMember,
        recordsByType: Map<String, List<HealthRecord>>
    ): String =
        buildString {
            var remaining = SUMMARY_CHAR_BUDGET
            for ((type, list) in recordsByType) {
                if (remaining <= 0) break
                val def = HealthTypes.def(type)
                val defaultUnit = HealthTypes.unit(type)
                val actualUnit = list.firstOrNull { it.unit.isNotBlank() }
                    ?.unit?.trim()?.takeIf { it.isNotBlank() }
                // 无单位指标（如骨密度 T 值）与字典外类型（defaultUnit 为空串）不算单位不一致
                val unitMismatch = actualUnit != null && defaultUnit.isNotBlank() && actualUnit != defaultUnit

                // 参考范围按性别取：血红蛋白 / 肌酐 / 尿酸等男女不同，用合并区间会误导模型与标注
                val ref = def?.rangeFor(member.gender)
                val header =
                    "【${HealthTypes.label(type)}】（单位：${actualUnit ?: defaultUnit}，参考范围：${ref?.text ?: HealthTypes.range(type)}）"
                if (!appendWithinBudget(header, remaining)) break
                remaining -= header.length + 1

                if (unitMismatch) {
                    val note =
                        "  注意：记录单位为「$actualUnit」，参考范围基于「$defaultUnit」，两者单位不同，数值不可直接比较。"
                    if (!appendWithinBudget(note, remaining)) break
                    remaining -= note.length + 1
                }

                for (r in list.take(10)) {
                    // 单位与参考范围不一致时跳过偏高/偏低标注，避免跨单位误判；
                    // 参考区间按成员性别取，标注口径与异常检测保持一致
                    val flag = if (unitMismatch) "" else {
                        val v = r.numericValue
                        when {
                            v == null -> ""
                            ref?.high != null && v > ref.high -> "（偏高）"
                            ref?.low != null && v < ref.low -> "（偏低）"
                            else -> ""
                        }
                    }
                    val unitText = r.unit.trim().takeIf { it.isNotBlank() }?.let { " $it" } ?: ""
                    val line = "  ${DateUtils.formatDate(r.recordDate)}：${r.value}$unitText$flag"
                    if (line.length + 1 > remaining) {
                        appendLine("  …其余记录因上下文预算已省略")
                        remaining = 0
                        break
                    }
                    appendLine(line)
                    remaining -= line.length + 1
                }
            }
        }

    /**
     * A1 真实检索路径：把 BM25 召回的 Top-K 记录整理为带引用编号（[n]）的上下文。
     * 与 [buildRecordsSummary] 的差异：只包含与问题相关的记录，且每条都有稳定编号，
     * 模型可以在回答里引用编号，答案末尾的引用明细（[buildCitationBlock]）与之对应。
     */
    private fun buildRetrievedSummary(
        member: FamilyMember,
        hits: List<QaRetriever.Scored>
    ): String = buildString {
        var remaining = SUMMARY_CHAR_BUDGET
        appendLine("以下是与问题相关度最高的 ${hits.size} 条健康记录（成员：${member.name}），行首 [n] 为引用编号：")
        for (hit in hits) {
            if (remaining <= 0) break
            val r = hit.record
            val label = HealthTypes.label(r.type)
            val ref = HealthTypes.def(r.type)?.rangeFor(member.gender)
            val unitText = r.unit.trim().ifBlank { HealthTypes.unit(r.type) }
            val line = buildString {
                append("[${hit.rank}] 【$label】${DateUtils.formatDate(r.recordDate)}：${r.value}")
                if (unitText.isNotBlank()) append(" $unitText")
                ref?.let { append("（参考 ${it.text}）") }
                r.notes?.trim()?.takeIf { it.isNotBlank() }?.let { append("；备注：$it") }
            }
            if (line.length + 1 > remaining) {
                appendLine("…其余记录因上下文预算已省略")
                break
            }
            appendLine(line)
            remaining -= line.length + 1
        }
    }

    /**
     * 答案末尾的引用明细：本地引擎与 LLM 两条路径统一追加，
     * 满足「答案末尾列出引用的记录明细；换一个问题召回的记录集合可解释」的验收标准。
     */
    private fun buildCitationBlock(hits: List<QaRetriever.Scored>): String = buildString {
        appendLine()
        appendLine("———")
        appendLine("📎 引用记录（按问题相关度从已保存的记录中检索）：")
        for (hit in hits) {
            val r = hit.record
            val unitText = r.unit.trim().ifBlank { HealthTypes.unit(r.type) }
            append("[${hit.rank}] ${HealthTypes.label(r.type)} · ${DateUtils.formatDate(r.recordDate)} · ${r.value}")
            if (unitText.isNotBlank()) append(" $unitText")
            appendLine()
        }
    }

    /** 折行追加，并返回是否成功（超预算返回 false） */
    private fun StringBuilder.appendWithinBudget(line: String, remaining: Int): Boolean {
        if (line.length + 1 > remaining) {
            appendLine("…更多数据因上下文预算已省略")
            return false
        }
        appendLine(line)
        return true
    }

    companion object {
        private const val TAG = "QARepositoryImpl"

        /** 每个指标取最近多少条记录参与问答上下文 */
        private const val RECORDS_PER_TYPE = 10

        /** 单次提问拼接给模型的健康记录摘要字符上限 */
        private const val SUMMARY_CHAR_BUDGET = 6_000

        /** BM25 检索的最大召回条数（见 [QaRetriever]） */
        private const val RETRIEVE_TOP_K = QaRetriever.DEFAULT_TOP_K
    }
}

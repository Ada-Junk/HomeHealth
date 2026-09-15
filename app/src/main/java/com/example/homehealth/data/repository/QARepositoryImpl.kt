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
            // 查询本身带每组 Top-N 限制，不会把成员的全部历史读进内存。
            val recordsByType = healthRecordDao
                .getRecentPerTypeByMember(member.id, RECORDS_PER_TYPE)
                .groupBy { it.type }
                .mapNotNull { (type, list) ->
                    if (HealthTypes.def(type) == null) null else type to list.take(RECORDS_PER_TYPE)
                }
                .toMap()

            val summary = buildRecordsSummary(member, recordsByType)

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
                    answer = llmAnswer.text
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
                answer = text
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
                val unitMismatch = actualUnit != null && actualUnit != defaultUnit

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
    }
}

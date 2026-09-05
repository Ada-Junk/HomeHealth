package com.example.homehealth.data.repository

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

            // 汇总成员健康记录（供 AI 上下文与本地引擎共用）
            val recordsByType = HealthTypes.ALL.mapNotNull { type ->
                val recent = healthRecordDao.getRecentRecords(member.id, type, 10)
                if (recent.isEmpty()) null else type to recent
            }.toMap()

            val summary = buildRecordsSummary(recordsByType)

            // LLM 供应商直连：智谱 / OpenAI / Gemini / DeepSeek / Kimi / 通义千问 / Anthropic / 自定义（失败回退本地）
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
                } catch (_: Exception) {
                }
            } // 本地模式 LOCAL 不走远程

            // 本地规则引擎回退（本地模式 / 远程失败 / 无记录）
            if (answer == null) {
                val (text, src) = localQaEngine.answer(member.name, recordsByType, question)
                answer = text
                sources = src
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
     */
    private fun buildRecordsSummary(recordsByType: Map<String, List<HealthRecord>>): String =
        buildString {
            recordsByType.forEach { (type, list) ->
                val def = HealthTypes.def(type)
                val defaultUnit = HealthTypes.unit(type)
                val actualUnit = list.firstOrNull { it.unit.isNotBlank() }
                    ?.unit?.trim()?.takeIf { it.isNotBlank() }

                appendLine("【${HealthTypes.label(type)}】（单位：${actualUnit ?: defaultUnit}，参考范围：${HealthTypes.range(type)}）")
                if (actualUnit != null && actualUnit != defaultUnit) {
                    appendLine("  注意：记录单位为「$actualUnit」，参考范围基于「$defaultUnit」，两者单位不同，数值不可直接比较。")
                }
                list.take(10).forEach { r ->
                    // 单位与参考范围不一致时跳过偏高/偏低标注，避免跨单位误判
                    val flag = if (actualUnit != null && actualUnit != defaultUnit) "" else {
                        def?.let { d ->
                            val v = r.numericValue
                            when {
                                v == null -> ""
                                d.high != null && v > d.high -> "（偏高）"
                                d.low != null && v < d.low -> "（偏低）"
                                else -> ""
                            }
                        } ?: ""
                    }
                    val unitText = r.unit.trim().takeIf { it.isNotBlank() }?.let { " $it" } ?: ""
                    appendLine("  ${DateUtils.formatDate(r.recordDate)}：${r.value}$unitText$flag")
                }
            }
        }
}

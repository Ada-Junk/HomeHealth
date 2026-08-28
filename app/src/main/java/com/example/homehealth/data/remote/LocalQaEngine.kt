package com.example.homehealth.data.remote

import com.example.homehealth.data.local.entity.HealthRecord
import com.example.homehealth.util.DateUtils
import com.example.homehealth.util.HealthTypes
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

/**
 * 本地问答引擎（离线回退方案）：
 * 基于成员健康记录的规则式问答，远程 RAG 服务不可用时使用。
 */
@Singleton
class LocalQaEngine @Inject constructor() {

    /** @return 回答文本 与 引用来源列表 */
    fun answer(
        memberName: String,
        recordsByType: Map<String, List<HealthRecord>>,
        question: String
    ): Pair<String, List<String>> {
        if (recordsByType.isEmpty()) {
            return "暂时没有找到任何健康记录。请先在「档案」中上传体检报告，之后我可以为您分析各项指标。" to emptyList()
        }

        val askedTypes = detectTypes(question)
        val targetTypes = if (askedTypes.isEmpty()) {
            recordsByType.keys.sortedBy { HealthTypes.label(it) }
        } else {
            askedTypes.filter { recordsByType.containsKey(it) }
        }

        if (targetTypes.isEmpty()) {
            val labelNames = askedTypes.joinToString("、") { HealthTypes.label(it) }
            return "目前还没有 $labelNames 相关的记录。上传包含该项指标的体检报告后，我可以为您分析。" to emptyList()
        }

        val sb = StringBuilder()
        sb.append("根据已保存的记录，为${memberName}整理如下：\n\n")
        val sources = mutableListOf<String>()

        targetTypes.forEach { type ->
            val list = recordsByType.getValue(type) // 按时间倒序
            val label = HealthTypes.label(type)
            // 优先使用记录实际保存的单位（报告原始单位），无则回退预设单位
            val unit = list.firstOrNull { it.unit.isNotBlank() }?.unit?.trim()
                ?: HealthTypes.unit(type)
            sb.append("【$label】共 ${list.size} 条记录\n")
            list.take(3).forEach { r ->
                val rUnit = r.unit.trim().ifBlank { unit }
                sb.append("  • ${DateUtils.formatDate(r.recordDate)}：${r.value} $rUnit\n")
                sources.add("$label 记录（${DateUtils.formatDate(r.recordDate)}）")
            }
            val nums = list.mapNotNull { it.numericValue }
            if (nums.size >= 2) {
                val avg = nums.average()
                val diff = nums[0] - nums[1]
                val dir = if (diff >= 0) "上升" else "下降"
                sb.append("  平均约 ${"%.1f".format(avg)} $unit，最新值较上次$dir ${"%.1f".format(abs(diff))} $unit\n")
            }
            sb.append("\n")
        }

        sb.append("⚠️ 以上内容由本地数据分析生成，仅供参考，不构成医疗建议。如有不适请及时就医。")
        return sb.toString() to sources.distinct()
    }

    /** 根据问题关键词匹配涉及的指标类型 */
    private fun detectTypes(question: String): List<String> {
        val types = mutableSetOf<String>()
        if (question.contains("血压")) types.add(HealthTypes.BLOOD_PRESSURE)
        if (question.contains("血糖")) types.add(HealthTypes.BLOOD_GLUCOSE)
        if (question.contains("胆固醇") || question.contains("血脂")) {
            types.add(HealthTypes.TOTAL_CHOLESTEROL)
            types.add(HealthTypes.TRIGLYCERIDES)
        }
        if (question.contains("甘油三酯")) types.add(HealthTypes.TRIGLYCERIDES)
        if (question.contains("体重") || question.contains("胖") || question.contains("瘦")) types.add(HealthTypes.WEIGHT)
        if (question.contains("心率") || question.contains("脉搏")) types.add(HealthTypes.HEART_RATE)
        return types.toList()
    }
}

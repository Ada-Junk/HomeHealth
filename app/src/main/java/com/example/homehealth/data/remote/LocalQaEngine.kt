package com.example.homehealth.data.remote

import com.example.homehealth.data.local.entity.HealthRecord
import com.example.homehealth.util.DateUtils
import com.example.homehealth.util.HealthTypes
import com.example.homehealth.util.SchemaNormalizer
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

/**
 * 本地问答引擎（离线回退方案）：
 * 基于成员健康记录的规则式问答，在未配置供应商、远程调用失败或本地模式下使用。
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

    /**
     * 根据问题匹配涉及的指标类型。
     *
     * 覆盖全部 49 项指标，而不是原先硬编码的 6 个分支。三个来源：
     * 1. 指标字典的中文名（`HealthTypes.DEFS.label`）；
     * 2. `SchemaNormalizer.typeAliases` —— 别名表本就是为"报告里的各种写法"准备的，
     *    口语提问同样适用，复用它可避免别名出现第二份副本；
     * 3. [GROUP_KEYWORDS] —— 「血脂」「肝功能」这类成组说法，命中后展开为多项。
     *
     * 匹配规则：中文做去空格子串匹配；**纯拉丁别名按单词边界匹配**，
     * 否则 `ua`、`cr`、`hb` 这类两字母缩写会命中无关文本。
     */
    private fun detectTypes(question: String): List<String> {
        val raw = question.lowercase()
        val tight = raw.replace(" ", "")
        val types = linkedSetOf<String>()

        GROUP_KEYWORDS.forEach { (keyword, targets) ->
            if (tight.contains(keyword)) types.addAll(targets)
        }
        HealthTypes.DEFS.forEach { def ->
            val label = def.label.lowercase().replace(" ", "")
            if (label.isNotEmpty() && tight.contains(label)) types.add(def.type)
        }
        aliasMatchers.forEach { m ->
            val hit = m.wordRegex?.containsMatchIn(raw) ?: tight.contains(m.tight)
            if (hit) types.add(m.type)
        }
        return types.toList()
    }

    private class AliasMatcher(val type: String, val tight: String, val wordRegex: Regex?)

    /** 别名匹配器，首次使用时构建一次（106 条别名，不必每次提问都编译正则） */
    private val aliasMatchers: List<AliasMatcher> by lazy {
        SchemaNormalizer.typeAliases.map { (alias, type) ->
            val a = alias.lowercase()
            val isLatin = a.all { it.code < 128 }
            AliasMatcher(
                type = type,
                tight = a.replace(" ", ""),
                wordRegex = if (isLatin) Regex("\\b" + Regex.escape(a) + "\\b") else null
            )
        }
    }

    private companion object {
        /**
         * 口语化的成组说法：这些词不是任何单一指标的别名，命中后应展开为多项。
         * 保留原实现里「血脂」「胖/瘦」等口语习惯，同时补齐常见体检套餐的分组。
         */
        val GROUP_KEYWORDS: Map<String, List<String>> = mapOf(
            "血脂" to listOf(
                HealthTypes.TOTAL_CHOLESTEROL, HealthTypes.TRIGLYCERIDES,
                HealthTypes.HDL, HealthTypes.LDL
            ),
            "胆固醇" to listOf(
                HealthTypes.TOTAL_CHOLESTEROL, HealthTypes.HDL, HealthTypes.LDL
            ),
            "血常规" to listOf("wbc", "rbc", "hemoglobin", "hematocrit", "platelets"),
            "肝功能" to listOf("alt", "ast", "ggt", "total_bilirubin", "albumin"),
            "肾功能" to listOf("creatinine", "urea_nitrogen", "uric_acid"),
            "甲状腺" to listOf("tsh", "ft3", "ft4"),
            "电解质" to listOf("potassium", "sodium", "chloride", "calcium"),
            "维生素" to listOf(
                "vitamin_a", "vitamin_b1", "vitamin_b6", "vitamin_b12",
                "vitamin_c", "vitamin_d", "vitamin_e", "folate"
            ),
            "胖" to listOf(HealthTypes.WEIGHT, "bmi", "body_fat"),
            "瘦" to listOf(HealthTypes.WEIGHT, "bmi")
        )
    }
}

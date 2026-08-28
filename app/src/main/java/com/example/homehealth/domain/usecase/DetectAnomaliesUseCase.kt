package com.example.homehealth.domain.usecase

import com.example.homehealth.data.local.entity.Alert
import com.example.homehealth.data.local.entity.AlertSeverity
import com.example.homehealth.data.local.entity.HealthRecord
import com.example.homehealth.domain.repository.AlertRepository
import com.example.homehealth.domain.repository.FamilyRepository
import com.example.homehealth.domain.repository.HealthRecordRepository
import com.example.homehealth.util.HealthTypes
import java.util.UUID
import javax.inject.Inject
import kotlin.math.abs

/**
 * 异常检测用例：
 * 1. 越界规则（血压 / 血糖 / 血脂 / 心率）
 * 2. 趋势规则（最近三次持续上升）
 * 相同 type+title 的未读预警不重复生成。
 */
class DetectAnomaliesUseCase @Inject constructor(
    private val familyRepository: FamilyRepository,
    private val healthRecordRepository: HealthRecordRepository,
    private val alertRepository: AlertRepository
) {

    /** 对单个成员执行检测，返回新生成的预警数量 */
    suspend operator fun invoke(memberId: String): Int {
        familyRepository.getMember(memberId) ?: return 0
        val unreadKeys = alertRepository.getUnreadByMember(memberId)
            .map { it.type to it.title }
            .toSet()

        var created = 0

        suspend fun createAlert(type: String, title: String, description: String, severity: AlertSeverity) {
            if (type to title in unreadKeys) return
            alertRepository.createAlert(
                Alert(
                    id = UUID.randomUUID().toString(),
                    memberId = memberId,
                    type = type,
                    title = title,
                    description = description,
                    severity = severity,
                    createdDate = System.currentTimeMillis(),
                    isRead = false
                )
            )
            created++
        }

        for (metric in HealthTypes.ALL) {
            val recent = healthRecordRepository.getRecentRecords(memberId, metric, 10)
            if (recent.isEmpty()) continue

            // ---- 越界规则 ----
            checkOutOfRange(metric, recent.first(), ::createAlert)

            // ---- 趋势规则：最近 3 条（升序） ----
            val asc = recent.take(3).reversed()
            if (asc.size == 3) checkTrend(metric, asc, ::createAlert)
        }
        return created
    }

    /** 对所有成员执行检测 */
    suspend fun invokeAll(): Int {
        var total = 0
        for (member in familyRepository.getMembers()) {
            total += invoke(member.id)
        }
        return total
    }

    private suspend fun checkOutOfRange(
        metric: String,
        record: HealthRecord,
        create: suspend (String, String, String, AlertSeverity) -> Unit
    ) {
        val dateStr = com.example.homehealth.util.DateUtils.formatDate(record.recordDate)

        // 血压特殊：收缩压/舒张压分别判断
        if (metric == HealthTypes.BLOOD_PRESSURE) {
            val parts = record.value.split("/")
            val sys = parts.getOrNull(0)?.trim()?.toDoubleOrNull() ?: record.numericValue
            val dia = parts.getOrNull(1)?.trim()?.toDoubleOrNull()
            if (sys != null && (sys >= 140 || (dia ?: 0.0) >= 90)) {
                create(
                    "out_of_range", "血压偏高",
                    "收缩压 ${parts.getOrNull(0)?.trim() ?: sys} / 舒张压 ${parts.getOrNull(1)?.trim() ?: dia ?: "-"} mmHg（$dateStr）超出正常范围（<140/90 mmHg），建议关注并咨询医生",
                    AlertSeverity.HIGH
                )
            } else if (sys != null && sys < 90) {
                create(
                    "out_of_range", "血压偏低",
                    "收缩压 ${sys} mmHg（$dateStr）低于 90 mmHg，如伴有头晕乏力请及时就医",
                    AlertSeverity.MEDIUM
                )
            }
            return
        }

        // 通用规则：按指标定义的参考上下限判断（覆盖全部 49 项指标）
        val def = HealthTypes.def(metric) ?: return
        val v = record.numericValue ?: return
        val label = def.label
        val unit = def.unit

        if (def.high != null && v > def.high) {
            val overRatio = (v - def.high) / def.high
            val severity = if (overRatio >= 0.5) AlertSeverity.HIGH else AlertSeverity.MEDIUM
            create(
                "out_of_range", "$label 偏高",
                "$label ${v} $unit（$dateStr）超出参考范围（${def.rangeText}）" +
                    if (severity == AlertSeverity.HIGH) "，明显偏高，建议尽快就医复查" else "，建议关注并复查",
                severity
            )
        } else if (def.low != null && v < def.low) {
            create(
                "out_of_range", "$label 偏低",
                "$label ${v} $unit（$dateStr）低于参考范围（${def.rangeText}），建议复查，如伴有不适请就医",
                AlertSeverity.MEDIUM
            )
        }
    }

    private suspend fun checkTrend(
        metric: String,
        asc: List<HealthRecord>,
        create: suspend (String, String, String, AlertSeverity) -> Unit
    ) {
        val threshold = HealthTypes.TREND_THRESHOLDS[metric] ?: return
        val nums = asc.mapNotNull { it.numericValue }
        if (nums.size < 3) return
        val rising = nums[0] < nums[1] && nums[1] < nums[2]
        val falling = nums[0] > nums[1] && nums[1] > nums[2]
        val totalDelta = nums[2] - nums[0]
        val label = HealthTypes.label(metric)
        val unit = HealthTypes.unit(metric)
        val from = DateUtils_format(asc.first().recordDate)
        val to = DateUtils_format(asc.last().recordDate)
        if (rising && totalDelta >= threshold) {
            create(
                "trend_anomaly", "$label 呈持续上升趋势",
                "$from 至 $to 期间，$label 从 ${nums[0]} 升至 ${nums[2]} $unit（累计上升 ${"%.1f".format(totalDelta)} $unit），请关注变化趋势",
                AlertSeverity.MEDIUM
            )
        } else if (falling && abs(totalDelta) >= threshold) {
            create(
                "trend_anomaly", "$label 呈持续下降趋势",
                "$from 至 $to 期间，$label 从 ${nums[0]} 降至 ${nums[2]} $unit（累计下降 ${"%.1f".format(abs(totalDelta))} $unit），请关注变化趋势",
                AlertSeverity.MEDIUM
            )
        }
    }

    private fun DateUtils_format(ts: Long): String =
        com.example.homehealth.util.DateUtils.formatDate(ts)
}

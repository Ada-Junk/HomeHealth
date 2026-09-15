package com.example.homehealth.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 健康预警。
 *
 * **结构 + 文本双写**的设计说明：
 * 早期版本只存 [title] / [description] 两段**成品中文文本**，带来两个问题：
 * 1. 界面切到英文时，告警正文仍然是中文（UI 是双语的，告警不是）；
 * 2. 文案在写入时固化，参考范围或阈值调整后**历史告警无法按新口径重算**。
 *
 * 因此新增一组结构化字段（[metricType] / [direction] / [valueText] / [unitText] / [refText]）：
 * 新预警写入结构化字段，标题与正文由展示层按当前语言生成（见 `util/AlertText`）。
 *
 * 两个文本字段仍然保留并继续写入，原因有二：
 * - **历史数据无法反推**：老预警只有文本、没有结构化字段，删列就等于丢内容；
 * - **兜底**：万一结构化字段缺失（老数据，或将来新增的 kind 未被渲染器覆盖），
 *   界面仍能显示内容，而不是空白卡片。
 */
@Entity(tableName = "alerts", indices = [Index("memberId")])
data class Alert(
    @PrimaryKey val id: String,
    val memberId: String,
    /** 预警种类：out_of_range / trend_anomaly / baseline_shift */
    val type: String,
    /** 成品标题（中文）。新预警仍写入，作为结构化渲染的兜底 */
    val title: String,
    /** 成品正文（中文）。同上 */
    val description: String,
    val severity: AlertSeverity, // LOW, MEDIUM, HIGH
    val createdDate: Long,
    val isRead: Boolean = false,
    /** 指标类型（`HealthTypes` 的 key），用于按当前语言取指标名；老数据为 null */
    val metricType: String? = null,
    /** 方向：HIGH / LOW / RISING / FALLING / BASELINE_UP / BASELINE_DOWN */
    val direction: String? = null,
    /** 触发时的读数文本（如 "160"、"<0.1"、"120/80"） */
    val valueText: String? = null,
    /** 读数单位（如 "g/L"）；无单位指标为空串 */
    val unitText: String? = null,
    /** 该指标在**当时性别口径**下的参考范围文本，用于回溯"当初为何报警" */
    val refText: String? = null,
    /** 趋势 / 基线告警的时间跨度描述（如 "近 60 天"、"同日"）；越界类为 null */
    val spanText: String? = null,
    /** 基线告警的个人历史中位值文本；其余类型为 null */
    val baselineText: String? = null
) {
    companion object {
        const val DIRECTION_HIGH = "HIGH"
        const val DIRECTION_LOW = "LOW"
        const val DIRECTION_RISING = "RISING"
        const val DIRECTION_FALLING = "FALLING"
        const val DIRECTION_BASELINE_UP = "BASELINE_UP"
        const val DIRECTION_BASELINE_DOWN = "BASELINE_DOWN"
    }
}

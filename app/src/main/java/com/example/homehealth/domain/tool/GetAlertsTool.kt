package com.example.homehealth.domain.tool

import com.example.homehealth.data.local.entity.Alert
import com.example.homehealth.data.local.entity.AlertSeverity
import com.example.homehealth.domain.repository.AlertRepository
import com.example.homehealth.util.DateUtils
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 工具 ③：读取已有的异常告警（**只读**）。
 *
 * ⚠️ **为什么不是"重新检测"**：`DetectAnomaliesUseCase.invoke()` 会写库
 * （`createAlert` + 7 天去重 + 严重度穿透）。把它当工具用意味着：
 * 用户只是问了一句「我有什么问题吗」，系统就凭空生成一批告警进预警页、可能触发通知 ——
 * 这是"用查询的姿势触发了写操作"，语义上就是错的。
 * 检测由每日后台任务与录入流程负责，问答只需要**读**已经判定的结果。
 *
 * 同样重要的是：异常结论必须来自本工具，不能由模型自行判断 ——
 * 参考范围取值、性别分层、180 天趋势窗、区间型比较符这些规则都留在确定性代码里。
 */
@Singleton
class GetAlertsTool @Inject constructor(
    private val alertRepository: AlertRepository
) : HealthTool {

    override val name = NAME

    override val description =
        "读取系统已经判定的异常告警（越界 / 趋势 / 个体基线三条规则）。" +
            "凡是回答「有没有异常」「哪项不正常」「要不要紧」之前，都应先调用本工具 —— " +
            "结论必须来自它，不要自行判断某项指标算不算异常。"

    override val parametersJsonSchema = """
        {
          "type": "object",
          "properties": {
            "since_days": {
              "type": "integer",
              "description": "回溯天数，默认 90，上限 730"
            }
          }
        }
    """.trimIndent()

    override suspend fun execute(context: ToolContext, argsJson: String): ToolResult {
        val days = (ToolArgs.int(ToolArgs.parse(argsJson), "since_days") ?: DEFAULT_DAYS)
            .coerceIn(1, MAX_DAYS)
        val since = System.currentTimeMillis() - days * DAY_MS

        return runCatching {
            val alerts = alertRepository.getByMemberSince(context.member.id, since)
            if (alerts.isEmpty()) {
                return@runCatching ToolResult.ok(
                    "最近 $days 天内系统没有判定出异常告警。" +
                        "这不等于所有指标都正常 —— 没有对应记录的项目无法判断，可以先用 " +
                        "${SearchRecordsTool.NAME} 看看有哪些数据。"
                )
            }
            ToolResult.ok(render(alerts, days))
        }.getOrElse { e ->
            ToolResult.fail("读取告警失败：${e.message ?: e.javaClass.simpleName}")
        }
    }

    /**
     * 只用结构化字段渲染，不用 `description` 成品文案 ——
     * 后者是写入时固化的中文，问答侧需要的是可直接引用的「指标 / 数值 / 参考范围」三要素。
     */
    private fun render(alerts: List<Alert>, days: Int): String = buildString {
        appendLine("最近 $days 天内系统判定的异常告警共 ${alerts.size} 条：")
        alerts.forEachIndexed { index, alert ->
            append("[${index + 1}] ${alert.title}")
            alert.valueText?.takeIf { it.isNotBlank() }?.let { value ->
                append(" · $value")
                alert.unitText?.takeIf { it.isNotBlank() }?.let { append(" $it") }
            }
            alert.refText?.takeIf { it.isNotBlank() }?.let { append("（参考 $it）") }
            alert.spanText?.takeIf { it.isNotBlank() }?.let { append("，时间跨度 $it") }
            alert.baselineText?.takeIf { it.isNotBlank() }?.let { append("，个人基线 $it") }
            append(" · ${DateUtils.formatDate(alert.createdDate)}")
            append(" · 严重度${severityText(alert.severity)}")
            appendLine()
        }
    }

    private fun severityText(severity: AlertSeverity): String = when (severity) {
        AlertSeverity.HIGH -> "高"
        AlertSeverity.MEDIUM -> "中"
        AlertSeverity.LOW -> "低"
    }

    companion object {
        const val NAME = "get_alerts"
        private const val DEFAULT_DAYS = 90
        private const val MAX_DAYS = 730
        private const val DAY_MS = 24L * 60 * 60 * 1000
    }
}

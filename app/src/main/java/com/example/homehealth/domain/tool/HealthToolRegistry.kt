package com.example.homehealth.domain.tool

import javax.inject.Inject
import javax.inject.Singleton

/**
 * 工具注册表：工具的唯一入口。
 *
 * 职责刻意做得很少 —— 按名查找、按上下文裁剪、导出给模型的声明。
 * 执行策略（轮数上限、连续失败禁用、结果截断）不在这里，而在 `ReActAgent` 里：
 * 注册表只管"有哪些工具"，循环管"怎么用工具"，两者混在一起会让策略无处测试。
 */
@Singleton
class HealthToolRegistry @Inject constructor(
    private val searchRecords: SearchRecordsTool,
    private val getReferenceRange: GetReferenceRangeTool,
    private val getAlerts: GetAlertsTool,
    private val readReportImage: ReadReportImageTool
) : ToolProvider {

    /** 全部工具（顺序固定：测试与日志比对需要一个稳定顺序） */
    override val all: List<HealthTool> = listOf(
        searchRecords,
        getReferenceRange,
        getAlerts,
        readReportImage
    )

    /**
     * 本轮实际可用的工具。
     *
     * 没有附图时不暴露读图工具：留着它只会让模型多一次必然失败的调用，
     * 既烧 token 又浪费一轮轮数预算。
     */
    override fun availableFor(context: ToolContext): List<HealthTool> =
        if (context.imageBase64.isNullOrBlank()) {
            all.filterNot { it.name == ReadReportImageTool.NAME }
        } else {
            all
        }

    override fun byName(name: String): HealthTool? = all.firstOrNull { it.name == name }
}

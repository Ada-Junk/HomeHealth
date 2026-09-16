package com.example.homehealth.domain.tool

import com.example.homehealth.util.HealthTypes
import com.example.homehealth.util.SchemaNormalizer
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 工具 ②：查参考范围。
 *
 * 检索结果里其实已经带了参考范围，那为什么还要单独一个工具？
 * 因为**没有任何记录的问题**只能靠它回答：「胆固醇正常范围是多少」在一个还没录入过
 * 血脂的新成员身上，检索路径一条都召回不了。它是四个工具里最便宜的一个（纯查表、零 IO）。
 */
@Singleton
class GetReferenceRangeTool @Inject constructor() : HealthTool {

    override val name = NAME

    override val description =
        "查询某指标的正常参考范围、计量单位，以及「偏高还是偏低更需要注意」。" +
            "回答「这个值算不算高」「正常范围是多少」之前应先调用；" +
            "下结论时必须以本工具返回的范围为准，不要自行假定正常值。"

    override val parametersJsonSchema = """
        {
          "type": "object",
          "properties": {
            "metric_type": {
              "type": "string",
              "description": "指标类型（英文键，如 ldl、blood_glucose）或其常见中文名（如 LDL、血糖）"
            }
          },
          "required": ["metric_type"]
        }
    """.trimIndent()

    override suspend fun execute(context: ToolContext, argsJson: String): ToolResult {
        val raw = ToolArgs.str(ToolArgs.parse(argsJson), "metric_type")
            ?: return ToolResult.fail("缺少 metric_type。请传入指标类型或其中文名。")

        val type = SchemaNormalizer.normalizeType(raw)
        val def = HealthTypes.def(type)
            ?: return ToolResult.fail(
                "未知指标「$raw」。可以先调用 ${SearchRecordsTool.NAME} 看看该成员录入了哪些指标。"
            )

        // 参考范围按性别取：血红蛋白 / 肌酐 / 尿酸等男女不同，用合并区间会系统性漏报
        val ref = def.rangeFor(context.member.gender)
        val direction = if (HealthTypes.higherIsWorse(type)) "偏高" else "偏低"

        return ToolResult.ok(
            buildString {
                appendLine("${def.label}（$type）")
                appendLine("- 参考范围：${ref.text}（按成员性别取值）")
                appendLine("- 计量单位：${def.unit.ifBlank { "无单位" }}")
                appendLine("- 更需要注意的方向：$direction")
                // 区间型结果（<0.1 / >100）的判断口径与异常检测一致，提前告知模型免得它自己拍脑袋
                append("- 区间型结果（如 <0.1、>100）只有边界值本身已越界才可判定异常")
            }
        )
    }

    companion object {
        const val NAME = "get_reference_range"
    }
}

package com.example.homehealth.util

/**
 * 指标标准化归一化（Schema Normalization）：
 * 1. 指标名归一化：LLM / 后端返回的别名（中文名、英文缩写、变体拼写）映射到 HealthTypes 标准类型；
 * 2. 单位写法归一化：统一单位拼写（mmol/l → mmol/L、umol/L → μmol/L 等）；
 * 3. 单位换算：报告原始单位（如维生素 D 的 nmol/L）按换算系数转为标准单位（ng/mL），
 *    数值与单位同步换算；没有换算系数的单位保持原样（绝不猜测）。
 *
 * 归一化在上传解析结果展示前执行，用户核对的是标准化后的数值，
 * 入库记录与标准字典（HealthTypes 参考范围、异常检测、趋势分析）单位一致。
 */
object SchemaNormalizer {

    /** 归一化结果 */
    data class Result(
        val type: String,          // 标准指标类型
        val value: String,         // 数值文本（换算后）
        val numericValue: Double?, // 主数值（换算后）
        val unit: String,          // 标准单位（无标准单位时保留归一化写法）
        val note: String?          // 换算说明（如「nmol/L 已换算为 ng/mL」），未换算为 null
    )

    // ---------- 1. 指标别名 → 标准类型（英文键统一小写） ----------

    private val TYPE_ALIASES: Map<String, String> = buildMap {
        // 血糖代谢
        put("血糖", HealthTypes.BLOOD_GLUCOSE)
        put("空腹血糖", HealthTypes.BLOOD_GLUCOSE)
        put("fasting_glucose", HealthTypes.BLOOD_GLUCOSE)
        put("fasting_blood_glucose", HealthTypes.BLOOD_GLUCOSE)
        put("fpg", HealthTypes.BLOOD_GLUCOSE)
        put("fbg", HealthTypes.BLOOD_GLUCOSE)
        put("glu", HealthTypes.BLOOD_GLUCOSE)
        put("glucose", HealthTypes.BLOOD_GLUCOSE)
        put("餐后血糖", "postprandial_glucose")
        put("餐后2小时血糖", "postprandial_glucose")
        put("postprandial_blood_glucose", "postprandial_glucose")
        put("ogtt_2h", "postprandial_glucose")
        put("糖化血红蛋白", "hba1c")
        put("hba1c", "hba1c")
        // 血脂
        put("总胆固醇", HealthTypes.TOTAL_CHOLESTEROL)
        put("tc", HealthTypes.TOTAL_CHOLESTEROL)
        put("chol", HealthTypes.TOTAL_CHOLESTEROL)
        put("cholesterol", HealthTypes.TOTAL_CHOLESTEROL)
        put("甘油三酯", HealthTypes.TRIGLYCERIDES)
        put("tg", HealthTypes.TRIGLYCERIDES)
        put("triglyceride", HealthTypes.TRIGLYCERIDES)
        put("高密度脂蛋白", HealthTypes.HDL)
        put("高密度脂蛋白胆固醇", HealthTypes.HDL)
        put("hdl", HealthTypes.HDL)
        put("hdl-c", HealthTypes.HDL)
        put("hdlc", HealthTypes.HDL)
        put("低密度脂蛋白", HealthTypes.LDL)
        put("低密度脂蛋白胆固醇", HealthTypes.LDL)
        put("ldl", HealthTypes.LDL)
        put("ldl-c", HealthTypes.LDL)
        put("ldlc", HealthTypes.LDL)
        // 基础体征
        put("血压", HealthTypes.BLOOD_PRESSURE)
        put("bp", HealthTypes.BLOOD_PRESSURE)
        put("心率", HealthTypes.HEART_RATE)
        put("脉搏", HealthTypes.HEART_RATE)
        put("hr", HealthTypes.HEART_RATE)
        put("pulse", HealthTypes.HEART_RATE)
        put("体重", HealthTypes.WEIGHT)
        put("身高", "height")
        // 血常规
        put("白细胞", "wbc")
        put("白细胞计数", "wbc")
        put("wbc", "wbc")
        put("红细胞", "rbc")
        put("红细胞计数", "rbc")
        put("rbc", "rbc")
        put("血红蛋白", "hemoglobin")
        put("hb", "hemoglobin")
        put("hgb", "hemoglobin")
        put("红细胞压积", "hematocrit")
        put("hct", "hematocrit")
        put("血小板", "platelets")
        put("血小板计数", "platelets")
        put("plt", "platelets")
        // 肝功能
        put("谷丙转氨酶", "alt")
        put("丙氨酸氨基转移酶", "alt")
        put("sgpt", "alt")
        put("谷草转氨酶", "ast")
        put("天门冬氨酸氨基转移酶", "ast")
        put("sgot", "ast")
        put("总胆红素", "total_bilirubin")
        put("tbil", "total_bilirubin")
        put("白蛋白", "albumin")
        put("alb", "albumin")
        // 肾功能
        put("肌酐", "creatinine")
        put("cr", "creatinine")
        put("crea", "creatinine")
        put("尿素氮", "urea_nitrogen")
        put("尿素", "urea_nitrogen")
        put("bun", "urea_nitrogen")
        put("尿酸", "uric_acid")
        put("ua", "uric_acid")
        // 甲状腺
        put("促甲状腺激素", "tsh")
        put("游离三碘甲状腺原氨酸", "ft3")
        put("游离t3", "ft3")
        put("游离甲状腺素", "ft4")
        put("游离t4", "ft4")
        // 维生素与微量元素
        put("维生素d", "vitamin_d")
        put("维d", "vitamin_d")
        put("25-羟维生素d", "vitamin_d")
        put("25羟基维生素d", "vitamin_d")
        put("25羟维生素d", "vitamin_d")
        put("25(oh)d", "vitamin_d")
        put("25-oh-d", "vitamin_d")
        put("25-ohd", "vitamin_d")
        put("vitamin d", "vitamin_d")
        put("维生素a", "vitamin_a")
        put("维生素b1", "vitamin_b1")
        put("硫胺素", "vitamin_b1")
        put("维生素b6", "vitamin_b6")
        put("维生素b12", "vitamin_b12")
        put("钴胺素", "vitamin_b12")
        put("叶酸", "folate")
        put("维生素c", "vitamin_c")
        put("维生素e", "vitamin_e")
        put("血清铁", "serum_iron")
        // 电解质
        put("血钾", "potassium")
        put("钾", "potassium")
        put("k+", "potassium")
        put("血钠", "sodium")
        put("钠", "sodium")
        put("na+", "sodium")
        put("血氯", "chloride")
        put("氯", "chloride")
        put("cl-", "chloride")
        put("血钙", "calcium")
        put("钙", "calcium")
    }

    /** 指标名 → 标准类型（先精确匹配，再小写匹配） */
    fun normalizeType(type: String): String {
        val t = type.trim()
        return TYPE_ALIASES[t] ?: TYPE_ALIASES[t.lowercase()] ?: t
    }

    // ---------- 2. 单位写法归一化 ----------

    /** 常见单位变体 → 规范写法（键为小写，μ 统一） */
    private val UNIT_CANON: Map<String, String> = mapOf(
        "mmol/l" to "mmol/L",
        "umol/l" to "μmol/L",
        "μmol/l" to "μmol/L",
        "nmol/l" to "nmol/L",
        "pmol/l" to "pmol/L",
        "ng/ml" to "ng/mL",
        "pg/ml" to "pg/mL",
        "μg/ml" to "μg/mL",
        "mg/dl" to "mg/dL",
        "μg/dl" to "μg/dL",
        "ng/dl" to "ng/dL",
        "mg/l" to "mg/L",
        "g/l" to "g/L",
        "g/dl" to "g/dL",
        "u/l" to "U/L",
        "miu/l" to "mIU/L",
        "uiu/ml" to "μIU/mL",
        "meq/l" to "mEq/L",
        "mmhg" to "mmHg",
        "°c" to "℃",
        "kg/m2" to "kg/m²",
        "10^9/l" to "×10⁹/L",
        "10*9/l" to "×10⁹/L",
        "10^12/l" to "×10¹²/L"
    )

    /** 单位写法归一化：去除空格、统一 μ、映射规范写法 */
    fun normalizeUnit(unit: String): String {
        val t = unit.trim().replace("µ", "μ").replace(" ", "")
        return UNIT_CANON[t.lowercase()] ?: t
    }

    // ---------- 3. 跨单位换算系数（源单位 × 系数 = 标准单位） ----------

    private val UNIT_FACTORS: Map<String, Map<String, Double>> = mapOf(
        // 血糖：mg/dL → mmol/L
        HealthTypes.BLOOD_GLUCOSE to mapOf("mg/dL" to 0.0555),
        "postprandial_glucose" to mapOf("mg/dL" to 0.0555),
        // 血脂：mg/dL → mmol/L
        HealthTypes.TOTAL_CHOLESTEROL to mapOf("mg/dL" to 0.0259),
        HealthTypes.HDL to mapOf("mg/dL" to 0.0259),
        HealthTypes.LDL to mapOf("mg/dL" to 0.0259),
        HealthTypes.TRIGLYCERIDES to mapOf("mg/dL" to 0.0113),
        // 蛋白类：g/dL → g/L
        "hemoglobin" to mapOf("g/dL" to 10.0),
        "albumin" to mapOf("g/dL" to 10.0),
        // 肾功能：mg/dL → μmol/L（尿素氮为 mmol/L）
        "creatinine" to mapOf("mg/dL" to 88.4),
        "uric_acid" to mapOf("mg/dL" to 59.48),
        "urea_nitrogen" to mapOf("mg/dL" to 0.357),
        // 肝功能：mg/dL → μmol/L
        "total_bilirubin" to mapOf("mg/dL" to 17.1),
        // 电解质：mg/dL / mEq/L → mmol/L
        "calcium" to mapOf("mg/dL" to 0.2495, "mEq/L" to 0.5),
        // 甲状腺：ng/dL → pmol/L（FT4）、pg/mL → pmol/L（FT3）
        "ft4" to mapOf("ng/dL" to 12.87),
        "ft3" to mapOf("pg/mL" to 1.536),
        // 维生素
        "vitamin_d" to mapOf("nmol/L" to 0.4),          // nmol/L → ng/mL
        "vitamin_b12" to mapOf("pmol/L" to 1.355),      // pmol/L → pg/mL
        "folate" to mapOf("nmol/L" to 0.4418),          // nmol/L → ng/mL
        "vitamin_b6" to mapOf("ng/mL" to 4.09),         // ng/mL → nmol/L
        "vitamin_b1" to mapOf("nmol/L" to 0.2665),      // nmol/L → ng/mL
        "vitamin_c" to mapOf("mg/L" to 5.678, "mg/dL" to 56.78), // → μmol/L
        "vitamin_a" to mapOf("μg/dL" to 0.0349, "ng/mL" to 0.00349), // → μmol/L
        "vitamin_e" to mapOf("mg/dL" to 23.22, "μg/mL" to 2.322),   // → μmol/L
        "serum_iron" to mapOf("μg/dL" to 0.179)         // → μmol/L
    )

    /**
     * 对单条解析记录做全量归一化。
     * 未知指标类型 / 未知单位 / 无换算系数时保持原样，绝不猜测。
     */
    fun normalize(type: String, value: String, numericValue: Double?, unit: String): Result {
        val stdType = normalizeType(type)
        val stdUnit = HealthTypes.unit(stdType)      // 标准单位（未知类型为空串）
        val canonUnit = normalizeUnit(unit)

        // 无标准单位、单位一致、或单位为空：只做指标名与写法归一
        if (stdUnit.isBlank() || canonUnit.isBlank() || canonUnit == stdUnit) {
            return Result(
                type = stdType,
                value = value,
                numericValue = numericValue,
                unit = canonUnit.ifBlank { stdUnit },
                note = null
            )
        }

        // 有换算系数：数值与单位同步换算
        val factor = UNIT_FACTORS[stdType]?.get(canonUnit)
        if (factor == null) {
            // 单位不同但无可靠换算系数（如血压 mmHg 以外的情况）——保留原单位与原值
            return Result(stdType, value, numericValue, canonUnit, null)
        }

        val source = numericValue ?: value.trim().toDoubleOrNull()
            ?: return Result(stdType, value, numericValue, canonUnit, null)
        val converted = round2(source * factor)
        return Result(
            type = stdType,
            value = formatDouble(converted),
            numericValue = converted,
            unit = stdUnit,
            note = "$canonUnit 已换算为 $stdUnit"
        )
    }

    private fun round2(v: Double): Double = kotlin.math.round(v * 100) / 100

    /** 去掉多余小数位（62.50 → 62.5、175.0 → 175） */
    private fun formatDouble(v: Double): String =
        if (v == v.toLong().toDouble()) v.toLong().toString() else v.toString()
}

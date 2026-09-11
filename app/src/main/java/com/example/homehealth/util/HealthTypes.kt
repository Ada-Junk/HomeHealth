package com.example.homehealth.util

import com.example.homehealth.R

/**
 * 健康指标体系：覆盖体检报告常见全类别指标。
 * 每项含英文标识、中文名、单位、参考范围（展示文本 + 数值上下限用于异常检测）。
 * 参考范围为通用成人参考值（男女合并区间），仅供参考，实际以报告单标注为准。
 */

/** 单个指标定义 */
data class MetricDef(
    val type: String,          // 英文标识（数据库存储 / LLM 输出）
    val label: String,         // 中文名
    val unit: String,          // 单位
    val rangeText: String,     // 参考范围文字
    val low: Double?,          // 数值下限（null 无）
    val high: Double?,         // 数值上限（null 无）
    val group: String          // 所属分组
)

object HealthTypes {

    // ---- 兼容旧数据的常量 ----
    const val BLOOD_PRESSURE = "blood_pressure"
    const val BLOOD_GLUCOSE = "blood_glucose"
    const val TOTAL_CHOLESTEROL = "total_cholesterol"
    const val TRIGLYCERIDES = "triglycerides"
    const val HDL = "hdl"
    const val LDL = "ldl"
    const val WEIGHT = "weight"
    const val HEART_RATE = "heart_rate"

    /** 指标分组（key → 显示名），按展示顺序 */
    val GROUPS: List<Pair<String, String>> = listOf(
        "vitals" to "基础体征",
        "glucose" to "血糖代谢",
        "lipids" to "血脂",
        "cbc" to "血常规",
        "liver" to "肝功能",
        "kidney" to "肾功能",
        "thyroid" to "甲状腺",
        "vitamins" to "维生素与微量元素",
        "electrolytes" to "电解质",
        "others" to "其他"
    )

    /** 全部指标定义 */
    val DEFS: List<MetricDef> = listOf(
        // ---- 基础体征 ----
        MetricDef(BLOOD_PRESSURE, "血压", "mmHg", "90-139/60-89", 90.0, 139.0, "vitals"),
        MetricDef(HEART_RATE, "心率", "bpm", "60-100", 60.0, 100.0, "vitals"),
        MetricDef(WEIGHT, "体重", "kg", "—", null, null, "vitals"),
        MetricDef("height", "身高", "cm", "—", null, null, "vitals"),
        MetricDef("bmi", "BMI 指数", "kg/m²", "18.5-23.9", 18.5, 23.9, "vitals"),
        MetricDef("body_fat", "体脂率", "%", "男 10-20 / 女 18-28", 10.0, 28.0, "vitals"),
        MetricDef("waist_circumference", "腰围", "cm", "男 <90 / 女 <85", null, 90.0, "vitals"),
        MetricDef("body_temperature", "体温", "℃", "36.0-37.2", 36.0, 37.2, "vitals"),
        MetricDef("spo2", "血氧饱和度", "%", "95-100", 95.0, 100.0, "vitals"),

        // ---- 血糖代谢 ----
        MetricDef(BLOOD_GLUCOSE, "空腹血糖", "mmol/L", "3.9-6.1", 3.9, 6.1, "glucose"),
        MetricDef("postprandial_glucose", "餐后2小时血糖", "mmol/L", "3.9-7.8", 3.9, 7.8, "glucose"),
        MetricDef("hba1c", "糖化血红蛋白", "%", "4.0-6.0", 4.0, 6.0, "glucose"),

        // ---- 血脂 ----
        MetricDef(TOTAL_CHOLESTEROL, "总胆固醇", "mmol/L", "2.8-5.2", 2.8, 5.2, "lipids"),
        MetricDef(TRIGLYCERIDES, "甘油三酯", "mmol/L", "0.4-1.7", 0.4, 1.7, "lipids"),
        MetricDef(HDL, "高密度脂蛋白", "mmol/L", "≥1.0", 1.0, null, "lipids"),
        MetricDef(LDL, "低密度脂蛋白", "mmol/L", "<3.4", null, 3.4, "lipids"),

        // ---- 血常规 ----
        MetricDef("wbc", "白细胞计数", "×10⁹/L", "3.5-9.5", 3.5, 9.5, "cbc"),
        MetricDef("rbc", "红细胞计数", "×10¹²/L", "3.8-5.8", 3.8, 5.8, "cbc"),
        MetricDef("hemoglobin", "血红蛋白", "g/L", "男 130-175 / 女 115-150", 115.0, 175.0, "cbc"),
        MetricDef("hematocrit", "红细胞压积", "%", "男 40-50 / 女 35-45", 35.0, 50.0, "cbc"),
        MetricDef("mcv", "平均红细胞体积", "fL", "80-100", 80.0, 100.0, "cbc"),
        MetricDef("platelets", "血小板计数", "×10⁹/L", "125-350", 125.0, 350.0, "cbc"),
        MetricDef("neutrophil_ratio", "中性粒细胞比率", "%", "40-75", 40.0, 75.0, "cbc"),
        MetricDef("lymphocyte_ratio", "淋巴细胞比率", "%", "20-50", 20.0, 50.0, "cbc"),

        // ---- 肝功能 ----
        MetricDef("alt", "谷丙转氨酶", "U/L", "9-50", 9.0, 50.0, "liver"),
        MetricDef("ast", "谷草转氨酶", "U/L", "15-40", 15.0, 40.0, "liver"),
        MetricDef("ggt", "γ-谷氨酰转肽酶", "U/L", "10-60", 10.0, 60.0, "liver"),
        MetricDef("total_bilirubin", "总胆红素", "μmol/L", "3.4-20.5", 3.4, 20.5, "liver"),
        MetricDef("albumin", "白蛋白", "g/L", "40-55", 40.0, 55.0, "liver"),

        // ---- 肾功能 ----
        MetricDef("creatinine", "肌酐", "μmol/L", "男 57-97 / 女 41-73", 41.0, 97.0, "kidney"),
        MetricDef("urea_nitrogen", "尿素氮", "mmol/L", "2.9-8.2", 2.9, 8.2, "kidney"),
        MetricDef("uric_acid", "尿酸", "μmol/L", "男 208-428 / 女 155-357", 155.0, 428.0, "kidney"),

        // ---- 甲状腺 ----
        MetricDef("tsh", "促甲状腺激素", "mIU/L", "0.27-4.2", 0.27, 4.2, "thyroid"),
        MetricDef("ft3", "游离三碘甲状腺原氨酸", "pmol/L", "3.1-6.8", 3.1, 6.8, "thyroid"),
        MetricDef("ft4", "游离甲状腺素", "pmol/L", "12-22", 12.0, 22.0, "thyroid"),

        // ---- 维生素与微量元素 ----
        MetricDef("vitamin_a", "维生素A", "μmol/L", "0.7-2.6", 0.7, 2.6, "vitamins"),
        MetricDef("vitamin_b1", "维生素B1", "ng/mL", "24-66", 24.0, 66.0, "vitamins"),
        MetricDef("vitamin_b6", "维生素B6", "nmol/L", "20-86", 20.0, 86.0, "vitamins"),
        MetricDef("folate", "叶酸", "ng/mL", "3.6-17", 3.6, 17.0, "vitamins"),
        MetricDef("vitamin_b12", "维生素B12", "pg/mL", "200-900", 200.0, 900.0, "vitamins"),
        MetricDef("vitamin_c", "维生素C", "μmol/L", "28-71", 28.0, 71.0, "vitamins"),
        MetricDef("vitamin_d", "25-羟维生素D", "ng/mL", "30-100", 30.0, 100.0, "vitamins"),
        MetricDef("vitamin_e", "维生素E", "μmol/L", "11.6-46.4", 11.6, 46.4, "vitamins"),
        MetricDef("serum_iron", "血清铁", "μmol/L", "男 11-30 / 女 9-27", 9.0, 30.0, "vitamins"),

        // ---- 电解质 ----
        MetricDef("potassium", "血钾", "mmol/L", "3.5-5.3", 3.5, 5.3, "electrolytes"),
        MetricDef("sodium", "血钠", "mmol/L", "137-147", 137.0, 147.0, "electrolytes"),
        MetricDef("chloride", "血氯", "mmol/L", "99-110", 99.0, 110.0, "electrolytes"),
        MetricDef("calcium", "血钙", "mmol/L", "2.11-2.52", 2.11, 2.52, "electrolytes"),

        // ---- 其他 ----
        MetricDef("bone_density_t", "骨密度T值", "", "≥-1", -1.0, null, "others")
    )

    private val DEF_MAP: Map<String, MetricDef> = DEFS.associateBy { it.type }

    /** 所有指标 type（顺序 = 定义顺序） */
    val ALL: List<String> = DEFS.map { it.type }

    /** 按分组取指标 */
    fun byGroup(groupKey: String): List<MetricDef> = DEFS.filter { it.group == groupKey }

    fun def(type: String): MetricDef? = DEF_MAP[type]

    fun label(type: String): String = DEF_MAP[type]?.label ?: type

    fun unit(type: String): String = DEF_MAP[type]?.unit ?: ""

    fun range(type: String): String = DEF_MAP[type]?.rangeText ?: "—"

    // ---- UI 本地化：指标名 / 分组名的字符串资源 ----

    /** 指标名字符串资源 id（UI 显示用；数据层与 LLM 请继续用 [label]） */
    fun labelRes(type: String): Int = when (type) {
        BLOOD_PRESSURE -> R.string.metric_blood_pressure
        HEART_RATE -> R.string.metric_heart_rate
        WEIGHT -> R.string.metric_weight
        "height" -> R.string.metric_height
        "bmi" -> R.string.metric_bmi
        "body_fat" -> R.string.metric_body_fat
        "waist_circumference" -> R.string.metric_waist_circumference
        "body_temperature" -> R.string.metric_body_temperature
        "spo2" -> R.string.metric_spo2
        BLOOD_GLUCOSE -> R.string.metric_blood_glucose
        "postprandial_glucose" -> R.string.metric_postprandial_glucose
        "hba1c" -> R.string.metric_hba1c
        TOTAL_CHOLESTEROL -> R.string.metric_total_cholesterol
        TRIGLYCERIDES -> R.string.metric_triglycerides
        HDL -> R.string.metric_hdl
        LDL -> R.string.metric_ldl
        "wbc" -> R.string.metric_wbc
        "rbc" -> R.string.metric_rbc
        "hemoglobin" -> R.string.metric_hemoglobin
        "hematocrit" -> R.string.metric_hematocrit
        "mcv" -> R.string.metric_mcv
        "platelets" -> R.string.metric_platelets
        "neutrophil_ratio" -> R.string.metric_neutrophil_ratio
        "lymphocyte_ratio" -> R.string.metric_lymphocyte_ratio
        "alt" -> R.string.metric_alt
        "ast" -> R.string.metric_ast
        "ggt" -> R.string.metric_ggt
        "total_bilirubin" -> R.string.metric_total_bilirubin
        "albumin" -> R.string.metric_albumin
        "creatinine" -> R.string.metric_creatinine
        "urea_nitrogen" -> R.string.metric_urea_nitrogen
        "uric_acid" -> R.string.metric_uric_acid
        "tsh" -> R.string.metric_tsh
        "ft3" -> R.string.metric_ft3
        "ft4" -> R.string.metric_ft4
        "vitamin_a" -> R.string.metric_vitamin_a
        "vitamin_b1" -> R.string.metric_vitamin_b1
        "vitamin_b6" -> R.string.metric_vitamin_b6
        "folate" -> R.string.metric_folate
        "vitamin_b12" -> R.string.metric_vitamin_b12
        "vitamin_c" -> R.string.metric_vitamin_c
        "vitamin_d" -> R.string.metric_vitamin_d
        "vitamin_e" -> R.string.metric_vitamin_e
        "serum_iron" -> R.string.metric_serum_iron
        "potassium" -> R.string.metric_potassium
        "sodium" -> R.string.metric_sodium
        "chloride" -> R.string.metric_chloride
        "calcium" -> R.string.metric_calcium
        "bone_density_t" -> R.string.metric_bone_density_t
        else -> 0
    }

    /** 分组名字符串资源 id */
    fun groupRes(groupKey: String): Int = when (groupKey) {
        "vitals" -> R.string.group_vitals
        "glucose" -> R.string.group_glucose
        "lipids" -> R.string.group_lipids
        "cbc" -> R.string.group_cbc
        "liver" -> R.string.group_liver
        "kidney" -> R.string.group_kidney
        "thyroid" -> R.string.group_thyroid
        "vitamins" -> R.string.group_vitamins
        "electrolytes" -> R.string.group_electrolytes
        else -> R.string.group_others
    }

    /** 上升视为不良（用于趋势着色与预警措辞） */
    fun higherIsWorse(type: String): Boolean = when (type) {
        HDL, ALBUMIN, "folate", "vitamin_a", "vitamin_b1", "vitamin_b6",
        "vitamin_b12", "vitamin_c", "vitamin_d", "vitamin_e", "serum_iron",
        "height", "bone_density_t" -> false
        else -> true
    }

    /** 趋势预警阈值：最近三次持续同向且累计变化超过阈值时生成预警 */
    val TREND_THRESHOLDS: Map<String, Double> = mapOf(
        BLOOD_PRESSURE to 10.0,
        BLOOD_GLUCOSE to 1.0,
        TOTAL_CHOLESTEROL to 0.5,
        TRIGLYCERIDES to 0.5,
        HDL to 0.3,
        LDL to 0.5,
        WEIGHT to 2.0,
        HEART_RATE to 10.0,
        "bmi" to 1.0,
        "body_fat" to 2.0,
        "hba1c" to 0.5,
        "wbc" to 1.0,
        "hemoglobin" to 10.0,
        "platelets" to 30.0,
        "alt" to 20.0,
        "uric_acid" to 60.0,
        "creatinine" to 15.0,
        "tsh" to 1.0,
        "vitamin_d" to 10.0
    )

    private const val ALBUMIN = "albumin"
}

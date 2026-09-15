package com.example.homehealth.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `SchemaNormalizer` 与指标字典的单元测试（纯 JVM，无需设备）。
 *
 * 为什么优先测这两个：它们是纯函数 / 纯数据，**是项目里最容易测、也最值得测的部分**。
 * 归一化层是全项目最硬的技术资产（106 条别名 + 24 类换算系数），一旦改错，
 * 趋势图与异常预警会静默给出错误结论 —— 而且不会报错，只会算错。
 *
 * 这里刻意包含几条**结构性断言**（别名指向的指标必须存在、指标 type 不能重复），
 * 它们不验证业务逻辑，只保证数据表本身没有笔误 —— 这类错误靠人眼看极难发现。
 */
/** 换算用例：源数值 + 源单位 → 期望的标准单位数值与展示文本 */
private data class ConvCase(
    val type: String,
    val value: String,
    val fromUnit: String,
    val expectedNumber: Double,
    val expectedText: String
)

class SchemaNormalizerTest {
    // ---------- 结构性断言：数据表本身没有笔误 ----------

    @Test
    fun `别名表里每一项都指向已定义的指标`() {
        val known = HealthTypes.ALL.toSet()
        val unknown = SchemaNormalizer.typeAliases.filterValues { it !in known }
        assertTrue("别名表存在指向未定义指标的项（多半是拼写错误）：$unknown", unknown.isEmpty())
    }

    @Test
    fun `指标 type 不重复`() {
        val types = HealthTypes.DEFS.map { it.type }
        assertEquals("指标字典存在重复 type", types.size, types.toSet().size)
    }

    @Test
    fun `趋势阈值表只包含已定义的指标`() {
        val unknown = HealthTypes.TREND_THRESHOLDS.keys - HealthTypes.ALL.toSet()
        assertTrue("趋势阈值表存在未知指标：$unknown", unknown.isEmpty())
    }

    @Test
    fun `性别特异区间的键合法且上下限顺序正确`() {
        val legalGenders = setOf(HealthTypes.GENDER_MALE, HealthTypes.GENDER_FEMALE)
        HealthTypes.DEFS.forEach { def ->
            def.byGender.keys.forEach { g ->
                assertTrue("${def.type} 的性别键非法：$g", g in legalGenders)
            }
            def.byGender.forEach { (g, range) ->
                val low = range.low
                val high = range.high
                if (low != null && high != null) {
                    assertTrue("${def.type}/$g 的上下限颠倒：$low > $high", low <= high)
                }
            }
        }
    }

    // ---------- 指标名归一化 ----------

    @Test
    fun `指标别名映射到标准类型`() {
        val cases = mapOf(
            "血糖" to "blood_glucose",
            "空腹血糖" to "blood_glucose",
            "FPG" to "blood_glucose",
            "glucose" to "blood_glucose",
            "糖化血红蛋白" to "hba1c",
            "总胆固醇" to "total_cholesterol",
            "TG" to "triglycerides",
            "HDL-C" to "hdl",
            "25-羟维生素D" to "vitamin_d",
            "25(OH)D" to "vitamin_d",
            "vitamin d" to "vitamin_d",
            "谷丙转氨酶" to "alt",
            "SGPT" to "alt",
            "尿酸" to "uric_acid",
            "血钾" to "potassium",
            "K+" to "potassium"
        )
        cases.forEach { (raw, expected) ->
            assertEquals("别名「$raw」应映射为 $expected", expected, SchemaNormalizer.normalizeType(raw))
        }
    }

    @Test
    fun `未知指标名原样返回`() {
        assertEquals("not_a_metric", SchemaNormalizer.normalizeType("not_a_metric"))
    }

    // ---------- 单位写法归一化 ----------

    @Test
    fun `单位写法归一化`() {
        val cases = mapOf(
            "mmol/l" to "mmol/L",
            "MMOL/L" to "mmol/L",
            "umol/L" to "μmol/L",
            "µmol/L" to "μmol/L",   // 微符号（U+00B5）应统一为希腊字母 μ
            "mg/dl" to "mg/dL",
            "u/l" to "U/L",
            "mmhg" to "mmHg",
            "°c" to "℃"
        )
        cases.forEach { (raw, expected) ->
            assertEquals("单位「$raw」应归一为 $expected", expected, SchemaNormalizer.normalizeUnit(raw))
        }
    }

    // ---------- 跨单位换算（最硬的部分） ----------

    @Test
    fun `常用换算系数结果正确`() {
        val cases = listOf(
            ConvCase("blood_glucose", "97", "mg/dL", 5.38, "5.38"),        // ×0.0555
            ConvCase("total_cholesterol", "200", "mg/dL", 5.18, "5.18"),   // ×0.0259
            ConvCase("triglycerides", "100", "mg/dL", 1.13, "1.13"),       // ×0.0113
            ConvCase("creatinine", "1.0", "mg/dL", 88.4, "88.4"),          // ×88.4
            ConvCase("uric_acid", "5.0", "mg/dL", 297.4, "297.4"),         // ×59.48
            ConvCase("vitamin_d", "50", "nmol/L", 20.0, "20"),            // ×0.4，整数去掉尾零
            ConvCase("hemoglobin", "14", "g/dL", 140.0, "140"),           // ×10
            ConvCase("calcium", "5.0", "mEq/L", 2.5, "2.5")               // ×0.5
        )
        cases.forEach { c ->
            val r = SchemaNormalizer.normalize(c.type, c.value, c.value.toDouble(), c.fromUnit)
            assertEquals("${c.type} 换算数值错误", c.expectedNumber, r.numericValue!!, 0.001)
            assertEquals("${c.type} 换算展示文本错误", c.expectedText, r.value)
            assertEquals("${c.type} 应换算到标准单位", HealthTypes.unit(c.type), r.unit)
            assertNotNull("${c.type} 应给出换算说明", r.note)
        }
    }

    @Test
    fun `单位一致时不做换算也不加说明`() {
        val r = SchemaNormalizer.normalize("blood_glucose", "5.4", 5.4, "mmol/L")
        assertEquals("5.4", r.value)
        assertEquals(5.4, r.numericValue!!, 0.0001)
        assertEquals("mmol/L", r.unit)
        assertNull("同单位不应出现换算说明", r.note)
    }

    @Test
    fun `没有可靠系数时不猜单位`() {
        // 血糖的标准单位是 mmol/L，"袋" 显然不是可换算单位
        val r = SchemaNormalizer.normalize("blood_glucose", "5.4", 5.4, "袋")
        assertEquals("应保留原值", "5.4", r.value)
        assertEquals("应保留原单位", "袋", r.unit)
        assertNull("不该编造换算说明", r.note)
    }

    @Test
    fun `未知指标不被改写`() {
        val r = SchemaNormalizer.normalize("unknown_metric", "42", 42.0, "xyz")
        assertEquals("unknown_metric", r.type)
        assertEquals("42", r.value)
        assertEquals("xyz", r.unit)
    }

    @Test
    fun `血压不被换算且保留原始文本`() {
        val r = SchemaNormalizer.normalize("blood_pressure", "120/80", 120.0, "mmHg")
        assertEquals("120/80", r.value)
        assertEquals(120.0, r.numericValue!!, 0.0001)
        assertEquals("mmHg", r.unit)
        assertNull(r.comparator)
    }

    // ---------- 区间型结果（比较符） ----------

    @Test
    fun `比较符解析`() {
        assertEquals(SchemaNormalizer.COMPARATOR_LT to 0.1, SchemaNormalizer.parseComparator("<0.1"))
        assertEquals(SchemaNormalizer.COMPARATOR_GT to 100.0, SchemaNormalizer.parseComparator(">100"))
        assertEquals(SchemaNormalizer.COMPARATOR_LT to 5.0, SchemaNormalizer.parseComparator("≤5"))
        assertEquals(SchemaNormalizer.COMPARATOR_GT to 5.0, SchemaNormalizer.parseComparator("≥5"))
        assertEquals(SchemaNormalizer.COMPARATOR_LT to 0.1, SchemaNormalizer.parseComparator("＜0.1")) // 全角
        assertEquals(null to 5.4, SchemaNormalizer.parseComparator("5.4"))
        assertEquals(null to null, SchemaNormalizer.parseComparator("阴性"))
        assertEquals(null to null, SchemaNormalizer.parseComparator(""))
    }

    @Test
    fun `区间型结果换算后保留比较符`() {
        // 血糖 "<0.1 mg/dL" → 0.1 × 0.0555 = 0.00555 → round2 → 0.01
        val r = SchemaNormalizer.normalize("blood_glucose", "<0.1", null, "mg/dL")
        assertEquals(SchemaNormalizer.COMPARATOR_LT, r.comparator)
        assertEquals("<0.01", r.value)
        assertEquals(0.01, r.numericValue!!, 0.0001)
    }

    @Test
    fun `区间型结果在无换算时也保留文本与比较符`() {
        val r = SchemaNormalizer.normalize("blood_glucose", "<0.1", null, "mmol/L")
        assertEquals(SchemaNormalizer.COMPARATOR_LT, r.comparator)
        assertEquals("<0.1", r.value)
        assertEquals(0.1, r.numericValue!!, 0.0001)
    }

    // ---------- 性别化参考范围 ----------

    @Test
    fun `参考范围按性别取值`() {
        // 血红蛋白：男 130-175 / 女 115-150
        assertEquals(130.0, HealthTypes.def("hemoglobin")!!.rangeFor(HealthTypes.GENDER_MALE).low!!, 0.001)
        assertEquals(150.0, HealthTypes.def("hemoglobin")!!.rangeFor(HealthTypes.GENDER_FEMALE).high!!, 0.001)
        // 未提供性别时回退通用区间（合并区间）
        assertEquals(115.0, HealthTypes.def("hemoglobin")!!.rangeFor(null).low!!, 0.001)
    }

    @Test
    fun `无性别差异的指标两种性别取值一致`() {
        val male = HealthTypes.def("blood_glucose")!!.rangeFor(HealthTypes.GENDER_MALE)
        val female = HealthTypes.def("blood_glucose")!!.rangeFor(HealthTypes.GENDER_FEMALE)
        assertEquals(male.low, female.low)
        assertEquals(male.high, female.high)
        assertEquals(male.text, female.text)
    }

    @Test
    fun `越高越好的方向判定`() {
        // 这几项的上升是好事
        listOf("hdl", "albumin", "folate", "vitamin_d", "spo2", "bone_density_t", "height").forEach {
            assertTrue("$it 上升应为好事", !HealthTypes.higherIsWorse(it))
        }
        // 这几项的上升是坏事（spo2 曾漏在白名单外，是回归用例）
        listOf("blood_pressure", "blood_glucose", "ldl", "alt", "uric_acid", "tsh", "weight").forEach {
            assertTrue("$it 上升应为坏事", HealthTypes.higherIsWorse(it))
        }
    }
}

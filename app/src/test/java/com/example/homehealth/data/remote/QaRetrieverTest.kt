package com.example.homehealth.data.remote

import com.example.homehealth.data.local.entity.HealthRecord
import com.example.homehealth.util.HealthTypes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A1 检索层单测：切分规则、BM25 排序、别名命中、引用编号的确定性。
 * 覆盖验收标准：「问血糖怎么样，召回的记录集合可解释」。
 */
class QaRetrieverTest {

    private fun record(
        type: String,
        value: String,
        numericValue: Double? = null,
        unit: String = "",
        daysAgo: Long = 1,
        notes: String? = null,
        comparator: String? = null
    ): HealthRecord = HealthRecord(
        id = "$type-$daysAgo-$value",
        memberId = "m1",
        type = type,
        value = value,
        numericValue = numericValue,
        unit = unit,
        recordDate = 1_760_000_000_000 - daysAgo * 24 * 60 * 60 * 1000,
        notes = notes,
        comparator = comparator
    )

    private fun corpus(): List<HealthRecord> = listOf(
        record(HealthTypes.BLOOD_GLUCOSE, "5.5", 5.5, "mmol/L", daysAgo = 3),
        record(HealthTypes.BLOOD_GLUCOSE, "6.2", 6.2, "mmol/L", daysAgo = 10),
        record(HealthTypes.BLOOD_GLUCOSE, "5.8", 5.8, "mmol/L", daysAgo = 20, notes = "早餐后测"),
        record(HealthTypes.WEIGHT, "72.5", 72.5, "kg", daysAgo = 5),
        record(HealthTypes.WEIGHT, "73.0", 73.0, "kg", daysAgo = 15),
        record(HealthTypes.HEART_RATE, "76", 76.0, "bpm", daysAgo = 2),
        record("uric_acid", "380", 380.0, "μmol/L", daysAgo = 8),
        record(HealthTypes.HDL, "1.4", 1.4, "mmol/L", daysAgo = 9)
    )

    @Test
    fun `tokenize 拆出中文单字双字与拉丁数字串`() {
        val tokens = QaRetriever.tokenize("空腹血糖6.2mmol/L")
        assertTrue(tokens.containsAll(listOf("空", "空腹", "腹血", "血", "血糖", "糖", "mmol")))
        // 数字被小数点拆开、单位斜杠分隔，全部转小写
        assertTrue(tokens.containsAll(listOf("6", "2", "l")))
        assertFalse(tokens.contains("6.2"))
    }

    @Test
    fun `问血糖只召回血糖记录且按相关度排序`() {
        val index = QaRetriever.index(corpus())
        val hits = index.search("我最近血糖怎么样")

        assertTrue("应召回记录，实际 0 条", hits.isNotEmpty())
        assertTrue(
            "召回结果里混入了无关指标：${hits.map { it.record.type }}",
            hits.all { it.record.type == HealthTypes.BLOOD_GLUCOSE }
        )
        // 三条血糖记录应全部召回，且得分降序、编号连续
        assertEquals(3, hits.size)
        assertEquals(listOf(1, 2, 3), hits.map { it.rank })
        assertTrue(hits.zipWithNext().all { (a, b) -> a.score >= b.score })
    }

    @Test
    fun `拉丁别名 LDL 能命中对应指标`() {
        val index = QaRetriever.index(corpus() + record(HealthTypes.LDL, "2.9", 2.9, "mmol/L", daysAgo = 4))
        val hits = index.search("我的LDL多少")

        assertTrue(hits.isNotEmpty())
        assertTrue(hits.all { it.record.type == HealthTypes.LDL })
    }

    @Test
    fun `单字口语提问（钾）通过单字粒度召回`() {
        val index = QaRetriever.index(
            corpus() + record("potassium", "4.2", 4.2, "mmol/L", daysAgo = 6)
        )
        val hits = index.search("钾怎么样")

        assertTrue(hits.isNotEmpty())
        assertEquals("potassium", hits.first().record.type)
    }

    @Test
    fun `比较符语义进入文档文本`() {
        val lt = QaRetriever.documentText(
            record("hscrp", "<0.1", 0.1, "mg/L", comparator = "LT")
        )
        assertTrue("LT 文档应包含『低于』：$lt", lt.contains("低于"))

        val withNotes = QaRetriever.documentText(
            record(HealthTypes.BLOOD_GLUCOSE, "5.5", 5.5, "mmol/L", notes = "空腹")
        )
        assertTrue(withNotes.contains("空腹"))
    }

    @Test
    fun `空语料与无关问题都不召回`() {
        assertTrue(QaRetriever.index(emptyList()).search("血糖").isEmpty())
        assertTrue(QaRetriever.index(corpus()).search("今天天气如何").isEmpty())
        // topK <= 0 时直接返回空
        assertTrue(QaRetriever.index(corpus()).search("血糖", topK = 0).isEmpty())
    }

    @Test
    fun `召回条数受 topK 限制`() {
        val many = (1..30).map { record(HealthTypes.BLOOD_GLUCOSE, "$it", it.toDouble(), "mmol/L", daysAgo = it.toLong()) }
        val hits = QaRetriever.index(many).search("血糖", topK = 5)
        assertEquals(5, hits.size)
        assertEquals(listOf(1, 2, 3, 4, 5), hits.map { it.rank })
    }

    @Test
    fun `泛化总结类问题抽不出强指标词`() {
        // 「整体健康状况」里的单字"体"不得撞进强词表（否则会窄化到体重/体脂率）
        assertTrue(QaRetriever.strongTermsOf("帮我看看我的整体健康状况怎么样").isEmpty())
        assertTrue(QaRetriever.strongTermsOf("体检结果如何").isEmpty())
    }

    @Test
    fun `指标名别名与成组词都算强指标词`() {
        assertTrue(QaRetriever.strongTermsOf("我最近血糖怎么样").contains("血糖"))
        assertTrue(QaRetriever.strongTermsOf("LDL多少").contains("ldl"))
        assertTrue(QaRetriever.strongTermsOf("血脂四项正常吗").contains("血脂"))
        // 多字别名通过双字窗口命中（维生素d → 维生/生素/素d）
        assertTrue(QaRetriever.strongTermsOf("维生素d够不够").isNotEmpty())
    }

    @Test
    fun `restrictTo 让弱匹配不进召回`() {
        val index = QaRetriever.index(corpus() + record(HealthTypes.WEIGHT, "72", 72.0, "kg", daysAgo = 1))
        // "身体怎么样" 无强指标词 → restrictTo 为空集 → 不召回（旧实现会因"体"窄化到体重）
        val strong = QaRetriever.strongTermsOf("身体怎么样")
        assertTrue(strong.isEmpty())
        assertTrue(index.search("身体怎么样", restrictTo = strong).isEmpty())
        // 限定强词后，只按强词评分：血糖问题只回血糖记录
        val hits = index.search("血糖怎么样", restrictTo = QaRetriever.strongTermsOf("血糖怎么样"))
        assertTrue(hits.isNotEmpty())
        assertTrue(hits.all { it.record.type == HealthTypes.BLOOD_GLUCOSE })
    }
}

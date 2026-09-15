package com.example.homehealth.data.remote

import com.example.homehealth.data.local.entity.HealthRecord
import com.example.homehealth.util.DateUtils
import com.example.homehealth.util.HealthTypes
import com.example.homehealth.util.SchemaNormalizer
import kotlin.math.ln

/**
 * 问答检索器（A1「真实 RAG」的检索层）：
 * 把成员的健康记录文本化建索引，按问题做 BM25 Top-K 召回，并给出可解释的引用编号。
 *
 * 设计取舍（面试要点）：
 * - **BM25 而非向量检索**：个人健康记录量级在千条以内，中文体检指标是强关键词场景
 *   （问"血糖"就该召回血糖记录），BM25 无模型依赖、零上传、可解释，是第一性选择；
 *   向量召回只在"同义改写很多、关键词覆盖不住"时才有必要，届时用本地小模型接入，
 *   云端 Embedding API 会破坏「数据不上传」的承诺。
 * - **内存索引而非 SQLite FTS**：语料在每次提问时由单条 `memberId` 索引查询载入，
 *   千条量级建索引耗时毫秒级；FTS 表要动 schema（DB v8 + 迁移），数据量涨上去再引入。
 * - **中文切分用「单字 + 双字组合」**：体检指标名以双字词为主（血糖、尿酸），
 *   双字粒度保精度；别名里有单字词（钾 / 钠 / 钙），单字粒度保召回。
 *   BM25 的 IDF 会自动压低高频单字的权重，不需要人工加权。
 * - **文档文本附带指标别名**（复用 [SchemaNormalizer.typeAliases]，不建第二份副本）：
 *   让「LDL 多少」「维D 够不够」这类口语提问也能命中对应记录。
 */
class QaRetriever private constructor(private val docs: List<Doc>) {

    /** 一条召回结果：记录本身、BM25 得分、引用编号（1 起） */
    data class Scored(val record: HealthRecord, val score: Double, val rank: Int)

    internal class Doc(val record: HealthRecord, val tf: Map<String, Int>, val length: Int)

    /**
     * 按问题检索最相关的记录。
     *
     * @param topK 最多返回条数；得分 <= 0 的文档一律不返回（完全不相关的记录不能进上下文）
     * @param restrictTo 非 null 时只用这些词评分（应为 [strongTermsOf] 的结果）——
     *   泛化词（"身体"的"体"）不再参与召回，弱匹配无法把上下文窄化到无关指标
     * @return 按得分降序的 Top-K，[Scored.rank] 从 1 连续编号
     */
    fun search(
        query: String,
        topK: Int = DEFAULT_TOP_K,
        restrictTo: Set<String>? = null
    ): List<Scored> {
        if (docs.isEmpty() || topK <= 0) return emptyList()
        var queryTerms = tokenize(query)
        if (restrictTo != null) queryTerms = queryTerms.filter { it in restrictTo }
        if (queryTerms.isEmpty()) return emptyList()

        val n = docs.size
        val avgLen = docs.sumOf { it.length } / n.toDouble()
        val df = HashMap<String, Int>()
        for (doc in docs) for (term in doc.tf.keys) df.merge(term, 1, Int::plus)

        val queryTf = queryTerms.groupingBy { it }.eachCount()
        val hits = ArrayList<Pair<Doc, Double>>(docs.size)
        for (doc in docs) {
            var score = 0.0
            for ((term, qtf) in queryTf) {
                val tf = doc.tf[term]?.toDouble() ?: continue
                val idf = ln(1.0 + (n - df.getValue(term) + 0.5) / (df.getValue(term) + 0.5))
                val norm = K1 * (1.0 - B + B * doc.length / avgLen)
                score += idf * qtf * (tf * (K1 + 1.0)) / (tf + norm)
            }
            if (score > 0.0) hits.add(doc to score)
        }
        return hits.sortedByDescending { it.second }
            .take(topK)
            .mapIndexed { index, (doc, score) -> Scored(doc.record, score, index + 1) }
    }

    companion object {

        /** 默认召回条数：12 条足以覆盖一个指标的全部近期记录 + 少量相关指标 */
        const val DEFAULT_TOP_K = 12

        /** BM25 经典参数（k1 控制词频饱和，b 控制文档长度归一的强度） */
        private const val K1 = 1.5
        private const val B = 0.75

        /** 别名 → 指标类型 的反查表（构建索引时给文档文本补别名） */
        private val ALIASES_BY_TYPE: Map<String, List<String>> by lazy {
            SchemaNormalizer.typeAliases.entries
                .groupBy({ it.value }, { it.key })
                .mapValues { (_, aliases) -> aliases.distinct() }
        }

        /** 为一批记录建索引（调用方保证同一成员的记录；输入顺序不影响检索结果） */
        fun index(records: List<HealthRecord>): QaRetriever =
            QaRetriever(records.map { record ->
                val tf = termFrequency(documentText(record))
                Doc(record, tf, tf.values.sum())
            })

        /**
         * 「强指标词」表：指标中文名 / 全部别名 / 口语成组词（血脂、肝功能…，复用
         * [LocalQaEngine.GROUP_KEYWORDS]，不建第二份副本）。多字条目额外收录相邻双字窗口，
         * 保证「维生素d」这类长名也能被问题里的双字碎片命中。
         */
        private val STRONG_VOCAB: Set<String> by lazy {
            val raw = buildList {
                HealthTypes.DEFS.forEach { add(it.label) }
                addAll(SchemaNormalizer.typeAliases.keys)
                addAll(LocalQaEngine.GROUP_KEYWORDS.keys)
            }
            val out = HashSet<String>()
            for (entry in raw) {
                val e = entry.lowercase().replace(" ", "")
                if (e.isEmpty()) continue
                out.add(e)
                if (e.length > 2) for (i in 0 until e.length - 1) out.add(e.substring(i, i + 2))
            }
            out
        }

        /**
         * 抽取问题中的「强指标词」。
         * 返回空集 = 泛化总结类问题（没有任何可靠的指标词），
         * 调用方应回退全量摘要，而不是放行检索——否则"身体怎么样"里的
         * 单字"体"撞上体重记录，会把上下文窄化成一两个指标。
         */
        fun strongTermsOf(query: String): Set<String> =
            tokenize(query).filterTo(HashSet()) { it in STRONG_VOCAB }

        /** 记录 → 可检索文本：指标名 + 别名 + 类型键 + 值 + 单位 + 比较符语义 + 日期 + 备注 */
        fun documentText(record: HealthRecord): String {
            val type = SchemaNormalizer.normalizeType(record.type)
            val comparatorText = when (record.comparator) {
                SchemaNormalizer.COMPARATOR_LT -> "低于 小于"
                SchemaNormalizer.COMPARATOR_GT -> "高于 大于"
                else -> ""
            }
            return listOfNotNull(
                HealthTypes.label(type),
                ALIASES_BY_TYPE[type]?.joinToString(" ")?.takeIf { it.isNotBlank() },
                type,
                record.value,
                record.unit.trim().takeIf { it.isNotBlank() },
                comparatorText.takeIf { it.isNotBlank() },
                DateUtils.formatDate(record.recordDate),
                record.notes?.trim()?.takeIf { it.isNotBlank() }
            ).joinToString(" ")
        }

        /**
         * 切词：CJK 按单字 + 相邻双字；拉丁字母 / 数字按连续串小写；其余字符视为分隔。
         * 例："空腹血糖6.2mmol/L" → [空, 腹, 血, 糖, 空腹, 腹血, 血糖, 6, 2, mmol, l]
         */
        fun tokenize(text: String): List<String> {
            val out = ArrayList<String>()
            val cjk = StringBuilder()
            val latin = StringBuilder()
            fun flushLatin() {
                if (latin.isNotEmpty()) {
                    out.add(latin.toString())
                    latin.clear()
                }
            }
            fun flushCjk() {
                if (cjk.isEmpty()) return
                val s = cjk.toString()
                if (s.length == 1) {
                    out.add(s)
                } else {
                    // 单字保召回（别名里有单字词：钾 / 钠 / 钙），双字保精度（指标名主体是双字词）
                    for (ch in s) out.add(ch.toString())
                    for (i in 0 until s.length - 1) out.add(s.substring(i, i + 2))
                }
                cjk.clear()
            }
            for (ch in text.lowercase()) {
                when {
                    isCjk(ch) -> {
                        flushLatin(); cjk.append(ch)
                    }
                    ch.isDigit() -> {
                        flushCjk()
                        // 数字与字母之间也切分："6.2mmol" → 6 | 2 | mmol
                        if (latin.isNotEmpty() && !latin.last().isDigit()) flushLatin()
                        latin.append(ch)
                    }
                    ch.isLetter() -> {
                        flushCjk()
                        if (latin.isNotEmpty() && latin.last().isDigit()) flushLatin()
                        latin.append(ch)
                    }
                    else -> {
                        flushLatin(); flushCjk()
                    }
                }
            }
            flushLatin()
            flushCjk()
            return out
        }

        private fun termFrequency(text: String): Map<String, Int> =
            tokenize(text).groupingBy { it }.eachCount()

        /** CJK 统一表意文字 + 扩展 A + 兼容表意文字（覆盖常用汉字足够） */
        private fun isCjk(ch: Char): Boolean =
            ch.code in 0x4E00..0x9FFF || ch.code in 0x3400..0x4DBF || ch.code in 0xF900..0xFAFF
    }
}

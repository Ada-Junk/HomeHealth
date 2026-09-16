package com.example.homehealth.domain.tool

import com.example.homehealth.data.local.dao.AlertWithMemberName
import com.example.homehealth.data.local.entity.Alert
import com.example.homehealth.data.local.entity.FamilyMember
import com.example.homehealth.data.local.entity.HealthRecord
import com.example.homehealth.domain.repository.AlertRepository
import com.example.homehealth.domain.repository.HealthRecordRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 工具注册表的结构断言。
 *
 * 为什么值得单独测：工具声明写错**不会报任何错**，只会表现为"模型从来不调用这个工具"，
 * 而那时你面对的是一个行为正确的模型和一个静默失效的功能 —— 最难排查的一类问题。
 * 这里把三件事钉死：名称唯一、描述里写明了使用时机、入参 schema 合法且 required 有定义。
 *
 * 与 `SchemaNormalizerTest` 对字典做的结构断言是同一个思路：
 * **配置数据本身也需要测试**，不能只测消费它的代码。
 */
class HealthToolRegistryTest {

    private val registry = HealthToolRegistry(
        searchRecords = SearchRecordsTool(NoopRecords),
        getReferenceRange = GetReferenceRangeTool(),
        getAlerts = GetAlertsTool(NoopAlerts),
        readReportImage = ReadReportImageTool(NoopVision)
    )

    private val member = FamilyMember(id = "m1", name = "妈妈", relationship = "母亲", gender = "female")
    private val textContext = ToolContext(member = member, question = "我有什么问题吗", imageBase64 = null)
    private val imageContext = textContext.copy(imageBase64 = "AAAA")

    @Test
    fun `四个工具的名称唯一且与常量一致`() {
        val names = registry.all.map { it.name }
        assertEquals(names.size, names.toSet().size)
        assertEquals(
            listOf(
                SearchRecordsTool.NAME,
                GetReferenceRangeTool.NAME,
                GetAlertsTool.NAME,
                ReadReportImageTool.NAME
            ),
            names
        )
        assertEquals(4, names.size)
    }

    @Test
    fun `每个工具的描述都写明了使用时机`() {
        // 描述含糊时模型会跳过工具、凭记忆作答 —— 对健康数据来说那是最糟的失败模式，
        // 所以"描述里必须说清什么时候调用"是一条硬要求，不是文案偏好
        registry.all.forEach { tool ->
            assertTrue("${tool.name} 的 description 为空", tool.description.isNotBlank())
            assertTrue(
                "${tool.name} 的 description 没有说明何时调用",
                tool.description.contains("调用") || tool.description.contains("先")
            )
        }
    }

    @Test
    fun `每个工具的入参 schema 是合法 JSON 且 required 字段都有定义`() {
        registry.all.forEach { tool ->
            val schema = JSONObject(tool.parametersJsonSchema)
            assertEquals("${tool.name} 的 schema 根类型必须是 object", "object", schema.getString("type"))
            val properties = schema.getJSONObject("properties")

            // required 里出现一个未定义的字段，供应商会直接 400 —— 而且是每次调用都 400
            val required = schema.optJSONArray("required")
            for (i in 0 until (required?.length() ?: 0)) {
                val name = required!!.getString(i)
                assertNotNull("${tool.name} 的 required 字段 $name 未在 properties 中定义", properties.opt(name))
            }

            // 每个字段都要有 description：模型靠它决定怎么填
            properties.keys().forEach { key ->
                val field = properties.getJSONObject(key)
                assertTrue("${tool.name}.$key 缺少 description", field.optString("description").isNotBlank())
            }
        }
    }

    @Test
    fun `没有附图时不暴露读图工具`() {
        val available = registry.availableFor(textContext).map { it.name }
        assertEquals(3, available.size)
        assertTrue(ReadReportImageTool.NAME !in available)
    }

    @Test
    fun `有附图时四个工具都可用`() {
        val available = registry.availableFor(imageContext).map { it.name }
        assertTrue(ReadReportImageTool.NAME in available)
        assertEquals(4, available.size)
    }

    @Test
    fun `byName 能找到每个工具且未知名返回空`() {
        registry.all.forEach { tool ->
            assertEquals(tool.name, registry.byName(tool.name)?.name)
        }
        assertNull(registry.byName("no_such_tool"))
    }

    // ---------- 测试替身 ----------

    private object NoopRecords : HealthRecordRepository {
        override fun observeRecordsByType(memberId: String, type: String): Flow<List<HealthRecord>> =
            flowOf(emptyList())

        override fun observeAllByMember(memberId: String): Flow<List<HealthRecord>> = flowOf(emptyList())
        override fun observeAllRecords(): Flow<List<HealthRecord>> = flowOf(emptyList())
        override suspend fun getRecentRecords(memberId: String, type: String, limit: Int) = emptyList<HealthRecord>()
        override suspend fun getRecentByMember(memberId: String, limit: Int) = emptyList<HealthRecord>()
        override suspend fun getAllByMember(memberId: String) = emptyList<HealthRecord>()
        override suspend fun getLatest(memberId: String, type: String): HealthRecord? = null
        override suspend fun addRecord(record: HealthRecord) = Unit
        override suspend fun addRecords(records: List<HealthRecord>) = Unit
        override suspend fun updateRecord(record: HealthRecord) = Unit
        override suspend fun deleteRecord(record: HealthRecord) = Unit
        override suspend fun getAllRecords() = emptyList<HealthRecord>()
    }

    private object NoopAlerts : AlertRepository {
        override fun observeAll(): Flow<List<AlertWithMemberName>> = flowOf(emptyList())
        override fun observeUnread(): Flow<List<AlertWithMemberName>> = flowOf(emptyList())
        override fun observeUnreadCount(memberId: String): Flow<Int> = flowOf(0)
        override suspend fun createAlert(alert: Alert) = Unit
        override suspend fun markRead(id: String) = Unit
        override suspend fun markAllRead() = Unit
        override suspend fun deleteAlert(id: String) = Unit
        override suspend fun getByMemberSince(memberId: String, since: Long) = emptyList<Alert>()
        override suspend fun getAllAlerts() = emptyList<Alert>()
    }

    private object NoopVision : VisionReader {
        override suspend fun readImageText(imageBase64: String, focus: String?, question: String) = ""
    }
}

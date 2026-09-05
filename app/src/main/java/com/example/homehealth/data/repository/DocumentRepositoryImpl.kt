package com.example.homehealth.data.repository

import android.content.Context
import android.net.Uri
import com.example.homehealth.data.SettingsPrefs
import com.example.homehealth.data.local.dao.HealthRecordDao
import com.example.homehealth.data.local.dao.MedicalDocumentDao
import com.example.homehealth.data.local.entity.HealthRecord
import com.example.homehealth.data.local.entity.MedicalDocument
import com.example.homehealth.data.local.entity.ParseStatus
import com.example.homehealth.data.remote.LlmClient
import com.example.homehealth.data.remote.LlmProviders
import com.example.homehealth.domain.model.ParseResult
import com.example.homehealth.domain.repository.DocumentRepository
import com.example.homehealth.util.FileUtils
import com.google.gson.Gson
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DocumentRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val medicalDocumentDao: MedicalDocumentDao,
    private val healthRecordDao: HealthRecordDao,
    private val settingsPrefs: SettingsPrefs,
    private val llmClient: LlmClient,
    private val gson: Gson
) : DocumentRepository {

    override fun observeDocuments(memberId: String): Flow<List<MedicalDocument>> =
        medicalDocumentDao.observeByMember(memberId)

    override suspend fun getDocument(id: String): MedicalDocument? = medicalDocumentDao.getById(id)

    override suspend fun getAll(): List<MedicalDocument> = medicalDocumentDao.getAll()

    override suspend fun saveImageAndCreateDocument(uri: Uri, memberId: String): MedicalDocument =
        withContext(Dispatchers.IO) {
            val dir = File(context.filesDir, "documents").apply { mkdirs() }
            val fileName = "doc_${System.currentTimeMillis()}.jpg"
            val dest = File(dir, fileName)
            context.contentResolver.openInputStream(uri)?.use { input ->
                dest.outputStream().use { output -> input.copyTo(output) }
            } ?: throw IOException("无法读取所选图片")
            val document = MedicalDocument(
                id = UUID.randomUUID().toString(),
                memberId = memberId,
                fileName = fileName,
                filePath = dest.absolutePath,
                uploadDate = System.currentTimeMillis(),
                documentType = "lab_report",
                parseStatus = ParseStatus.PROCESSING
            )
            medicalDocumentDao.insert(document)
            document
        }

    override suspend fun parseDocument(
        document: MedicalDocument,
        documentType: String?
    ): ParseResult = withContext(Dispatchers.IO) {
        // 本地模式无法解析报告图片
        if (!LlmProviders.isDirect(settingsPrefs.parseProvider)) {
            throw IllegalStateException(
                "报告解析服务为本地模式，无法解析报告。请在「设置 → 报告解析服务」中选择供应商并配置。"
            )
        }
        if (!llmClient.parseConfigured()) {
            throw IllegalStateException(
                "解析服务未配置 API Key，请在「设置 → 报告解析服务」中填写"
            )
        }
        // LLM 供应商直连（智谱 / OpenAI / Gemini / DeepSeek / Kimi / 通义千问 / Anthropic / 自定义）
        try {
            val base64 = FileUtils.compressImageToBase64(File(document.filePath))
            val result = llmClient.parseHealthDocument(base64)
            if (result.records.isEmpty()) {
                throw IllegalStateException(
                    "未能从报告中识别出健康指标，请拍清晰完整后重试，或手动录入指标"
                )
            }
            result
        } catch (e: IllegalStateException) {
            throw e
        } catch (e: Exception) {
            throw IllegalStateException("解析失败：${e.message}", e)
        }
    }

    override suspend fun confirmRecords(document: MedicalDocument, records: List<HealthRecord>) {
        healthRecordDao.insertAll(records)
        medicalDocumentDao.update(
            document.copy(
                parseStatus = ParseStatus.COMPLETED,
                extractedJson = gson.toJson(records),
                errorMessage = null
            )
        )
    }

    override suspend fun markFailed(document: MedicalDocument, error: String?) {
        medicalDocumentDao.updateStatus(document.id, ParseStatus.FAILED, error)
    }

    override suspend fun markProcessing(document: MedicalDocument) {
        medicalDocumentDao.updateStatus(document.id, ParseStatus.PROCESSING, null)
    }

    override suspend fun resetStuckProcessing() {
        val stuck = medicalDocumentDao.getByStatus(ParseStatus.PROCESSING)
        // 历史记录中本轮尚未开始的解析都属僵尸状态（崩溃/退出中断），统一置为失败供用户重试
        stuck.forEach {
            medicalDocumentDao.updateStatus(
                it.id, ParseStatus.FAILED, "解析被中断（应用退出或内存不足），请点击重试"
            )
        }
    }
}

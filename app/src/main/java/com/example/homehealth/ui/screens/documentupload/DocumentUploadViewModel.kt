package com.example.homehealth.ui.screens.documentupload

import android.content.Context
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.homehealth.R
import com.example.homehealth.data.SettingsPrefs
import com.example.homehealth.data.local.entity.FamilyMember
import com.example.homehealth.data.local.entity.HealthRecord
import com.example.homehealth.data.local.entity.MedicalDocument
import com.example.homehealth.data.remote.LlmProviders
import com.example.homehealth.domain.repository.DocumentRepository
import com.example.homehealth.domain.repository.FamilyRepository
import com.example.homehealth.domain.usecase.DetectAnomaliesUseCase
import com.example.homehealth.util.DateUtils
import com.example.homehealth.util.HealthTypes
import com.example.homehealth.util.SchemaNormalizer
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

/** 可编辑的解析结果条目 */
data class EditableRecord(
    val id: String,
    val type: String,
    val value: String,
    val unit: String,
    val date: String,
    /** 归一化换算说明（如「nmol/L 已换算为 ng/mL」），未换算为 null */
    val normalizationNote: String? = null
)

enum class UploadPhase {
    IDLE, SAVING, PARSING, PARSED, CONFIRMING, DONE, ERROR
}

data class UploadUiState(
    val members: List<FamilyMember> = emptyList(),
    val selectedMemberId: String = "",
    val imagePath: String? = null,
    val currentDocument: MedicalDocument? = null,
    val phase: UploadPhase = UploadPhase.IDLE,
    val editableRecords: List<EditableRecord> = emptyList(),
    val rawText: String = "",
    val errorMessage: String? = null,
    val documents: List<MedicalDocument> = emptyList(),
    /** 解析引擎描述（如「Vision 大模型 glm-4.6v」或「OCR + LLM JSON 模式」） */
    val parseEngine: String = ""
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class DocumentUploadViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    @ApplicationContext private val appContext: Context,
    private val familyRepository: FamilyRepository,
    private val documentRepository: DocumentRepository,
    private val detectAnomalies: DetectAnomaliesUseCase,
    private val settingsPrefs: SettingsPrefs
) : ViewModel() {

    private val initialMemberId: String = savedStateHandle["memberId"] ?: ""

    private val selectedMemberId = MutableStateFlow(initialMemberId)

    private val internal = MutableStateFlow(
        UploadUiState(selectedMemberId = initialMemberId)
    )

    /** 当前拍照目标 URI（供屏幕 TakePicture launcher 使用） */
    var pendingCameraUri: Uri? = null
        private set

    val uiState: StateFlow<UploadUiState> = combine(
        familyRepository.observeMembers(),
        selectedMemberId,
        internal
    ) { members, selectedId, state ->
        val effectiveId = if (members.any { it.id == selectedId }) selectedId
        else members.firstOrNull()?.id ?: ""
        state.copy(members = members, selectedMemberId = effectiveId)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UploadUiState())

    val documents: StateFlow<List<MedicalDocument>> = selectedMemberId
        .flatMapLatest { id -> documentRepository.observeDocuments(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        // 恢复僵尸状态：上次崩溃/退出时卡在 PROCESSING 的文档重置为 FAILED（可重试）
        viewModelScope.launch {
            runCatching { documentRepository.resetStuckProcessing() }
        }
        // 文档列表同步进 uiState（供界面渲染历史）
        viewModelScope.launch {
            documents.collect { list ->
                internal.update { it.copy(documents = list) }
            }
        }
        // 无初始成员时自动选择第一个
        viewModelScope.launch {
            if (initialMemberId.isBlank()) {
                val members = familyRepository.observeMembers().first()
                members.firstOrNull()?.let { selectMember(it.id) }
            }
        }
    }

    fun selectMember(memberId: String) {
        selectedMemberId.value = memberId
        internal.update { it.copy(selectedMemberId = memberId) }
    }

    /** 准备拍照：生成目标文件 URI */
    fun prepareCameraCapture(context: android.content.Context): Uri? {
        val dir = java.io.File(context.filesDir, "documents").apply { mkdirs() }
        val file = java.io.File(dir, "doc_${System.currentTimeMillis()}.jpg")
        val uri = androidx.core.content.FileProvider.getUriForFile(
            context, "${context.packageName}.fileprovider", file
        )
        pendingCameraUri = uri
        return uri
    }

    /** 拍照/选图完成：保存文件、创建文档并开始解析 */
    fun onImageReady(uri: Uri) {
        val memberId = selectedMemberId.value
        if (memberId.isBlank()) return
        viewModelScope.launch {
            internal.update {
                it.copy(
                    phase = UploadPhase.SAVING,
                    errorMessage = null,
                    editableRecords = emptyList()
                )
            }
            try {
                val document = documentRepository.saveImageAndCreateDocument(uri, memberId)
                internal.update {
                    it.copy(
                        currentDocument = document,
                        imagePath = document.filePath,
                        phase = UploadPhase.PARSING,
                        parseEngine = parseEngineText()
                    )
                }
                parseInternal(document)
            } catch (e: Exception) {
                internal.update {
                    it.copy(
                        phase = UploadPhase.ERROR,
                        errorMessage = appContext.getString(R.string.vm_upload_save_failed, e.message ?: "")
                    )
                }
            }
        }
    }

    /** 重试解析失败的文档 */
    fun retryParse(document: MedicalDocument) {
        viewModelScope.launch {
            internal.update {
                it.copy(
                    currentDocument = document,
                    imagePath = document.filePath,
                    phase = UploadPhase.PARSING,
                    errorMessage = null,
                    parseEngine = parseEngineText()
                )
            }
            documentRepository.markProcessing(document)
            parseInternal(document)
        }
    }

    /**
     * 当前解析引擎描述：
     * - 供应商直连 → Vision 大模型（含模型名）+ JSON 结构化提取。
     */
    private fun parseEngineText(): String {
        val provider = settingsPrefs.parseProvider
        return when {
            LlmProviders.isDirect(provider) -> {
                val model = settingsPrefs.parseModel.ifBlank {
                    LlmProviders.byId(provider)?.visionModels?.firstOrNull() ?: ""
                }
                if (model.isBlank()) appContext.getString(R.string.upload_engine_vision_default)
                else appContext.getString(R.string.upload_engine_vision, model)
            }
            else -> ""
        }
    }

    private suspend fun parseInternal(document: MedicalDocument) {
        try {
            val result = documentRepository.parseDocument(document)
            // Schema Normalization：指标别名映射标准字典 + 单位统一/换算
            val editable = result.records.map { r ->
                val n = SchemaNormalizer.normalize(r.type, r.value, r.numeric_value, r.unit)
                EditableRecord(
                    id = UUID.randomUUID().toString(),
                    type = n.type,
                    value = n.value,
                    unit = n.unit.ifBlank { HealthTypes.unit(n.type) },
                    date = r.date ?: DateUtils.formatDate(System.currentTimeMillis()),
                    normalizationNote = n.note
                )
            }
            internal.update {
                it.copy(
                    phase = UploadPhase.PARSED,
                    editableRecords = editable,
                    rawText = result.rawText
                )
            }
        } catch (t: Throwable) {
            // 捕获 Throwable 而非 Exception：OutOfMemoryError 等错误也转为失败态，避免闪退
            val msg = if (t is OutOfMemoryError) {
                appContext.getString(R.string.vm_upload_oom)
            } else {
                t.message ?: appContext.getString(R.string.vm_upload_unknown)
            }
            runCatching { documentRepository.markFailed(document, msg) }
            internal.update {
                it.copy(
                    phase = UploadPhase.ERROR,
                    errorMessage = appContext.getString(R.string.vm_upload_parse_failed, msg)
                )
            }
        }
    }

    fun updateEditableRecord(id: String, transform: (EditableRecord) -> EditableRecord) {
        internal.update { state ->
            state.copy(
                editableRecords = state.editableRecords.map { if (it.id == id) transform(it) else it }
            )
        }
    }

    fun removeEditableRecord(id: String) {
        internal.update { state ->
            state.copy(editableRecords = state.editableRecords.filterNot { it.id == id })
        }
    }

    /** 确认保存：入库 + 异常检测 */
    fun confirmRecords() {
        val document = internal.value.currentDocument ?: return
        val memberId = selectedMemberId.value
        val editable = internal.value.editableRecords
        if (memberId.isBlank() || editable.isEmpty()) return
        viewModelScope.launch {
            internal.update { it.copy(phase = UploadPhase.CONFIRMING) }
            val records = editable.map { e ->
                HealthRecord(
                    id = UUID.randomUUID().toString(),
                    memberId = memberId,
                    type = e.type,
                    value = e.value.trim(),
                    numericValue = e.value.split("/").firstOrNull()?.trim()?.toDoubleOrNull(),
                    unit = e.unit.trim().ifBlank { HealthTypes.unit(e.type) },
                    recordDate = DateUtils.parseDate(e.date) ?: System.currentTimeMillis(),
                    sourceDocumentId = document.id
                )
            }
            documentRepository.confirmRecords(document, records)
            detectAnomalies(memberId)
            internal.update { it.copy(phase = UploadPhase.DONE) }
        }
    }

    fun resetToIdle() {
        internal.update {
            UploadUiState(
                selectedMemberId = selectedMemberId.value,
                documents = it.documents
            )
        }
    }
}

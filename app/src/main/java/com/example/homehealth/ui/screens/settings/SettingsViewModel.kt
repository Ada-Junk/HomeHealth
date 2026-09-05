package com.example.homehealth.ui.screens.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.homehealth.data.SettingsPrefs
import com.example.homehealth.data.local.entity.Alert
import com.example.homehealth.data.local.entity.FamilyMember
import com.example.homehealth.data.local.entity.HealthRecord
import com.example.homehealth.data.local.entity.MedicationReminder
import com.example.homehealth.data.local.entity.MedicalDocument
import com.example.homehealth.data.local.entity.QAHistory
import com.example.homehealth.domain.repository.AlertRepository
import com.example.homehealth.domain.repository.DocumentRepository
import com.example.homehealth.domain.repository.FamilyRepository
import com.example.homehealth.domain.repository.HealthRecordRepository
import com.example.homehealth.domain.repository.MedicationReminderRepository
import com.example.homehealth.domain.repository.QARepository
import com.example.homehealth.worker.DailyCheckWorker
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.google.gson.Gson
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import javax.inject.Inject

/** 导出事件 */
sealed interface SettingsEvent {
    data class ExportReady(val file: File) : SettingsEvent
    data class CheckEnqueued(val message: String) : SettingsEvent
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val familyRepository: FamilyRepository,
    private val healthRecordRepository: HealthRecordRepository,
    private val alertRepository: AlertRepository,
    private val medicationReminderRepository: MedicationReminderRepository,
    private val documentRepository: DocumentRepository,
    private val qaRepository: QARepository,
    private val settingsPrefs: SettingsPrefs,
    private val gson: Gson
) : ViewModel() {

    private val _events = MutableSharedFlow<SettingsEvent>()
    val events: SharedFlow<SettingsEvent> = _events

    val members: StateFlow<List<FamilyMember>> = familyRepository.observeMembers()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // ---- 外观模式 ----
    val themeMode = MutableStateFlow(settingsPrefs.themeMode)

    fun setThemeMode(value: String) {
        settingsPrefs.themeMode = value
        themeMode.value = value
    }

    // ---- 报告解析服务配置 ----
    val parseProvider = MutableStateFlow(settingsPrefs.parseProvider)
    val parseApiKey = MutableStateFlow(settingsPrefs.parseApiKey)
    val parseModel = MutableStateFlow(settingsPrefs.parseModel)
    val parseBaseUrl = MutableStateFlow(settingsPrefs.parseBaseUrl)

    fun setParseProvider(value: String) {
        if (settingsPrefs.parseProvider != value) {
            // 切换供应商后旧模型名不再适用，清空以回退到该供应商的默认模型
            settingsPrefs.parseModel = ""
            parseModel.value = ""
        }
        settingsPrefs.parseProvider = value
        parseProvider.value = value
    }

    fun setParseApiKey(value: String) {
        settingsPrefs.parseApiKey = value
        parseApiKey.value = value
    }

    fun setParseModel(value: String) {
        settingsPrefs.parseModel = value
        parseModel.value = value
    }

    fun setParseBaseUrl(value: String) {
        settingsPrefs.parseBaseUrl = value
        parseBaseUrl.value = value
    }

    // ---- 健康问答服务配置 ----
    val qaProvider = MutableStateFlow(settingsPrefs.qaProvider)
    val qaApiKey = MutableStateFlow(settingsPrefs.qaApiKey)
    val qaModel = MutableStateFlow(settingsPrefs.qaModel)
    val qaBaseUrl = MutableStateFlow(settingsPrefs.qaBaseUrl)

    fun setQaProvider(value: String) {
        if (settingsPrefs.qaProvider != value) {
            // 切换供应商后旧模型名不再适用，清空以回退到该供应商的默认模型
            settingsPrefs.qaModel = ""
            qaModel.value = ""
        }
        settingsPrefs.qaProvider = value
        qaProvider.value = value
    }

    fun setQaApiKey(value: String) {
        settingsPrefs.qaApiKey = value
        qaApiKey.value = value
    }

    fun setQaModel(value: String) {
        settingsPrefs.qaModel = value
        qaModel.value = value
    }

    fun setQaBaseUrl(value: String) {
        settingsPrefs.qaBaseUrl = value
        qaBaseUrl.value = value
    }

    fun upsertMember(member: FamilyMember?) {
        viewModelScope.launch {
            familyRepository.upsertMember(
                member ?: return@launch
            )
        }
    }

    fun addMember(
        name: String,
        relationship: String,
        dob: String?,
        gender: String?,
        heightCm: Double?,
        weightKg: Double?
    ) {
        viewModelScope.launch {
            familyRepository.upsertMember(
                FamilyMember(
                    id = UUID.randomUUID().toString(),
                    name = name,
                    relationship = relationship,
                    dateOfBirth = dob,
                    gender = gender,
                    heightCm = heightCm,
                    weightKg = weightKg
                )
            )
        }
    }

    fun updateMember(
        existing: FamilyMember,
        name: String,
        relationship: String,
        dob: String?,
        gender: String?,
        heightCm: Double?,
        weightKg: Double?
    ) {
        viewModelScope.launch {
            familyRepository.upsertMember(
                existing.copy(
                    name = name,
                    relationship = relationship,
                    dateOfBirth = dob,
                    gender = gender,
                    heightCm = heightCm,
                    weightKg = weightKg
                )
            )
        }
    }

    fun deleteMember(member: FamilyMember) {
        viewModelScope.launch { familyRepository.deleteMember(member) }
    }

    /** 导出全部数据为 JSON 文件 */
    fun exportData() {
        viewModelScope.launch {
            val file = withContext(kotlinx.coroutines.Dispatchers.IO) {
                val members = familyRepository.getMembers()
                val records = healthRecordRepository.getAllRecords()
                val alerts = alertRepository.getAllAlerts()
                val reminders = medicationReminderRepository.getAll()
                val documents = documentRepository.getAll()
                val qaHistory = qaRepository.getAllHistory()

                val data = mapOf(
                    "exported_at" to SimpleDateFormat(
                        "yyyy-MM-dd HH:mm:ss", Locale.CHINA
                    ).format(Date()),
                    "members" to members,
                    "health_records" to records,
                    "alerts" to alerts,
                    "medication_reminders" to reminders,
                    "medical_documents" to documents,
                    "qa_history" to qaHistory
                )
                val dir = File(appContext.cacheDir, "shared").apply { mkdirs() }
                val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.CHINA).format(Date())
                val file = File(dir, "homehealth_export_$stamp.json")
                file.writeText(gson.toJson(data))
                file
            }
            _events.emit(SettingsEvent.ExportReady(file))
        }
    }

    /** 立即执行健康检查（用药提醒 + 异常检测） */
    fun runCheckNow() {
        val request = OneTimeWorkRequestBuilder<DailyCheckWorker>().build()
        WorkManager.getInstance(appContext).enqueue(request)
        viewModelScope.launch {
            _events.emit(SettingsEvent.CheckEnqueued("已提交健康检查任务，结果将以通知形式提醒"))
        }
    }
}

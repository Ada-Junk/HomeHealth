package com.example.homehealth.ui.screens.memberdetail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.homehealth.data.local.entity.FamilyMember
import com.example.homehealth.data.local.entity.HealthRecord
import com.example.homehealth.domain.repository.AlertRepository
import com.example.homehealth.domain.repository.FamilyRepository
import com.example.homehealth.domain.repository.HealthRecordRepository
import com.example.homehealth.domain.usecase.DetectAnomaliesUseCase
import com.example.homehealth.util.DateUtils
import com.example.homehealth.util.HealthTypes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject
import kotlin.math.abs

/** 指标概览项 */
data class MetricSummary(
    val type: String,
    val latest: HealthRecord,
    val previous: HealthRecord?,
    val count: Int
) {
    val delta: Double?
        get() = latest.numericValue?.let { latest ->
            previous?.numericValue?.let { prev -> latest - prev }
        }
}

@HiltViewModel
class MemberDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val familyRepository: FamilyRepository,
    private val healthRecordRepository: HealthRecordRepository,
    alertRepository: AlertRepository,
    private val detectAnomalies: DetectAnomaliesUseCase
) : ViewModel() {

    val memberId: String = checkNotNull(savedStateHandle["memberId"])

    val member: StateFlow<FamilyMember?> = familyRepository.observeMembers()
        .map { list -> list.firstOrNull { it.id == memberId } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val metrics: StateFlow<List<MetricSummary>> = healthRecordRepository
        .observeAllByMember(memberId)
        .map { records ->
            records.groupBy { it.type }.map { (type, list) ->
                val sorted = list.sortedByDescending { it.recordDate }
                MetricSummary(
                    type = type,
                    latest = sorted.first(),
                    previous = sorted.getOrNull(1),
                    count = list.size
                )
            }.sortedBy { HealthTypes.label(it.type) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val recentRecords: StateFlow<List<HealthRecord>> = healthRecordRepository
        .observeAllByMember(memberId)
        .map { it.take(8) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val unreadAlerts: StateFlow<Int> = alertRepository.observeUnreadCount(memberId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    /** 上升视为不良的指标（用于趋势着色） */
    fun higherIsWorse(type: String): Boolean = when (type) {
        HealthTypes.BLOOD_PRESSURE, HealthTypes.BLOOD_GLUCOSE,
        HealthTypes.TOTAL_CHOLESTEROL, HealthTypes.TRIGLYCERIDES,
        HealthTypes.LDL, HealthTypes.HEART_RATE -> true
        else -> false
    }

    /** 手动添加健康记录（档案页「添加指标」）：保存后自动执行异常检测 */
    fun addRecord(
        type: String,
        primary: String,
        secondary: String?,
        dateText: String,
        notes: String?
    ) {
        val value = if (secondary.isNullOrBlank()) primary.trim()
        else "${primary.trim()}/${secondary.trim()}"
        val numeric = value.split("/").firstOrNull()?.trim()?.toDoubleOrNull()
        val date = DateUtils.parseDate(dateText) ?: System.currentTimeMillis()
        viewModelScope.launch {
            healthRecordRepository.addRecord(
                HealthRecord(
                    id = UUID.randomUUID().toString(),
                    memberId = memberId,
                    type = type,
                    value = value,
                    numericValue = numeric,
                    unit = HealthTypes.unit(type),
                    recordDate = date,
                    sourceDocumentId = null,
                    notes = notes?.trim()?.ifBlank { null }
                )
            )
            detectAnomalies(memberId)
        }
    }

    /** 更新成员个人信息（编辑对话框保存） */
    fun updateMember(
        existing: FamilyMember,
        name: String,
        relationship: String,
        dob: String?,
        gender: String?,
        heightCm: Double?,
        weightKg: Double?,
        avatarUrl: String? = null
    ) {
        viewModelScope.launch {
            // 头像被替换或清除时删除旧头像文件
            if (existing.avatarUrl != null && existing.avatarUrl != avatarUrl) {
                runCatching { java.io.File(existing.avatarUrl).delete() }
            }
            familyRepository.upsertMember(
                existing.copy(
                    name = name,
                    relationship = relationship,
                    avatarUrl = avatarUrl,
                    dateOfBirth = dob,
                    gender = gender,
                    heightCm = heightCm,
                    weightKg = weightKg
                )
            )
        }
    }
}

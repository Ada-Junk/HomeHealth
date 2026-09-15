package com.example.homehealth.ui.screens.recorddetail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.homehealth.data.local.entity.HealthRecord
import com.example.homehealth.domain.repository.FamilyRepository
import com.example.homehealth.domain.repository.HealthRecordRepository
import com.example.homehealth.domain.usecase.DetectAnomaliesUseCase
import com.example.homehealth.util.DateUtils
import com.example.homehealth.util.HealthTypes
import com.example.homehealth.util.SchemaNormalizer
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

data class RecordDetailUiState(
    val records: List<HealthRecord> = emptyList(), // 时间倒序
    val chartPoints: List<Pair<Long, Double>> = emptyList(), // 时间升序
    val latest: Double? = null,
    val average: Double? = null,
    val highest: Double? = null,
    val lowest: Double? = null
)

@HiltViewModel
class RecordDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val healthRecordRepository: HealthRecordRepository,
    private val familyRepository: FamilyRepository,
    private val detectAnomalies: DetectAnomaliesUseCase
) : ViewModel() {

    val memberId: String = checkNotNull(savedStateHandle["memberId"])
    val type: String = checkNotNull(savedStateHandle["type"])

    /** 成员性别：参考范围展示需按性别取（血红蛋白 / 肌酐 / 尿酸等男女区间不同） */
    val memberGender: StateFlow<String?> = familyRepository.observeMembers()
        .map { members -> members.firstOrNull { it.id == memberId }?.gender }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val uiState: StateFlow<RecordDetailUiState> = healthRecordRepository
        .observeRecordsByType(memberId, type)
        .map { records ->
            val nums = records.mapNotNull { it.numericValue }
            RecordDetailUiState(
                records = records,
                chartPoints = records
                    .filter { it.numericValue != null }
                    .sortedBy { it.recordDate }
                    .map { it.recordDate to it.numericValue!! },
                latest = nums.firstOrNull(),
                average = nums.takeIf { it.isNotEmpty() }?.average(),
                highest = nums.maxOrNull(),
                lowest = nums.minOrNull()
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), RecordDetailUiState())

    /** 手动添加记录：value 形如 "120/80"（血压）或 "5.4"；保存后自动执行异常检测 */
    fun addRecord(primary: String, secondary: String?, dateText: String, notes: String?) {
        val value = if (secondary.isNullOrBlank()) primary.trim()
        else "${primary.trim()}/${secondary.trim()}"
        // 支持手动输入区间型结果（"<0.1" / ">100"）
        val (comparator, numeric) = SchemaNormalizer.parseComparator(
            value.split("/").firstOrNull()?.trim().orEmpty()
        )
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
                    notes = notes?.trim()?.ifBlank { null },
                    comparator = comparator
                )
            )
            detectAnomalies(memberId)
        }
    }

    fun deleteRecord(record: HealthRecord) {
        viewModelScope.launch {
            healthRecordRepository.deleteRecord(record)
            detectAnomalies(memberId)
        }
    }

    /** 编辑已有记录：保留 id / 来源，更新数值与日期备注；保存后自动执行异常检测 */
    fun updateRecord(
        record: HealthRecord,
        primary: String,
        secondary: String?,
        dateText: String,
        notes: String?
    ) {
        val value = if (secondary.isNullOrBlank()) primary.trim()
        else "${primary.trim()}/${secondary.trim()}"
        val (comparator, numeric) = SchemaNormalizer.parseComparator(
            value.split("/").firstOrNull()?.trim().orEmpty()
        )
        val date = DateUtils.parseDate(dateText) ?: record.recordDate
        viewModelScope.launch {
            healthRecordRepository.updateRecord(
                record.copy(
                    value = value,
                    numericValue = numeric,
                    unit = record.unit.ifBlank { HealthTypes.unit(record.type) },
                    recordDate = date,
                    notes = notes?.trim()?.ifBlank { null },
                    comparator = comparator
                )
            )
            detectAnomalies(memberId)
        }
    }
}

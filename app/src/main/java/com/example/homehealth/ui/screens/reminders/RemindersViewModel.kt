package com.example.homehealth.ui.screens.reminders

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.homehealth.data.local.dao.ReminderWithMemberName
import com.example.homehealth.data.local.entity.FamilyMember
import com.example.homehealth.data.local.entity.MedicationReminder
import com.example.homehealth.domain.repository.FamilyRepository
import com.example.homehealth.domain.repository.MedicationReminderRepository
import com.example.homehealth.util.CalendarEventHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID
import javax.inject.Inject

data class RemindersUiState(
    val reminders: List<ReminderWithMemberName> = emptyList(),
    val members: List<FamilyMember> = emptyList()
)

@HiltViewModel
class RemindersViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val medicationReminderRepository: MedicationReminderRepository,
    private val familyRepository: FamilyRepository
) : ViewModel() {

    val uiState: StateFlow<RemindersUiState> = combine(
        medicationReminderRepository.observeAll(),
        familyRepository.observeMembers()
    ) { reminders, members ->
        RemindersUiState(reminders = reminders, members = members)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), RemindersUiState())

    fun saveReminder(
        existing: MedicationReminder?,
        memberId: String,
        medicationName: String,
        dosage: String,
        times: List<String>
    ) {
        viewModelScope.launch {
            medicationReminderRepository.upsert(
                existing?.copy(
                    memberId = memberId,
                    medicationName = medicationName,
                    dosage = dosage,
                    schedule = MedicationReminder.buildSchedule(times)
                ) ?: MedicationReminder(
                    id = UUID.randomUUID().toString(),
                    memberId = memberId,
                    medicationName = medicationName,
                    dosage = dosage,
                    schedule = MedicationReminder.buildSchedule(times),
                    startDate = System.currentTimeMillis()
                )
            )
        }
    }

    /** 删除提醒：已写入系统日历的事件一并删除（按事件 ID + 签名兜底；失败不阻断本地删除） */
    fun deleteReminder(item: ReminderWithMemberName) {
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    CalendarEventHelper.deleteMedicationEvents(
                        context = appContext,
                        medicationName = item.reminder.medicationName,
                        memberName = item.memberName,
                        storedEventIds = item.reminder.calendarEventIdList()
                    )
                }
            }
            medicationReminderRepository.delete(item.reminder)
        }
    }

    /** 记录提醒已写入日历的事件 ID（再次写入时先清理旧事件） */
    fun updateCalendarEventIds(reminder: MedicationReminder, eventIds: List<Long>) {
        viewModelScope.launch {
            medicationReminderRepository.upsert(
                reminder.copy(
                    calendarEventIds = MedicationReminder.buildCalendarEventIds(eventIds)
                )
            )
        }
    }

    fun toggleActive(reminder: MedicationReminder) {
        viewModelScope.launch {
            medicationReminderRepository.upsert(reminder.copy(active = !reminder.active))
        }
    }
}

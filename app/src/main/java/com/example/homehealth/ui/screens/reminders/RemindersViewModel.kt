package com.example.homehealth.ui.screens.reminders

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.homehealth.data.local.dao.ReminderWithMemberName
import com.example.homehealth.data.local.entity.FamilyMember
import com.example.homehealth.data.local.entity.MedicationReminder
import com.example.homehealth.domain.repository.FamilyRepository
import com.example.homehealth.domain.repository.MedicationReminderRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

data class RemindersUiState(
    val reminders: List<ReminderWithMemberName> = emptyList(),
    val members: List<FamilyMember> = emptyList()
)

@HiltViewModel
class RemindersViewModel @Inject constructor(
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

    fun deleteReminder(reminder: MedicationReminder) {
        viewModelScope.launch { medicationReminderRepository.delete(reminder) }
    }

    fun toggleActive(reminder: MedicationReminder) {
        viewModelScope.launch {
            medicationReminderRepository.upsert(reminder.copy(active = !reminder.active))
        }
    }
}

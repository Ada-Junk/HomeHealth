package com.example.homehealth.ui.screens.alerts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.homehealth.data.local.dao.AlertWithMemberName
import com.example.homehealth.domain.repository.AlertRepository
import com.example.homehealth.domain.usecase.DetectAnomaliesUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class AlertFilter { ALL, UNREAD }

data class AlertsUiState(
    val filter: AlertFilter = AlertFilter.ALL,
    val alerts: List<AlertWithMemberName> = emptyList(),
    val unreadCount: Int = 0,
    val detecting: Boolean = false
)

@HiltViewModel
class AlertsViewModel @Inject constructor(
    private val alertRepository: AlertRepository,
    private val detectAnomalies: DetectAnomaliesUseCase
) : ViewModel() {

    private val filter = MutableStateFlow(AlertFilter.ALL)
    private val detecting = MutableStateFlow(false)

    val uiState: StateFlow<AlertsUiState> = combine(
        filter,
        detecting,
        alertRepository.observeAll()
    ) { f, isDetecting, alerts ->
        AlertsUiState(
            filter = f,
            alerts = if (f == AlertFilter.UNREAD) alerts.filter { !it.alert.isRead } else alerts,
            unreadCount = alerts.count { !it.alert.isRead },
            detecting = isDetecting
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AlertsUiState())

    fun setFilter(newFilter: AlertFilter) {
        filter.value = newFilter
    }

    fun markRead(id: String) {
        viewModelScope.launch { alertRepository.markRead(id) }
    }

    fun markAllRead() {
        viewModelScope.launch { alertRepository.markAllRead() }
    }

    /** 删除单条预警（长按触发） */
    fun deleteAlert(id: String) {
        viewModelScope.launch { alertRepository.deleteAlert(id) }
    }

    /** 进入预警中心时静默执行一次检测（不显示进度条），保证预警与最新数据同步 */
    fun refreshOnEnter() {
        viewModelScope.launch {
            runCatching { detectAnomalies.invokeAll() }
        }
    }

    /** 立即执行异常检测 */
    fun runDetection(onResult: (Int) -> Unit) {
        if (detecting.value) return
        viewModelScope.launch {
            detecting.value = true
            try {
                val created = detectAnomalies.invokeAll()
                onResult(created)
            } finally {
                detecting.value = false
            }
        }
    }
}

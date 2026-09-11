package com.example.homehealth.ui.screens.qa

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.homehealth.R
import com.example.homehealth.data.local.entity.FamilyMember
import com.example.homehealth.data.local.entity.QAHistory
import com.example.homehealth.domain.repository.FamilyRepository
import com.example.homehealth.domain.repository.QARepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

data class QAUiState(
    val members: List<FamilyMember> = emptyList(),
    val selectedMemberId: String = "",
    val history: List<QAHistory> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null,
    /** 已发送、正在等待回答的问题（立即上屏，不等 LLM 返回） */
    val pendingQuestion: String? = null
)

/** 提问过程中的临时状态（合并为一个 Flow，避免 combine 超过 5 个流） */
private data class AskState(
    val loading: Boolean = false,
    val error: String? = null,
    val pendingQuestion: String? = null
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class QAViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val qaRepository: QARepository,
    private val settingsPrefs: com.example.homehealth.data.SettingsPrefs,
    familyRepository: FamilyRepository
) : ViewModel() {

    // 启动时恢复上次咨询的成员，重启后无需再点击即显示历史对话
    private val selectedMemberId = MutableStateFlow(settingsPrefs.qaMemberId)
    private val askState = MutableStateFlow(AskState())

    init {
        // 成员列表就绪后，若当前选择无效（首次启动 / 成员已删除），自动选中第一个成员
        viewModelScope.launch {
            familyRepository.observeMembers()
                .filter { it.isNotEmpty() }
                .map { it.map { m -> m.id } }
                .distinctUntilChanged()
                .collect { ids ->
                    if (selectedMemberId.value !in ids) {
                        selectMember(ids.first())
                    }
                }
        }
    }

    private val historyFlow = selectedMemberId.flatMapLatest { id ->
        if (id.isBlank()) flowOf(emptyList()) else qaRepository.observeHistory(id)
    }

    val uiState: StateFlow<QAUiState> = combine(
        familyRepository.observeMembers(),
        selectedMemberId,
        historyFlow,
        askState
    ) { members, selectedId, history, asking ->
        val effectiveId = if (members.any { it.id == selectedId }) selectedId
        else members.firstOrNull()?.id ?: ""
        QAUiState(
            members = members,
            selectedMemberId = effectiveId,
            history = history,
            loading = asking.loading,
            error = asking.error,
            pendingQuestion = asking.pendingQuestion
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), QAUiState())

    fun selectMember(memberId: String) {
        settingsPrefs.qaMemberId = memberId // 持久化，重启后直接选中
        selectedMemberId.value = memberId
    }

    /** @return 是否成功发起提问（失败时调用方应保留输入内容） */
    fun ask(question: String): Boolean {
        val q = question.trim()
        if (q.isEmpty() || askState.value.loading) return false
        val memberId = uiState.value.selectedMemberId
        val member = uiState.value.members.firstOrNull { it.id == memberId } ?: return false
        askState.value = AskState(loading = true, pendingQuestion = q)
        viewModelScope.launch {
            try {
                qaRepository.ask(member, q)
            } catch (e: Exception) {
                askState.value = askState.value.copy(
                    error = appContext.getString(R.string.vm_qa_failed, e.message ?: "")
                )
            } finally {
                // 等 Room 历史流推送出本次问答后再清空 pending，避免问题气泡闪烁消失
                withTimeoutOrNull(2000) {
                    historyFlow.first { list -> list.any { it.question == q } }
                }
                askState.value = askState.value.copy(loading = false, pendingQuestion = null)
            }
        }
        return true
    }
}

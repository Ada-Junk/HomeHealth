package com.example.homehealth.ui.screens.qa

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.homehealth.data.local.entity.FamilyMember
import com.example.homehealth.data.local.entity.QAHistory
import com.example.homehealth.domain.repository.FamilyRepository
import com.example.homehealth.domain.repository.QARepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
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
    private val qaRepository: QARepository,
    familyRepository: FamilyRepository
) : ViewModel() {

    private val selectedMemberId = MutableStateFlow("")
    private val askState = MutableStateFlow(AskState())

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
                askState.value = askState.value.copy(error = "回答生成失败：${e.message}")
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

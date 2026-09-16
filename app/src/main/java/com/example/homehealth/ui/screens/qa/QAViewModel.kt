package com.example.homehealth.ui.screens.qa

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.homehealth.R
import com.example.homehealth.data.local.entity.FamilyMember
import com.example.homehealth.data.local.entity.QAHistory
import com.example.homehealth.domain.repository.FamilyRepository
import com.example.homehealth.domain.repository.QARepository
import com.example.homehealth.domain.repository.QaStreamEvent
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
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

/** Agent 路径的一次工具调用（界面折叠展示；[done] 为 false 表示还在执行中） */
data class QaToolStep(val name: String, val detail: String, val ok: Boolean, val done: Boolean)

data class QAUiState(
    val members: List<FamilyMember> = emptyList(),
    val selectedMemberId: String = "",
    val history: List<QAHistory> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null,
    /** 已发送、正在等待回答的问题（立即上屏，不等 LLM 返回） */
    val pendingQuestion: String? = null,
    /** 已选中但尚未发送的附图（发送后清空） */
    val pendingImagePath: String? = null,
    /** 本轮提问已发送的附图（流式气泡要显示缩略图，否则提问气泡的图会「跳」一下） */
    val streamingImagePath: String? = null,
    /** 正在流式生成的回答正文（非空时界面用流式气泡代替加载动画） */
    val streamingAnswer: String = "",
    /** 正在流式生成的思考过程（深度思考模型） */
    val streamingThinking: String = "",
    /** 本次回答的数据依据（本地检索产物或工具返回，先于回答就绪） */
    val streamingReferences: String = "",
    /** Agent 路径的工具调用轨迹 */
    val streamingTools: List<QaToolStep> = emptyList()
) {
    /** 是否已有可展示的流式内容（依据 / 正文 / 思考 / 工具轨迹任一就绪即算） */
    val hasStreaming: Boolean
        get() = streamingReferences.isNotEmpty() ||
            streamingAnswer.isNotEmpty() ||
            streamingThinking.isNotEmpty() ||
            streamingTools.isNotEmpty()
}

/** 提问过程中的临时状态（合并为一个 Flow，避免 combine 超过 5 个流） */
private data class AskState(
    val loading: Boolean = false,
    val error: String? = null,
    val pendingQuestion: String? = null,
    /** 本轮提问已提交的附图路径 */
    val imagePath: String? = null,
    val answer: String = "",
    val thinking: String = "",
    val references: String = "",
    val tools: List<QaToolStep> = emptyList()
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
    private val pendingImagePath = MutableStateFlow<String?>(null)

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
        // 顺手清理孤儿附图（选了图却直接离开、提问失败、历史被清空等情况）。
        // 与文档上传页的 cleanupEmptyImages 同一套「进页面顺手清一次」的做法：
        // 不追求实时，但保证不会无限累积。
        viewModelScope.launch {
            runCatching { qaRepository.cleanupOrphanAttachments() }
        }
    }

    private val historyFlow = selectedMemberId.flatMapLatest { id ->
        if (id.isBlank()) flowOf(emptyList()) else qaRepository.observeHistory(id)
    }

    val uiState: StateFlow<QAUiState> = combine(
        familyRepository.observeMembers(),
        selectedMemberId,
        historyFlow,
        askState,
        pendingImagePath
    ) { members, selectedId, history, asking, pendingImage ->
        val effectiveId = if (members.any { it.id == selectedId }) selectedId
        else members.firstOrNull()?.id ?: ""
        QAUiState(
            members = members,
            selectedMemberId = effectiveId,
            history = history,
            loading = asking.loading,
            error = asking.error,
            pendingQuestion = asking.pendingQuestion,
            pendingImagePath = pendingImage,
            streamingImagePath = asking.imagePath,
            streamingAnswer = asking.answer,
            streamingThinking = asking.thinking,
            streamingReferences = asking.references,
            streamingTools = asking.tools
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), QAUiState())

    fun selectMember(memberId: String) {
        settingsPrefs.qaMemberId = memberId // 持久化，重启后直接选中
        selectedMemberId.value = memberId
    }

    /** 选中一张附图。落盘后再记状态，失败时给出可读错误而不是静默无反应 */
    fun attachImage(uri: Uri) {
        val previous = pendingImagePath.value
        viewModelScope.launch {
            try {
                val path = qaRepository.saveAttachment(uri)
                pendingImagePath.value = path
                // 换图时删掉上一张：否则用户每换一次，私有目录里就多留一张
                previous?.takeIf { it != path }?.let { qaRepository.discardAttachment(it) }
                askState.value = askState.value.copy(error = null)
            } catch (e: Exception) {
                askState.value = askState.value.copy(
                    error = appContext.getString(R.string.vm_qa_attach_failed, e.message ?: "")
                )
            }
        }
    }

    /** 移除尚未发送的附图 */
    fun removeAttachment() {
        val path = pendingImagePath.value ?: return
        pendingImagePath.value = null
        viewModelScope.launch { qaRepository.discardAttachment(path) }
    }

    /** @return 是否成功发起提问（失败时调用方应保留输入内容） */
    fun ask(question: String): Boolean {
        val q = question.trim()
        // 允许「只发图不提问」：图里往往就是要问的东西，强制先打字是多余的门槛
        val imagePath = pendingImagePath.value
        if (askState.value.loading) return false
        if (q.isEmpty() && imagePath == null) return false
        val memberId = uiState.value.selectedMemberId
        val member = uiState.value.members.firstOrNull { it.id == memberId } ?: return false

        // 附件被本轮消费：从「待发送」挪进「本轮已提交」，流式气泡据此显示缩略图
        pendingImagePath.value = null
        askState.value = AskState(loading = true, pendingQuestion = q, imagePath = imagePath)

        viewModelScope.launch {
            try {
                qaRepository.askStream(member, q, imagePath).collect { event ->
                    when (event) {
                        // 依据最先到达：界面可以在等待模型时先把"回答基于什么"摆出来
                        is QaStreamEvent.References ->
                            askState.value = askState.value.copy(references = event.text)

                        is QaStreamEvent.Answer ->
                            askState.value = askState.value.copy(
                                answer = askState.value.answer + event.delta
                            )

                        is QaStreamEvent.Thinking ->
                            askState.value = askState.value.copy(
                                thinking = askState.value.thinking + event.delta
                            )

                        // 工具轨迹：先追加一条「执行中」，结果回来再就地更新那一条
                        is QaStreamEvent.ToolCall ->
                            askState.value = askState.value.copy(
                                tools = askState.value.tools + QaToolStep(
                                    name = event.name,
                                    detail = event.argsSummary,
                                    ok = true,
                                    done = false
                                )
                            )

                        is QaStreamEvent.ToolResult -> {
                            val steps = askState.value.tools.toMutableList()
                            val index = steps.indexOfLast { it.name == event.name && !it.done }
                            if (index >= 0) {
                                steps[index] = steps[index].copy(
                                    detail = event.summary,
                                    ok = event.ok,
                                    done = true
                                )
                                askState.value = askState.value.copy(tools = steps)
                            }
                        }

                        // 已落库：真正的收尾在 finally 里等 Room 历史流，避免中途替换气泡
                        is QaStreamEvent.Finished -> Unit
                    }
                }
            } catch (e: CancellationException) {
                // 离开页面导致的取消不是错误，直接向上抛给协程
                throw e
            } catch (e: Exception) {
                askState.value = askState.value.copy(
                    error = appContext.getString(R.string.vm_qa_failed, e.message ?: "")
                )
            } finally {
                // 等 Room 历史流推送出本次问答后再清空流式状态，避免回答气泡闪烁消失
                withTimeoutOrNull(2000) {
                    historyFlow.first { list -> list.any { it.question == q } }
                }
                // 只清流式与加载状态，保留 error —— 否则刚设置的错误提示会被这一步抹掉
                askState.value = askState.value.copy(
                    loading = false,
                    pendingQuestion = null,
                    imagePath = null,
                    answer = "",
                    thinking = "",
                    references = "",
                    tools = emptyList()
                )
            }
        }
        return true
    }
}

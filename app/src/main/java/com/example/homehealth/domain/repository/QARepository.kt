package com.example.homehealth.domain.repository

import android.net.Uri
import com.example.homehealth.data.local.entity.FamilyMember
import com.example.homehealth.data.local.entity.QAHistory
import kotlinx.coroutines.flow.Flow

/**
 * 一次提问向下游推送的事件。
 *
 * 之所以用事件流而不是「返回一个字符串」，是因为流式回答的过程中界面要表现多种不同状态
 * （依据就绪 / 思考中 / 工具调用 / 正文生成中），最后还要把落库后的完整条目交给列表去替换临时气泡。
 */
sealed interface QaStreamEvent {

    /**
     * 「数据依据」块 —— **先于答案下发**。
     *
     * 顺序是刻意的：先把依据摆出来，模型结论到达时它的可信度就已经确定；
     * 反过来（先给结论再补依据）用户会先形成判断再看到出处，这是问答类产品最容易失去信任的地方。
     * 依据来自本地检索、不依赖网络，所以它总是最先就绪的。
     */
    data class References(val text: String) : QaStreamEvent

    /** 正文增量（**非累计**，调用方自行拼接） */
    data class Answer(val delta: String) : QaStreamEvent

    /** 思考过程增量（深度思考模型才有） */
    data class Thinking(val delta: String) : QaStreamEvent

    /**
     * Agent 路径：正在调用某个工具。
     * 前端把它渲染成可折叠的调用轨迹 —— 让用户看到"回答是怎么查出来的"，
     * 而不只是看到结论。
     */
    data class ToolCall(val name: String, val argsSummary: String) : QaStreamEvent

    /** Agent 路径：工具返回（[summary] 是短摘要，完整结果只回填给模型） */
    data class ToolResult(val name: String, val ok: Boolean, val summary: String) : QaStreamEvent

    /** 回答已落库。Room 历史流随后会推送同一条目（含依据与来源），界面据此替换流式气泡 */
    data class Finished(val history: QAHistory) : QaStreamEvent
}

/** 健康问答仓库（远程模型流式优先，未配置或调用失败时回退本地规则引擎） */
interface QARepository {
    fun observeHistory(memberId: String): Flow<List<QAHistory>>

    /**
     * 流式提问：先推 [QaStreamEvent.References]（数据依据），再增量推正文与思考过程，
     * 最后推 [QaStreamEvent.Finished]（已落库的完整条目）。
     *
     * @param imagePath 本轮提问附带的报告图片（[saveAttachment] 返回的路径，可为空）。
     *   影像 / 病理这类叙述性报告没有对应的结构化指标，只能以图片提问。
     *   冷流：每次 collect 都会真实发起一次请求，不要重复收集。
     */
    fun askStream(member: FamilyMember, question: String, imagePath: String? = null): Flow<QaStreamEvent>

    suspend fun getAllHistory(): List<QAHistory>
    suspend fun clearHistory(memberId: String)

    // ---------- 附件的文件生命周期 ----------

    /**
     * 把用户选中的图片复制进应用私有目录（`filesDir/qa_images`），返回落盘路径。
     *
     * 落盘而非只留在内存：进程被杀后已选中的附件不该丢失；同时也让
     * [askStream] 只依赖一个路径，无需在界面层持有 base64 大字符串。
     */
    suspend fun saveAttachment(uri: Uri): String

    /** 丢弃尚未随提问提交的附图（用户移除附件时调用） */
    suspend fun discardAttachment(path: String)

    /**
     * 清理孤儿附图：`qa_images/` 下不被任何问答历史引用、且已过静置期的文件。
     *
     * 会过期的情况：用户选了图却直接离开页面、提问失败、或历史被清空。
     * 保留静置期是为了不误删「刚选中、正准备发送」的文件。启动 / 进入问答页时调用一次即可。
     */
    suspend fun cleanupOrphanAttachments()
}

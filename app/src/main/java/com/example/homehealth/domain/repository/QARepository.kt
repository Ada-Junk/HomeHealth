package com.example.homehealth.domain.repository

import com.example.homehealth.data.local.entity.FamilyMember
import com.example.homehealth.data.local.entity.QAHistory
import kotlinx.coroutines.flow.Flow

/** 健康问答仓库（远程 RAG 优先，回退本地规则引擎） */
interface QARepository {
    fun observeHistory(memberId: String): Flow<List<QAHistory>>
    suspend fun ask(member: FamilyMember, question: String): QAHistory
    suspend fun getAllHistory(): List<QAHistory>
    suspend fun clearHistory(memberId: String)
}

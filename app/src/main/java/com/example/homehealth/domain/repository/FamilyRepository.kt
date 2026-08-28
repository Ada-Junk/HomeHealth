package com.example.homehealth.domain.repository

import com.example.homehealth.data.local.entity.FamilyMember
import kotlinx.coroutines.flow.Flow

/** 家庭成员仓库 */
interface FamilyRepository {
    fun observeMembers(): Flow<List<FamilyMember>>
    suspend fun getMember(id: String): FamilyMember?
    suspend fun getMembers(): List<FamilyMember>
    suspend fun upsertMember(member: FamilyMember)
    suspend fun deleteMember(member: FamilyMember)
}

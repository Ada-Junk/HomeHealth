package com.example.homehealth.data.repository

import com.example.homehealth.data.local.dao.FamilyMemberDao
import com.example.homehealth.data.local.dao.AlertDao
import com.example.homehealth.data.local.dao.MedicalDocumentDao
import com.example.homehealth.data.local.dao.MedicationReminderDao
import com.example.homehealth.data.local.dao.QAHistoryDao
import com.example.homehealth.data.local.entity.FamilyMember
import com.example.homehealth.domain.repository.FamilyRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FamilyRepositoryImpl @Inject constructor(
    private val familyMemberDao: FamilyMemberDao,
    private val alertDao: AlertDao,
    private val medicationReminderDao: MedicationReminderDao,
    private val medicalDocumentDao: MedicalDocumentDao,
    private val qaHistoryDao: QAHistoryDao
) : FamilyRepository {

    override fun observeMembers(): Flow<List<FamilyMember>> = familyMemberDao.observeAll()

    override suspend fun getMember(id: String): FamilyMember? = familyMemberDao.getById(id)

    override suspend fun getMembers(): List<FamilyMember> = familyMemberDao.getAll()

    override suspend fun upsertMember(member: FamilyMember) = familyMemberDao.upsert(member)

    override suspend fun deleteMember(member: FamilyMember) {
        // health_records 通过外键级联删除，其余表手动清理
        familyMemberDao.delete(member)
        alertDao.deleteByMember(member.id)
        medicationReminderDao.deleteByMember(member.id)
        medicalDocumentDao.deleteByMember(member.id)
        qaHistoryDao.deleteByMember(member.id)
    }
}

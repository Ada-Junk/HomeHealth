package com.example.homehealth.domain.usecase

import com.example.homehealth.data.local.entity.HealthRecord
import com.example.homehealth.domain.repository.HealthRecordRepository
import javax.inject.Inject

/** 获取成员健康记录（可按类型过滤） */
class GetMemberHealthRecordsUseCase @Inject constructor(
    private val healthRecordRepository: HealthRecordRepository
) {
    suspend operator fun invoke(memberId: String, type: String? = null, limit: Int = 100): List<HealthRecord> =
        if (type == null) {
            healthRecordRepository.getRecentByMember(memberId, limit)
        } else {
            healthRecordRepository.getRecentRecords(memberId, type, limit)
        }
}

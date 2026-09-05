package com.example.homehealth.data.repository

import com.example.homehealth.data.local.dao.AlertDao
import com.example.homehealth.data.local.dao.AlertWithMemberName
import com.example.homehealth.data.local.entity.Alert
import com.example.homehealth.domain.repository.AlertRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AlertRepositoryImpl @Inject constructor(
    private val alertDao: AlertDao
) : AlertRepository {

    override fun observeAll(): Flow<List<AlertWithMemberName>> = alertDao.observeAll()

    override fun observeUnread(): Flow<List<AlertWithMemberName>> = alertDao.observeUnread()

    override fun observeUnreadCount(memberId: String): Flow<Int> =
        alertDao.observeUnreadCount(memberId)

    override suspend fun createAlert(alert: Alert) = alertDao.insert(alert)

    override suspend fun markRead(id: String) = alertDao.markRead(id)

    override suspend fun markAllRead() = alertDao.markAllRead()

    override suspend fun deleteAlert(id: String) = alertDao.deleteById(id)

    override suspend fun getUnreadByMember(memberId: String): List<Alert> =
        alertDao.getUnreadByMember(memberId)

    override suspend fun getAllAlerts(): List<Alert> = alertDao.getAll()
}

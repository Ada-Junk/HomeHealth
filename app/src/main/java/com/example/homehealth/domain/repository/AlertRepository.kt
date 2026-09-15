package com.example.homehealth.domain.repository

import com.example.homehealth.data.local.dao.AlertWithMemberName
import com.example.homehealth.data.local.entity.Alert
import kotlinx.coroutines.flow.Flow

/** 健康预警仓库 */
interface AlertRepository {
    fun observeAll(): Flow<List<AlertWithMemberName>>
    fun observeUnread(): Flow<List<AlertWithMemberName>>
    fun observeUnreadCount(memberId: String): Flow<Int>
    suspend fun createAlert(alert: Alert)
    suspend fun markRead(id: String)
    suspend fun markAllRead()
    suspend fun deleteAlert(id: String)
    suspend fun getByMemberSince(memberId: String, since: Long): List<Alert>
    suspend fun getAllAlerts(): List<Alert>
}

package com.example.homehealth.di

import com.example.homehealth.data.repository.AlertRepositoryImpl
import com.example.homehealth.data.repository.DocumentRepositoryImpl
import com.example.homehealth.data.repository.FamilyRepositoryImpl
import com.example.homehealth.data.repository.HealthRecordRepositoryImpl
import com.example.homehealth.data.repository.LlmCallLogRepositoryImpl
import com.example.homehealth.data.repository.MedicationReminderRepositoryImpl
import com.example.homehealth.data.repository.QARepositoryImpl
import com.example.homehealth.data.remote.AgentLlmGateway
import com.example.homehealth.data.remote.LlmClient
import com.example.homehealth.domain.repository.AlertRepository
import com.example.homehealth.domain.repository.DocumentRepository
import com.example.homehealth.domain.repository.FamilyRepository
import com.example.homehealth.domain.repository.HealthRecordRepository
import com.example.homehealth.domain.repository.LlmCallLogRepository
import com.example.homehealth.domain.repository.MedicationReminderRepository
import com.example.homehealth.domain.repository.QARepository
import com.example.homehealth.domain.tool.HealthToolRegistry
import com.example.homehealth.domain.tool.ToolProvider
import com.example.homehealth.domain.tool.VisionReader
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindFamilyRepository(impl: FamilyRepositoryImpl): FamilyRepository

    @Binds
    @Singleton
    abstract fun bindHealthRecordRepository(impl: HealthRecordRepositoryImpl): HealthRecordRepository

    @Binds
    @Singleton
    abstract fun bindDocumentRepository(impl: DocumentRepositoryImpl): DocumentRepository

    @Binds
    @Singleton
    abstract fun bindAlertRepository(impl: AlertRepositoryImpl): AlertRepository

    @Binds
    @Singleton
    abstract fun bindMedicationReminderRepository(impl: MedicationReminderRepositoryImpl): MedicationReminderRepository

    @Binds
    @Singleton
    abstract fun bindQARepository(impl: QARepositoryImpl): QARepository

    @Binds
    @Singleton
    abstract fun bindLlmCallLogRepository(impl: LlmCallLogRepositoryImpl): LlmCallLogRepository

    /**
     * 读图能力绑定到 [LlmClient]（它同时是 Agent 网关与视觉调用的实现）。
     * 抽接口是为了让 `ReadReportImageTool` 依赖一个窄接口而不是整个 LLM 客户端 ——
     * 否则这个工具在 JVM 单测里根本构造不出来（要凑齐 OkHttpClient / SettingsPrefs / …）。
     */
    @Binds
    @Singleton
    abstract fun bindVisionReader(impl: LlmClient): VisionReader

    /** Agent 网关同样绑定到 [LlmClient]：`ReActAgent` 依赖接口，才能用假网关测循环策略 */
    @Binds
    @Singleton
    abstract fun bindAgentLlmGateway(impl: LlmClient): AgentLlmGateway

    /** 工具集契约绑定到真实注册表；`ReActAgent` 依赖 [ToolProvider] 接口，便于单测注入假工具 */
    @Binds
    @Singleton
    abstract fun bindToolProvider(impl: HealthToolRegistry): ToolProvider
}

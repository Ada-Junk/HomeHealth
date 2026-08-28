package com.example.homehealth.di

import com.example.homehealth.data.repository.AlertRepositoryImpl
import com.example.homehealth.data.repository.DocumentRepositoryImpl
import com.example.homehealth.data.repository.FamilyRepositoryImpl
import com.example.homehealth.data.repository.HealthRecordRepositoryImpl
import com.example.homehealth.data.repository.MedicationReminderRepositoryImpl
import com.example.homehealth.data.repository.QARepositoryImpl
import com.example.homehealth.domain.repository.AlertRepository
import com.example.homehealth.domain.repository.DocumentRepository
import com.example.homehealth.domain.repository.FamilyRepository
import com.example.homehealth.domain.repository.HealthRecordRepository
import com.example.homehealth.domain.repository.MedicationReminderRepository
import com.example.homehealth.domain.repository.QARepository
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
}

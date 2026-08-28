package com.example.homehealth.di

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.homehealth.data.local.AppDatabase
import com.example.homehealth.data.local.dao.AlertDao
import com.example.homehealth.data.local.dao.FamilyMemberDao
import com.example.homehealth.data.local.dao.HealthRecordDao
import com.example.homehealth.data.local.dao.MedicalDocumentDao
import com.example.homehealth.data.local.dao.MedicationReminderDao
import com.example.homehealth.data.local.dao.QAHistoryDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    /** v1 → v2：family_members 新增身高、体重列（保留已有数据） */
    private val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE family_members ADD COLUMN heightCm REAL")
            db.execSQL("ALTER TABLE family_members ADD COLUMN weightKg REAL")
        }
    }

    /** v2 → v3：qa_history 新增思考过程列（保留已有数据） */
    private val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE qa_history ADD COLUMN thinking TEXT")
        }
    }

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "homehealth.db")
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
            .fallbackToDestructiveMigration()
            .build()

    @Provides
    fun provideFamilyMemberDao(db: AppDatabase): FamilyMemberDao = db.familyMemberDao()

    @Provides
    fun provideHealthRecordDao(db: AppDatabase): HealthRecordDao = db.healthRecordDao()

    @Provides
    fun provideMedicalDocumentDao(db: AppDatabase): MedicalDocumentDao = db.medicalDocumentDao()

    @Provides
    fun provideAlertDao(db: AppDatabase): AlertDao = db.alertDao()

    @Provides
    fun provideMedicationReminderDao(db: AppDatabase): MedicationReminderDao = db.medicationReminderDao()

    @Provides
    fun provideQAHistoryDao(db: AppDatabase): QAHistoryDao = db.qaHistoryDao()
}

package com.example.homehealth.di

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.homehealth.data.local.AppDatabase
import com.example.homehealth.data.local.AppMigrationSql
import com.example.homehealth.data.local.dao.AlertDao
import com.example.homehealth.data.local.dao.FamilyMemberDao
import com.example.homehealth.data.local.dao.HealthRecordDao
import com.example.homehealth.data.local.dao.LlmCallLogDao
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

    // 四个迁移对 androidTest 开放（测试必须用真实的迁移对象，用副本测试没有意义）

    /** v1 → v2：family_members 新增身高、体重列（保留已有数据） */
    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE family_members ADD COLUMN heightCm REAL")
            db.execSQL("ALTER TABLE family_members ADD COLUMN weightKg REAL")
        }
    }

    /** v2 → v3：qa_history 新增思考过程列（保留已有数据） */
    val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE qa_history ADD COLUMN thinking TEXT")
        }
    }

    /** v3 → v4：medication_reminders 新增日历事件 ID 列（保留已有数据） */
    val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE medication_reminders ADD COLUMN calendarEventIds TEXT")
        }
    }

    /**
     * v4 → v5：补索引（保留已有数据）。
     * - alerts(memberId)：预警按成员查询（未读数、去重窗口）
     * - health_records(memberId, type, recordDate)：覆盖「某成员某指标的最近 N 条」
     * 索引名由 Room 按 index_<表名>_<列名> 规则生成，必须与之逐字一致，
     * 否则打开数据库时的 schema 校验会直接抛 IllegalStateException。
     * SQL 常量与 JVM 测试共用同一份（见 AppMigrationSql，防两端漂移）。
     */
    val MIGRATION_4_5 = object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            AppMigrationSql.V4_TO_V5.forEach(db::execSQL)
        }
    }

    /**
     * v5 → v6：health_records 新增比较符列（保留已有数据）。
     * 用于承载 `<0.1` / `>100` 这类区间型结果，使它们不再因 numericValue 为空而被趋势与预警跳过。
     */
    val MIGRATION_5_6 = object : Migration(5, 6) {
        override fun migrate(db: SupportSQLiteDatabase) {
            AppMigrationSql.V5_TO_V6.forEach(db::execSQL)
        }
    }

    /**
     * v6 → v7（保留已有数据）：
     * ① 新增 `llm_call_logs` 表 —— LLM 调用可观测性（耗时 / 字符数 / 重试次数 / 失败类型）；
     * ② `alerts` 新增 5 列结构化字段 —— 让告警能按当前语言在展示层渲染，
     *    而不是把成品文案在写入时固化进数据库。
     *
     * `llm_call_logs` 的 DDL 需与 Room 生成的 schema（app/schemas/7.json）**逐字一致**，
     * 否则打开数据库时的 schema 校验会直接抛 IllegalStateException。
     */
    val MIGRATION_6_7 = object : Migration(6, 7) {
        override fun migrate(db: SupportSQLiteDatabase) {
            AppMigrationSql.V6_TO_V7.forEach(db::execSQL)
        }
    }

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "homehealth.db")
            .addMigrations(
                MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7
            )
            // 不使用 fallbackToDestructiveMigration()：版本跳变或缺失迁移时宁可直接启动失败，
            // 也不能静默清空用户数据（与下方"渐进式迁移保留数据"的定位一致）。
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

    @Provides
    fun provideLlmCallLogDao(db: AppDatabase): LlmCallLogDao = db.llmCallLogDao()
}

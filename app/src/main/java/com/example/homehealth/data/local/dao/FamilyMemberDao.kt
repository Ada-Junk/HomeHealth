package com.example.homehealth.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import com.example.homehealth.data.local.entity.FamilyMember
import kotlinx.coroutines.flow.Flow

@Dao
interface FamilyMemberDao {

    @Query("SELECT * FROM family_members ORDER BY name")
    fun observeAll(): Flow<List<FamilyMember>>

    @Query("SELECT * FROM family_members WHERE id = :id")
    suspend fun getById(id: String): FamilyMember?

    @Query("SELECT * FROM family_members ORDER BY name")
    suspend fun getAll(): List<FamilyMember>

    /**
     * 插入或更新成员。
     * 注意：不能用 @Insert(REPLACE) —— SQLite 的 REPLACE 会先 DELETE 旧行再插入，
     * 会触发 health_records 等子表的外键级联删除，导致编辑成员时健康记录全部丢失。
     * @Upsert 冲突时执行 UPDATE，不删行，无级联。
     */
    @Upsert
    suspend fun upsert(member: FamilyMember)

    @Update
    suspend fun update(member: FamilyMember)

    @Delete
    suspend fun delete(member: FamilyMember)
}

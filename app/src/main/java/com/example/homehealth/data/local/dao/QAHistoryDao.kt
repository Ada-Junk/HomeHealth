package com.example.homehealth.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.example.homehealth.data.local.entity.QAHistory
import kotlinx.coroutines.flow.Flow

@Dao
interface QAHistoryDao {

    @Query("SELECT * FROM qa_history WHERE memberId = :memberId ORDER BY timestamp")
    fun observeByMember(memberId: String): Flow<List<QAHistory>>

    @Query("SELECT * FROM qa_history ORDER BY timestamp DESC")
    suspend fun getAll(): List<QAHistory>

    @Insert
    suspend fun insert(history: QAHistory)

    @Query("DELETE FROM qa_history WHERE memberId = :memberId")
    suspend fun clearByMember(memberId: String)

    @Query("DELETE FROM qa_history WHERE memberId = :memberId")
    suspend fun deleteByMember(memberId: String)

    /**
     * 该成员全部问答附图路径。
     * 删除成员前必须先取：行一旦删掉，图片路径就再也拿不回来，`qa_images/` 下会留下永久孤儿文件。
     */
    @Query("SELECT imagePath FROM qa_history WHERE memberId = :memberId AND imagePath IS NOT NULL")
    suspend fun getImagePathsByMember(memberId: String): List<String>

    /** 全部问答附图路径（孤儿清理用：目录里不被此集合引用的文件即为孤儿） */
    @Query("SELECT imagePath FROM qa_history WHERE imagePath IS NOT NULL")
    suspend fun getAllImagePaths(): List<String>
}

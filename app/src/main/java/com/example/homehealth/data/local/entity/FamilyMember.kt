package com.example.homehealth.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/** 家庭成员 */
@Entity(tableName = "family_members")
data class FamilyMember(
    @PrimaryKey val id: String, // UUID
    val name: String,
    val relationship: String, // 如 "本人", "配偶", "父亲", "哥哥"
    val avatarUrl: String? = null,
    val dateOfBirth: String? = null, // ISO date: yyyy-MM-dd
    val gender: String? = null, // "male", "female", "other"
    val heightCm: Double? = null, // 身高（厘米）
    val weightKg: Double? = null, // 体重（千克）
    val notes: String? = null
)

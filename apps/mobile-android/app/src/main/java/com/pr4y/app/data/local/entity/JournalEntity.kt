package com.pr4y.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "journal")
data class JournalEntity(
    @PrimaryKey val id: String,
    val content: String,
    val createdAt: Long,
    val updatedAt: Long,
    val synced: Boolean = false,
)

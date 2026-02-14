package com.pr4y.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "requests")
data class RequestEntity(
    @PrimaryKey val id: String,
    val title: String,
    val body: String?,
    val createdAt: Long,
    val updatedAt: Long,
    val synced: Boolean = false,
)

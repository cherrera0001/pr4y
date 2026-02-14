package com.pr4y.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "outbox")
data class OutboxEntity(
    @PrimaryKey val recordId: String,
    val type: String,
    val version: Int,
    val encryptedPayloadB64: String,
    val clientUpdatedAt: Long,
    val createdAt: Long,
)

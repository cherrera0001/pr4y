package com.pr4y.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/** 
 * Tech Lead Note: Vinculado a userId para evitar fugas de datos entre cuentas locales.
 */
@Entity(tableName = "journal")
data class JournalEntity(
    @PrimaryKey val id: String,
    /** ID del búnker/propietario. */
    val userId: String,
    val content: String,
    val createdAt: Long,
    val updatedAt: Long,
    val synced: Boolean = false,
    val encryptedPayloadB64: String? = null,
)

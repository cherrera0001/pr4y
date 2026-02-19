package com.pr4y.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/** 
 * Tech Lead Note: Vinculado a userId para evitar fugas de datos entre cuentas locales.
 */
@Entity(tableName = "requests")
data class RequestEntity(
    @PrimaryKey val id: String,
    /** ID del búnker/propietario. */
    val userId: String,
    val title: String,
    val body: String?,
    val createdAt: Long,
    val updatedAt: Long,
    val synced: Boolean = false,
    val encryptedPayloadB64: String? = null,
)

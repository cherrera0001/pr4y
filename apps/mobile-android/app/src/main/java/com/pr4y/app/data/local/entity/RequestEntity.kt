package com.pr4y.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Almacenamiento E2EE: contenido en texto plano solo para legacy; nuevo contenido en encryptedPayloadB64. */
@Entity(tableName = "requests")
data class RequestEntity(
    @PrimaryKey val id: String,
    /** Legacy: Título en texto plano. En nuevas versiones usar encryptedPayloadB64. */
    val title: String,
    /** Legacy: Cuerpo en texto plano. En nuevas versiones usar encryptedPayloadB64. */
    val body: String?,
    val createdAt: Long,
    val updatedAt: Long,
    val synced: Boolean = false,
    /** Payload cifrado (JSON con "title", "body"). Si está presente, ignorar campos title/body planos. */
    val encryptedPayloadB64: String? = null,
)

package com.pr4y.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Almacenamiento E2EE: contenido en texto plano solo para legacy; nuevo contenido en encryptedPayloadB64. */
@Entity(tableName = "journal")
data class JournalEntity(
    @PrimaryKey val id: String,
    /** Legacy o vacío cuando se usa encryptedPayloadB64. */
    val content: String,
    val createdAt: Long,
    val updatedAt: Long,
    val synced: Boolean = false,
    /** Payload cifrado (JSON con "content", "createdAt", "updatedAt"). Preferir sobre content. */
    val encryptedPayloadB64: String? = null,
)

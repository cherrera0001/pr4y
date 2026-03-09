package com.pr4y.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Spec: Pr4y Ledger (Bitácora Personal Cifrada).
 * Privacidad Absoluta: isPublic es false por defecto.
 */
@Entity(tableName = "ledger")
data class LedgerEntity(
    @PrimaryKey val id: String,
    val userId: String,
    /** Título opcional en claro para búsqueda local rápida (no sensible). */
    val title: String,
    /** 
     * Contenido cifrado (AES-GCM). 
     * Se guarda aquí después de cifrar con la DEK derivada de la Passphrase.
     */
    val encryptedContent: String,
    val iv: String,
    val createdAt: Long,
    val updatedAt: Long,
    val isPublic: Boolean = false,
    /** Hash único para validación de integridad local. */
    val contentHash: String
)

package com.pr4y.app.data.local.dao

import androidx.room.*
import com.pr4y.app.data.local.entity.LedgerEntity
import kotlinx.coroutines.flow.Flow

/**
 * Spec: Pr4y Ledger (Bitácora Personal Cifrada).
 * Aislamiento de Datos: Filtrado obligatorio por userId.
 */
@Dao
interface LedgerDao {
    @Query("SELECT * FROM ledger WHERE userId = :userId ORDER BY createdAt DESC")
    fun getAll(userId: String): Flow<List<LedgerEntity>>

    @Query("SELECT * FROM ledger WHERE id = :id AND userId = :userId")
    suspend fun getById(id: String, userId: String): LedgerEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(ledger: LedgerEntity)

    @Update
    suspend fun update(ledger: LedgerEntity)

    @Delete
    suspend fun delete(ledger: LedgerEntity)

    @Query("DELETE FROM ledger WHERE userId = :userId")
    suspend fun deleteAll(userId: String)
}

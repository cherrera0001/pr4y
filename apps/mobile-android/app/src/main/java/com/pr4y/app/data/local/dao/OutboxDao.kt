package com.pr4y.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.pr4y.app.data.local.entity.OutboxEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface OutboxDao {
    @Query("SELECT * FROM outbox ORDER BY createdAt ASC")
    suspend fun getAll(): List<OutboxEntity>

    @Query("SELECT * FROM outbox ORDER BY createdAt ASC")
    fun getAllFlow(): Flow<List<OutboxEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: OutboxEntity)

    @Query("DELETE FROM outbox WHERE recordId = :recordId")
    suspend fun deleteByRecordId(recordId: String)
}

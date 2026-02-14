package com.pr4y.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.pr4y.app.data.local.entity.JournalEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface JournalDao {
    @Query("SELECT * FROM journal ORDER BY updatedAt DESC")
    fun getAll(): Flow<List<JournalEntity>>

    @Query("SELECT * FROM journal WHERE id = :id")
    suspend fun getById(id: String): JournalEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: JournalEntity)

    @Query("DELETE FROM journal WHERE id = :id")
    suspend fun deleteById(id: String)
}

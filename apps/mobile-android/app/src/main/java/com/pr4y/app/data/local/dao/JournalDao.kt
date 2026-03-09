package com.pr4y.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.pr4y.app.data.local.entity.JournalEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface JournalDao {
    @Query("SELECT * FROM journal WHERE userId = :userId ORDER BY updatedAt DESC")
    fun getAll(userId: String): Flow<List<JournalEntity>>

    @Query("SELECT * FROM journal WHERE userId = :userId ORDER BY updatedAt DESC LIMIT :limit")
    fun getRecent(userId: String, limit: Int = 200): Flow<List<JournalEntity>>

    @Query("SELECT * FROM journal WHERE id = :id AND userId = :userId")
    suspend fun getById(id: String, userId: String): JournalEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: JournalEntity)

    @Query("DELETE FROM journal WHERE id = :id AND userId = :userId")
    suspend fun deleteById(id: String, userId: String)
}

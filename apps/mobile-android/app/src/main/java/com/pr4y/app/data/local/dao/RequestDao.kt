package com.pr4y.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.pr4y.app.data.local.entity.RequestEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RequestDao {
    @Query("SELECT * FROM requests ORDER BY updatedAt DESC")
    fun getAll(): Flow<List<RequestEntity>>

    @Query("SELECT * FROM requests WHERE id = :id")
    suspend fun getById(id: String): RequestEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: RequestEntity)

    @Query("DELETE FROM requests WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("SELECT * FROM requests WHERE title LIKE '%' || :q || '%' OR body LIKE '%' || :q || '%' ORDER BY updatedAt DESC")
    fun search(q: String): Flow<List<RequestEntity>>
}

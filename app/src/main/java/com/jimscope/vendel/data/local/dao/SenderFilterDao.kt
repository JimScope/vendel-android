package com.jimscope.vendel.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.jimscope.vendel.data.local.entity.SenderFilterEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SenderFilterDao {

    @Query("SELECT * FROM sender_filters ORDER BY created_at DESC")
    fun observeAll(): Flow<List<SenderFilterEntity>>

    @Query("SELECT COUNT(*) FROM sender_filters")
    fun countFlow(): Flow<Int>

    @Query(
        """
        SELECT EXISTS(
            SELECT 1 FROM sender_filters
            WHERE (is_prefix = 0 AND LOWER(pattern) = LOWER(:sender))
               OR (is_prefix = 1 AND LOWER(:sender) LIKE LOWER(pattern) || '%')
        )
        """
    )
    suspend fun matches(sender: String): Boolean

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(filter: SenderFilterEntity): Long

    @Query("DELETE FROM sender_filters WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM sender_filters")
    suspend fun deleteAll()
}

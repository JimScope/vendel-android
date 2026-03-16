package com.jimscope.vendel.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.jimscope.vendel.data.local.entity.MessageLogEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MessageLogDao {

    @Insert
    suspend fun insert(log: MessageLogEntity)

    @Query("UPDATE message_log SET status = :status, error_message = :errorMessage WHERE message_id = :messageId")
    suspend fun updateStatus(messageId: String, status: String, errorMessage: String? = null)

    @Query("SELECT * FROM message_log ORDER BY timestamp DESC")
    fun observeAll(): Flow<List<MessageLogEntity>>

    @Query("SELECT COUNT(*) FROM message_log WHERE status = :status")
    fun countByStatus(status: String): Flow<Int>

    @Query("SELECT COUNT(*) FROM message_log WHERE status IN (:statuses)")
    fun countByStatuses(statuses: List<String>): Flow<Int>

    @Query("DELETE FROM message_log WHERE timestamp < :cutoff")
    suspend fun pruneOlderThan(cutoff: Long)
}

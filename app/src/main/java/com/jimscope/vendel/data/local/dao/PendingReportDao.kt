package com.jimscope.vendel.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.jimscope.vendel.data.local.entity.PendingReportEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PendingReportDao {

    @Insert
    suspend fun insert(report: PendingReportEntity)

    @Query("SELECT * FROM pending_reports ORDER BY created_at ASC")
    suspend fun getAll(): List<PendingReportEntity>

    @Query("DELETE FROM pending_reports WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT COUNT(*) FROM pending_reports")
    fun countFlow(): Flow<Int>
}

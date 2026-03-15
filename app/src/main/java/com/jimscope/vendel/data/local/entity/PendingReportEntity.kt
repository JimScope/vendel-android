package com.jimscope.vendel.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "pending_reports")
data class PendingReportEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "message_id") val messageId: String,
    @ColumnInfo(name = "status") val status: String,
    @ColumnInfo(name = "error_message") val errorMessage: String? = null,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis()
)

package com.jimscope.vendel.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "message_log")
data class MessageLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "message_id") val messageId: String,
    @ColumnInfo(name = "recipient") val recipient: String,
    @ColumnInfo(name = "body") val body: String,
    @ColumnInfo(name = "direction") val direction: String, // "outgoing" or "incoming"
    @ColumnInfo(name = "status") val status: String, // pending, sent, delivered, failed, received
    @ColumnInfo(name = "error_message") val errorMessage: String? = null,
    @ColumnInfo(name = "timestamp") val timestamp: Long = System.currentTimeMillis()
)

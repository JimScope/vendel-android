package com.jimscope.vendel.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "sender_filters",
    indices = [Index(value = ["pattern", "is_prefix"], unique = true)]
)
data class SenderFilterEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val pattern: String,
    @ColumnInfo(name = "is_prefix") val isPrefix: Boolean = false,
    val label: String? = null,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis()
)

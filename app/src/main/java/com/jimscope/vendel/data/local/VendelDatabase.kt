package com.jimscope.vendel.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.jimscope.vendel.data.local.dao.MessageLogDao
import com.jimscope.vendel.data.local.dao.PendingReportDao
import com.jimscope.vendel.data.local.dao.SenderFilterDao
import com.jimscope.vendel.data.local.entity.MessageLogEntity
import com.jimscope.vendel.data.local.entity.PendingReportEntity
import com.jimscope.vendel.data.local.entity.SenderFilterEntity

@Database(
    entities = [PendingReportEntity::class, MessageLogEntity::class, SenderFilterEntity::class],
    version = 2,
    exportSchema = false
)
abstract class VendelDatabase : RoomDatabase() {
    abstract fun pendingReportDao(): PendingReportDao
    abstract fun messageLogDao(): MessageLogDao
    abstract fun senderFilterDao(): SenderFilterDao
}

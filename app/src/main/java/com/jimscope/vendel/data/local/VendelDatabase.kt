package com.jimscope.vendel.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.jimscope.vendel.data.local.dao.MessageLogDao
import com.jimscope.vendel.data.local.dao.PendingReportDao
import com.jimscope.vendel.data.local.entity.MessageLogEntity
import com.jimscope.vendel.data.local.entity.PendingReportEntity

@Database(
    entities = [PendingReportEntity::class, MessageLogEntity::class],
    version = 1,
    exportSchema = false
)
abstract class VendelDatabase : RoomDatabase() {
    abstract fun pendingReportDao(): PendingReportDao
    abstract fun messageLogDao(): MessageLogDao
}

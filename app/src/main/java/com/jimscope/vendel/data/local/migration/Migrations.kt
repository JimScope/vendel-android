package com.jimscope.vendel.data.local.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS sender_filters (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                pattern TEXT NOT NULL,
                is_prefix INTEGER NOT NULL DEFAULT 0,
                label TEXT,
                created_at INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE UNIQUE INDEX IF NOT EXISTS index_sender_filters_pattern_is_prefix
            ON sender_filters(pattern, is_prefix)
            """.trimIndent()
        )
    }
}

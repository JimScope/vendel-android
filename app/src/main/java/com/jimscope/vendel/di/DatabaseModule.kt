package com.jimscope.vendel.di

import android.content.Context
import androidx.room.Room
import com.jimscope.vendel.data.local.VendelDatabase
import com.jimscope.vendel.data.local.dao.MessageLogDao
import com.jimscope.vendel.data.local.dao.PendingReportDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context
    ): VendelDatabase = Room.databaseBuilder(
        context,
        VendelDatabase::class.java,
        "vendel_database"
    ).build()

    @Provides
    fun providePendingReportDao(db: VendelDatabase): PendingReportDao = db.pendingReportDao()

    @Provides
    fun provideMessageLogDao(db: VendelDatabase): MessageLogDao = db.messageLogDao()
}

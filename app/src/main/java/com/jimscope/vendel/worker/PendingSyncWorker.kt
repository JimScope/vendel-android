package com.jimscope.vendel.worker

import android.content.Context
import android.util.Log
import com.jimscope.vendel.BuildConfig
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.jimscope.vendel.data.preferences.SecurePreferences
import com.jimscope.vendel.data.repository.SmsRepository
import com.jimscope.vendel.domain.RegisterFcmTokenUseCase
import com.jimscope.vendel.domain.SyncPendingUseCase
import com.jimscope.vendel.service.SmsSenderService
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class PendingSyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val syncPending: SyncPendingUseCase,
    private val registerFcmToken: RegisterFcmTokenUseCase,
    private val smsRepository: SmsRepository,
    private val securePreferences: SecurePreferences
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        if (!securePreferences.isConfigured) {
            if (BuildConfig.DEBUG) Log.d(TAG, "Not configured, skipping sync")
            return Result.success()
        }

        return try {
            val messages = syncPending().getOrDefault(emptyList())
            if (messages.isNotEmpty()) {
                SmsSenderService.start(applicationContext)
            }

            smsRepository.pruneOldLogs()
            registerFcmToken.flushPending()

            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Sync worker error", e)
            Result.retry()
        }
    }

    companion object {
        private const val TAG = "PendingSyncWorker"
        const val WORK_NAME = "pending_sync_worker"
    }
}

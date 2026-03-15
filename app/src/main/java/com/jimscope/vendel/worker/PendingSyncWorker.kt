package com.jimscope.vendel.worker

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.jimscope.vendel.data.preferences.SecurePreferences
import com.jimscope.vendel.data.repository.SmsRepository
import com.jimscope.vendel.service.SmsSenderService
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class PendingSyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val smsRepository: SmsRepository,
    private val securePreferences: SecurePreferences
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        if (!securePreferences.isConfigured) {
            Log.d(TAG, "Not configured, skipping sync")
            return Result.success()
        }

        return try {
            // Flush queued reports
            smsRepository.flushQueuedReports()

            // Check for pending messages
            val messages = smsRepository.fetchAndProcessPending()
            if (messages.isNotEmpty()) {
                SmsSenderService.start(applicationContext)
            }

            // Prune old logs (>7 days)
            smsRepository.pruneOldLogs()

            // Send pending FCM token if any
            val pendingToken = securePreferences.pendingFcmToken
            if (pendingToken.isNotBlank()) {
                smsRepository.updateFcmToken(pendingToken)
                securePreferences.pendingFcmToken = ""
            }

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

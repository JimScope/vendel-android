package com.jimscope.vendel.worker

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.jimscope.vendel.data.preferences.SecurePreferences
import com.jimscope.vendel.data.repository.SmsRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class ReportFlushWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val smsRepository: SmsRepository,
    private val securePreferences: SecurePreferences
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        if (!securePreferences.isConfigured) {
            return Result.success()
        }

        return try {
            smsRepository.flushQueuedReports()
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Report flush error", e)
            Result.retry()
        }
    }

    companion object {
        private const val TAG = "ReportFlushWorker"
        const val WORK_NAME = "report_flush_worker"
    }
}

package com.jimscope.vendel.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.jimscope.vendel.worker.PendingSyncWorker

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            Log.d(TAG, "Boot completed, enqueuing sync worker")
            val workRequest = OneTimeWorkRequestBuilder<PendingSyncWorker>().build()
            WorkManager.getInstance(context).enqueue(workRequest)
        }
    }

    companion object {
        private const val TAG = "BootReceiver"
    }
}

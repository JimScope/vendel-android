package com.jimscope.vendel.service

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.jimscope.vendel.BuildConfig
import com.jimscope.vendel.data.repository.SmsRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class SmsDeliveredReceiver : BroadcastReceiver() {

    @Inject lateinit var smsRepository: SmsRepository

    override fun onReceive(context: Context, intent: Intent) {
        val messageId = intent.getStringExtra(SmsSenderService.EXTRA_MESSAGE_ID) ?: return

        val status = when (resultCode) {
            Activity.RESULT_OK -> "delivered"
            else -> return // Don't report non-delivery; "sent" status is sufficient
        }

        if (BuildConfig.DEBUG) Log.d(TAG, "SMS $messageId delivered")

        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                smsRepository.reportStatus(messageId, status)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to report delivery for $messageId", e)
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        private const val TAG = "SmsDeliveredReceiver"
    }
}

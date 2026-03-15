package com.jimscope.vendel.service

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.SmsManager
import android.util.Log
import com.jimscope.vendel.BuildConfig
import com.jimscope.vendel.data.repository.SmsRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class SmsSentReceiver : BroadcastReceiver() {

    @Inject lateinit var smsRepository: SmsRepository

    override fun onReceive(context: Context, intent: Intent) {
        val messageId = intent.getStringExtra(SmsSenderService.EXTRA_MESSAGE_ID) ?: return
        val partIndex = intent.getIntExtra(SmsSenderService.EXTRA_PART_INDEX, 0)
        val totalParts = intent.getIntExtra(SmsSenderService.EXTRA_TOTAL_PARTS, 1)

        val status: String
        val errorMessage: String?

        when (resultCode) {
            Activity.RESULT_OK -> {
                // For multipart, only report when all parts sent
                if (totalParts > 1) {
                    val remaining = SmsSenderService.multipartTracker[messageId]
                    if (remaining != null && remaining.decrementAndGet() > 0) {
                        return // More parts pending
                    }
                    SmsSenderService.multipartTracker.remove(messageId)
                }
                status = "sent"
                errorMessage = null
            }
            SmsManager.RESULT_ERROR_GENERIC_FAILURE -> {
                status = "failed"
                errorMessage = "Generic failure"
            }
            SmsManager.RESULT_ERROR_NO_SERVICE -> {
                status = "failed"
                errorMessage = "No service"
            }
            SmsManager.RESULT_ERROR_NULL_PDU -> {
                status = "failed"
                errorMessage = "Null PDU"
            }
            SmsManager.RESULT_ERROR_RADIO_OFF -> {
                status = "failed"
                errorMessage = "Radio off"
            }
            else -> {
                status = "failed"
                errorMessage = "Unknown error: $resultCode"
            }
        }

        if (BuildConfig.DEBUG) Log.d(TAG, "SMS $messageId part $partIndex: $status ($errorMessage)")

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                smsRepository.reportStatus(messageId, status, errorMessage)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to report status for $messageId", e)
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        private const val TAG = "SmsSentReceiver"
    }
}

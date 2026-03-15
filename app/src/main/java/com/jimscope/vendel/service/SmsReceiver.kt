package com.jimscope.vendel.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log
import com.jimscope.vendel.BuildConfig
import com.jimscope.vendel.data.preferences.SecurePreferences
import com.jimscope.vendel.data.repository.SmsRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.Instant
import javax.inject.Inject

@AndroidEntryPoint
class SmsReceiver : BroadcastReceiver() {

    @Inject lateinit var smsRepository: SmsRepository
    @Inject lateinit var securePreferences: SecurePreferences

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        if (!securePreferences.isConfigured || !securePreferences.incomingSmsEnabled) return

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        if (messages.isNullOrEmpty()) return

        // Reassemble multipart messages grouped by sender
        val grouped = messages.groupBy { it.originatingAddress ?: "unknown" }

        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                for ((sender, parts) in grouped) {
                    val body = parts.joinToString("") { it.messageBody ?: "" }
                    val timestamp = Instant.ofEpochMilli(
                        parts.first().timestampMillis
                    ).toString()

                    if (BuildConfig.DEBUG) Log.d(TAG, "Incoming SMS from $sender")
                    smsRepository.reportIncoming(sender, body, timestamp)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error processing incoming SMS", e)
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        private const val TAG = "SmsReceiver"
    }
}

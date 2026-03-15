package com.jimscope.vendel.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.telephony.SmsManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.jimscope.vendel.MainActivity
import com.jimscope.vendel.R
import com.jimscope.vendel.data.remote.dto.PendingMessage
import com.jimscope.vendel.data.repository.SmsRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject

@AndroidEntryPoint
class SmsSenderService : Service() {

    @Inject lateinit var smsRepository: SmsRepository

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val notificationManager by lazy { getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                buildNotification(getString(R.string.notification_preparing)),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            startForeground(NOTIFICATION_ID, buildNotification(getString(R.string.notification_preparing)))
        }

        serviceScope.launch {
            try {
                // 1. Flush queued reports
                smsRepository.flushQueuedReports()

                // 2. Fetch pending messages
                val messages = smsRepository.fetchAndProcessPending().getOrThrow()

                if (messages.isEmpty()) {
                    updateNotification(getString(R.string.notification_no_pending))
                    stopSelf()
                    return@launch
                }

                // 3. Send each SMS sequentially with delay
                sendMessages(messages)
            } catch (e: Exception) {
                Log.e(TAG, "Service error", e)
                updateNotification(getString(R.string.notification_error, e.message ?: ""))
            } finally {
                delay(NOTIFICATION_DISPLAY_MS)
                stopSelf()
            }
        }

        return START_NOT_STICKY
    }

    private suspend fun sendMessages(messages: List<PendingMessage>) {
        val total = messages.size
        val smsManager = getSystemService(SmsManager::class.java)

        messages.forEachIndexed { index, message ->
            updateNotification(getString(R.string.notification_sending, index + 1, total))

            try {
                val body = message.body
                if (body.length > SINGLE_SMS_MAX_LENGTH) {
                    sendMultipartSms(smsManager, message)
                } else {
                    sendSingleSms(smsManager, message)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Send error for ${message.messageId}", e)
                smsRepository.reportStatus(
                    message.messageId, "failed", e.message ?: "Send failed"
                )
            }

            // Rate limiting: 1s delay between sends
            if (index < total - 1) {
                delay(RATE_LIMIT_DELAY_MS)
            }
        }

        updateNotification(getString(R.string.notification_complete, total))
    }

    private fun sendSingleSms(smsManager: SmsManager, message: PendingMessage) {
        val sentIntent = createSentPendingIntent(message.messageId, 0, 1)
        val deliveredIntent = createDeliveredPendingIntent(message.messageId, 0, 1)

        smsManager.sendTextMessage(
            message.recipient,
            null,
            message.body,
            sentIntent,
            deliveredIntent
        )
    }

    private fun sendMultipartSms(smsManager: SmsManager, message: PendingMessage) {
        val parts = smsManager.divideMessage(message.body)
        val totalParts = parts.size

        // Track multipart completion
        multipartTracker[message.messageId] = AtomicInteger(totalParts)

        val sentIntents = ArrayList<PendingIntent>(totalParts)
        val deliveredIntents = ArrayList<PendingIntent>(totalParts)

        for (i in parts.indices) {
            sentIntents.add(createSentPendingIntent(message.messageId, i, totalParts))
            deliveredIntents.add(createDeliveredPendingIntent(message.messageId, i, totalParts))
        }

        smsManager.sendMultipartTextMessage(
            message.recipient,
            null,
            parts,
            sentIntents,
            deliveredIntents
        )
    }

    private fun createSentPendingIntent(messageId: String, partIndex: Int, totalParts: Int): PendingIntent {
        val intent = Intent(SMS_SENT_ACTION).apply {
            setPackage(packageName)
            putExtra(EXTRA_MESSAGE_ID, messageId)
            putExtra(EXTRA_PART_INDEX, partIndex)
            putExtra(EXTRA_TOTAL_PARTS, totalParts)
        }
        val requestCode = "$messageId-sent-$partIndex".hashCode()
        return PendingIntent.getBroadcast(
            this, requestCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun createDeliveredPendingIntent(messageId: String, partIndex: Int, totalParts: Int): PendingIntent {
        val intent = Intent(SMS_DELIVERED_ACTION).apply {
            setPackage(packageName)
            putExtra(EXTRA_MESSAGE_ID, messageId)
            putExtra(EXTRA_PART_INDEX, partIndex)
            putExtra(EXTRA_TOTAL_PARTS, totalParts)
        }
        val requestCode = "$messageId-delivered-$partIndex".hashCode()
        return PendingIntent.getBroadcast(
            this, requestCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = getString(R.string.notification_channel_desc)
        }
        notificationManager.createNotificationChannel(channel)
    }

    private fun buildNotification(text: String): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(text: String) {
        notificationManager.notify(NOTIFICATION_ID, buildNotification(text))
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "SmsSenderService"
        const val CHANNEL_ID = "vendel_sms_channel"
        const val NOTIFICATION_ID = 1
        const val SMS_SENT_ACTION = "com.jimscope.vendel.SMS_SENT"
        const val SMS_DELIVERED_ACTION = "com.jimscope.vendel.SMS_DELIVERED"
        const val EXTRA_MESSAGE_ID = "message_id"
        const val EXTRA_PART_INDEX = "part_index"
        const val EXTRA_TOTAL_PARTS = "total_parts"

        private const val SINGLE_SMS_MAX_LENGTH = 160
        private const val RATE_LIMIT_DELAY_MS = 1000L
        private const val NOTIFICATION_DISPLAY_MS = 2000L

        val multipartTracker = ConcurrentHashMap<String, AtomicInteger>()

        fun start(context: Context) {
            val intent = Intent(context, SmsSenderService::class.java)
            context.startForegroundService(intent)
        }
    }
}

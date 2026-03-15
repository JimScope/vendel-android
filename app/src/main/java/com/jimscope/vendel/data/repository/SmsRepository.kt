package com.jimscope.vendel.data.repository

import android.util.Log
import com.jimscope.vendel.data.local.dao.MessageLogDao
import com.jimscope.vendel.data.local.dao.PendingReportDao
import com.jimscope.vendel.data.local.entity.MessageLogEntity
import com.jimscope.vendel.data.local.entity.PendingReportEntity
import com.jimscope.vendel.data.remote.VendelApi
import com.jimscope.vendel.data.remote.dto.FcmTokenRequest
import com.jimscope.vendel.data.remote.dto.IncomingSmsRequest
import com.jimscope.vendel.data.remote.dto.PendingMessage
import com.jimscope.vendel.data.remote.dto.StatusReportRequest
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SmsRepository @Inject constructor(
    private val api: VendelApi,
    private val pendingReportDao: PendingReportDao,
    private val messageLogDao: MessageLogDao,
    private val configRepository: ConfigRepository
) {
    suspend fun fetchAndProcessPending(): List<PendingMessage> {
        return try {
            val response = api.fetchPending()
            if (response.isSuccessful) {
                val body = response.body() ?: return emptyList()
                // Save device ID from response
                if (body.deviceId.isNotBlank()) {
                    configRepository.saveDeviceId(body.deviceId)
                }
                // Log each message
                body.messages.forEach { msg ->
                    messageLogDao.insert(
                        MessageLogEntity(
                            messageId = msg.messageId,
                            recipient = msg.recipient,
                            body = msg.body,
                            direction = "outgoing",
                            status = "pending"
                        )
                    )
                }
                body.messages
            } else {
                Log.e(TAG, "fetchPending failed: ${response.code()} ${response.message()}")
                emptyList()
            }
        } catch (e: Exception) {
            Log.e(TAG, "fetchPending error", e)
            emptyList()
        }
    }

    suspend fun reportStatus(messageId: String, status: String, errorMessage: String? = null) {
        // Update local log
        messageLogDao.updateStatus(messageId, status, errorMessage)

        try {
            val response = api.reportStatus(
                StatusReportRequest(messageId, status, errorMessage)
            )
            if (!response.isSuccessful) {
                Log.e(TAG, "reportStatus failed: ${response.code()}, queuing locally")
                queueReport(messageId, status, errorMessage)
            }
        } catch (e: Exception) {
            Log.e(TAG, "reportStatus error, queuing locally", e)
            queueReport(messageId, status, errorMessage)
        }
    }

    private suspend fun queueReport(messageId: String, status: String, errorMessage: String?) {
        pendingReportDao.insert(
            PendingReportEntity(
                messageId = messageId,
                status = status,
                errorMessage = errorMessage
            )
        )
    }

    suspend fun reportIncoming(fromNumber: String, body: String, timestamp: String) {
        try {
            val response = api.reportIncoming(
                IncomingSmsRequest(fromNumber, body, timestamp)
            )
            val messageId = if (response.isSuccessful) {
                response.body()?.messageId ?: "unknown"
            } else {
                Log.e(TAG, "reportIncoming failed: ${response.code()}")
                "local-${System.currentTimeMillis()}"
            }
            messageLogDao.insert(
                MessageLogEntity(
                    messageId = messageId,
                    recipient = fromNumber,
                    body = body,
                    direction = "incoming",
                    status = "received"
                )
            )
        } catch (e: Exception) {
            Log.e(TAG, "reportIncoming error", e)
            messageLogDao.insert(
                MessageLogEntity(
                    messageId = "local-${System.currentTimeMillis()}",
                    recipient = fromNumber,
                    body = body,
                    direction = "incoming",
                    status = "received"
                )
            )
        }
    }

    suspend fun flushQueuedReports() {
        val queued = pendingReportDao.getAll()
        for (report in queued) {
            try {
                val response = api.reportStatus(
                    StatusReportRequest(report.messageId, report.status, report.errorMessage)
                )
                if (response.isSuccessful) {
                    pendingReportDao.delete(report.id)
                }
            } catch (e: Exception) {
                Log.e(TAG, "flushQueuedReports error for ${report.messageId}", e)
                // Stop flushing on network error - will retry later
                break
            }
        }
    }

    suspend fun updateFcmToken(token: String) {
        try {
            val response = api.updateFcmToken(FcmTokenRequest(token))
            if (!response.isSuccessful) {
                Log.e(TAG, "updateFcmToken failed: ${response.code()}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "updateFcmToken error", e)
        }
    }

    suspend fun pruneOldLogs(daysToKeep: Int = 7) {
        val cutoff = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(daysToKeep.toLong())
        messageLogDao.pruneOlderThan(cutoff)
    }

    companion object {
        private const val TAG = "SmsRepository"
    }
}

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
import com.jimscope.vendel.data.preferences.SecurePreferences
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SmsRepository @Inject constructor(
    private val api: VendelApi,
    private val pendingReportDao: PendingReportDao,
    private val messageLogDao: MessageLogDao,
    private val configRepository: ConfigRepository,
    private val securePreferences: SecurePreferences
) {
    suspend fun healthCheck(): Result<Unit> {
        return runCatching {
            val response = api.fetchPending()
            if (!response.isSuccessful) {
                throw Exception("HTTP ${response.code()}")
            }
        }
    }

    suspend fun fetchAndProcessPending(): Result<List<PendingMessage>> {
        return runCatching {
            val response = api.fetchPending()
            if (!response.isSuccessful) {
                throw Exception("HTTP ${response.code()} ${response.message()}")
            }
            val body = response.body() ?: return Result.success(emptyList())
            securePreferences.lastSyncTimestamp = System.currentTimeMillis()
            if (body.deviceId.isNotBlank()) {
                configRepository.saveDeviceId(body.deviceId)
            }
            configRepository.refresh()
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
        }.onFailure { Log.e(TAG, "fetchPending error", it) }
    }

    suspend fun reportStatus(messageId: String, status: String, errorMessage: String? = null) {
        messageLogDao.updateStatus(messageId, status, errorMessage)

        val result = runCatching {
            val response = api.reportStatus(
                StatusReportRequest(messageId, status, errorMessage)
            )
            if (!response.isSuccessful) {
                Log.e(TAG, "reportStatus failed: ${response.code()}, queuing locally")
                queueReport(messageId, status, errorMessage)
            }
        }
        result.onFailure {
            Log.e(TAG, "reportStatus error, queuing locally", it)
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

    suspend fun reportIncoming(fromNumber: String, body: String, timestamp: String): Result<Unit> {
        return runCatching {
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
        }.onFailure {
            Log.e(TAG, "reportIncoming error", it)
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
            val result = runCatching {
                val response = api.reportStatus(
                    StatusReportRequest(report.messageId, report.status, report.errorMessage)
                )
                if (response.isSuccessful) {
                    pendingReportDao.delete(report.id)
                }
            }
            if (result.isFailure) {
                Log.e(TAG, "flushQueuedReports error for ${report.messageId}", result.exceptionOrNull())
                break
            }
        }
    }

    suspend fun updateFcmToken(token: String): Result<Unit> {
        return runCatching {
            val response = api.updateFcmToken(FcmTokenRequest(token))
            if (!response.isSuccessful) {
                Log.e(TAG, "updateFcmToken failed: ${response.code()}")
            }
        }.onFailure { Log.e(TAG, "updateFcmToken error", it) }
    }

    suspend fun pruneOldLogs(daysToKeep: Int = 7) {
        val cutoff = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(daysToKeep.toLong())
        messageLogDao.pruneOlderThan(cutoff)
    }

    companion object {
        private const val TAG = "SmsRepository"
    }
}

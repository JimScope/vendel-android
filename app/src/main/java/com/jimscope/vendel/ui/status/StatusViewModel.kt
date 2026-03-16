package com.jimscope.vendel.ui.status

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jimscope.vendel.data.local.dao.MessageLogDao
import com.jimscope.vendel.data.local.dao.PendingReportDao
import com.jimscope.vendel.data.preferences.SecurePreferences
import com.jimscope.vendel.data.repository.ConfigRepository
import com.jimscope.vendel.data.repository.ConnectionConfig
import com.jimscope.vendel.data.repository.SmsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@Immutable
data class StatusUiState(
    val config: ConnectionConfig = ConnectionConfig(),
    val sentCount: Int = 0,
    val failedCount: Int = 0,
    val pendingCount: Int = 0,
    val queuedReports: Int = 0,
    val serverReachable: Boolean? = null,
    val isCheckingConnection: Boolean = false,
    val fcmTokenRegistered: Boolean? = null
)

@HiltViewModel
class StatusViewModel @Inject constructor(
    private val configRepository: ConfigRepository,
    private val smsRepository: SmsRepository,
    private val securePreferences: SecurePreferences,
    messageLogDao: MessageLogDao,
    pendingReportDao: PendingReportDao
) : ViewModel() {

    private val _connectionState = MutableStateFlow(
        ConnectionState(serverReachable = null, isChecking = false, fcmTokenRegistered = null)
    )

    val uiState: StateFlow<StatusUiState> = combine(
        configRepository.config,
        messageLogDao.countByStatuses(listOf("sent", "delivered")),
        messageLogDao.countByStatus("failed"),
        messageLogDao.countByStatus("pending"),
        pendingReportDao.countFlow(),
        _connectionState
    ) { values ->
        val config = values[0] as ConnectionConfig
        val sent = values[1] as Int
        val failed = values[2] as Int
        val pending = values[3] as Int
        val queued = values[4] as Int
        val connState = values[5] as ConnectionState
        StatusUiState(
            config = config,
            sentCount = sent,
            failedCount = failed,
            pendingCount = pending,
            queuedReports = queued,
            serverReachable = connState.serverReachable,
            isCheckingConnection = connState.isChecking,
            fcmTokenRegistered = connState.fcmTokenRegistered
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        StatusUiState()
    )

    init {
        viewModelScope.launch {
            // Small delay so the UI renders first before checking connection
            kotlinx.coroutines.delay(300)
            checkConnection()
        }
    }

    fun checkConnection() {
        val config = configRepository.config.value
        if (!config.isConfigured) return

        viewModelScope.launch {
            _connectionState.update { it.copy(isChecking = true) }

            val healthResult = smsRepository.healthCheck()
            val hasPendingFcmToken = securePreferences.pendingFcmToken.isNotBlank()

            _connectionState.update {
                it.copy(
                    serverReachable = healthResult.isSuccess,
                    isChecking = false,
                    fcmTokenRegistered = !hasPendingFcmToken
                )
            }
        }
    }

    private data class ConnectionState(
        val serverReachable: Boolean?,
        val isChecking: Boolean,
        val fcmTokenRegistered: Boolean?
    )
}

package com.jimscope.vendel.ui.status

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jimscope.vendel.data.local.dao.MessageLogDao
import com.jimscope.vendel.data.local.dao.PendingReportDao
import com.jimscope.vendel.data.repository.ConfigRepository
import com.jimscope.vendel.data.repository.ConnectionConfig
import com.jimscope.vendel.service.SmsSenderService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class StatusUiState(
    val config: ConnectionConfig = ConnectionConfig(),
    val sentCount: Int = 0,
    val failedCount: Int = 0,
    val pendingCount: Int = 0,
    val queuedReports: Int = 0
)

@HiltViewModel
class StatusViewModel @Inject constructor(
    configRepository: ConfigRepository,
    messageLogDao: MessageLogDao,
    pendingReportDao: PendingReportDao
) : ViewModel() {

    val uiState: StateFlow<StatusUiState> = combine(
        configRepository.config,
        messageLogDao.countByStatus("sent"),
        messageLogDao.countByStatus("failed"),
        messageLogDao.countByStatus("pending"),
        pendingReportDao.countFlow()
    ) { config, sent, failed, pending, queued ->
        StatusUiState(
            config = config,
            sentCount = sent,
            failedCount = failed,
            pendingCount = pending,
            queuedReports = queued
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        StatusUiState()
    )
}

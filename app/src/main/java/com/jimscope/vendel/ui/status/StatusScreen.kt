package com.jimscope.vendel.ui.status

import android.text.format.DateUtils
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.HourglassBottom
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.jimscope.vendel.R
import com.jimscope.vendel.ui.theme.StatusDelivered
import com.jimscope.vendel.ui.theme.StatusFailed
import com.jimscope.vendel.ui.theme.StatusPending
import com.jimscope.vendel.ui.theme.StatusSent
import com.jimscope.vendel.ui.theme.VendelBrand

@Composable
fun StatusScreen(
    viewModel: StatusViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = stringResource(R.string.status_title),
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Connection status
        ConnectionStatusCard(uiState = uiState, onRetry = { viewModel.checkConnection() })

        Spacer(modifier = Modifier.height(16.dp))

        // Stats grid
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            StatCard(
                modifier = Modifier.weight(1f),
                label = stringResource(R.string.status_sent),
                count = uiState.sentCount,
                icon = { Icon(Icons.Default.CheckCircle, null, tint = StatusSent) }
            )
            StatCard(
                modifier = Modifier.weight(1f),
                label = stringResource(R.string.status_failed),
                count = uiState.failedCount,
                icon = { Icon(Icons.Default.Error, null, tint = StatusFailed) }
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            StatCard(
                modifier = Modifier.weight(1f),
                label = stringResource(R.string.status_pending),
                count = uiState.pendingCount,
                icon = { Icon(Icons.Default.HourglassBottom, null, tint = StatusPending) }
            )
            StatCard(
                modifier = Modifier.weight(1f),
                label = stringResource(R.string.status_queued),
                count = uiState.queuedReports,
                icon = { Icon(Icons.Default.Sync, null, tint = VendelBrand) }
            )
        }

    }
}

@Composable
private fun ConnectionStatusCard(
    uiState: StatusUiState,
    onRetry: () -> Unit
) {
    val statusColor = when {
        !uiState.config.isConfigured -> StatusFailed
        uiState.isCheckingConnection -> StatusPending
        uiState.serverReachable == true -> StatusSent
        uiState.serverReachable == false -> StatusFailed
        else -> StatusPending
    }

    val statusText = when {
        !uiState.config.isConfigured -> stringResource(R.string.status_disconnected)
        uiState.isCheckingConnection -> stringResource(R.string.status_checking)
        uiState.serverReachable == true -> stringResource(R.string.status_connected)
        uiState.serverReachable == false -> stringResource(R.string.status_unreachable)
        else -> stringResource(R.string.status_disconnected)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (uiState.isCheckingConnection) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(12.dp),
                        strokeWidth = 2.dp,
                        color = StatusPending
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .clip(CircleShape)
                            .background(statusColor)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.titleMedium
                    )
                    if (uiState.config.isConfigured) {
                        Text(
                            text = uiState.config.serverUrl,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                if (uiState.serverReachable == false && !uiState.isCheckingConnection) {
                    Spacer(modifier = Modifier.width(12.dp))
                    IconButton(onClick = onRetry) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = stringResource(R.string.status_retry),
                            tint = VendelBrand
                        )
                    }
                }
            }

            // Last sync timestamp
            if (uiState.config.isConfigured && uiState.config.lastSyncTimestamp > 0L) {
                Spacer(modifier = Modifier.height(8.dp))
                val relativeTime = DateUtils.getRelativeTimeSpanString(
                    uiState.config.lastSyncTimestamp,
                    System.currentTimeMillis(),
                    DateUtils.MINUTE_IN_MILLIS
                )
                Text(
                    text = stringResource(R.string.status_last_sync, relativeTime),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else if (uiState.config.isConfigured) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.status_never_synced),
                    style = MaterialTheme.typography.bodySmall,
                    color = StatusFailed
                )
            }

            // FCM token warning
            if (uiState.fcmTokenRegistered == false) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Warning,
                        contentDescription = null,
                        tint = StatusPending,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.status_fcm_pending),
                        style = MaterialTheme.typography.bodySmall,
                        color = StatusPending
                    )
                }
            }
        }
    }
}

@Composable
private fun StatCard(
    modifier: Modifier = Modifier,
    label: String,
    count: Int,
    icon: @Composable () -> Unit
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            icon()
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = count.toString(),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

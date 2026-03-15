package com.jimscope.vendel.ui.status

import android.content.Context
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
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.jimscope.vendel.service.SmsSenderService
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
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Estado",
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Connection status
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(if (uiState.config.isConfigured) StatusSent else StatusFailed)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = if (uiState.config.isConfigured) "Conectado" else "Desconectado",
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
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Stats grid
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            StatCard(
                modifier = Modifier.weight(1f),
                label = "Enviados",
                count = uiState.sentCount,
                icon = { Icon(Icons.Default.CheckCircle, null, tint = StatusSent) }
            )
            StatCard(
                modifier = Modifier.weight(1f),
                label = "Fallidos",
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
                label = "Pendientes",
                count = uiState.pendingCount,
                icon = { Icon(Icons.Default.HourglassBottom, null, tint = StatusPending) }
            )
            StatCard(
                modifier = Modifier.weight(1f),
                label = "En cola",
                count = uiState.queuedReports,
                icon = { Icon(Icons.Default.Sync, null, tint = VendelBrand) }
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Sync now button
        Button(
            onClick = { SmsSenderService.start(context) },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = VendelBrand),
            enabled = uiState.config.isConfigured
        ) {
            Icon(Icons.Default.Sync, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Sincronizar ahora")
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

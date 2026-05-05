package com.jimscope.vendel.ui.filter

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.jimscope.vendel.R
import com.jimscope.vendel.data.local.entity.SenderFilterEntity
import com.jimscope.vendel.data.repository.SenderFilterMode
import com.jimscope.vendel.ui.theme.VendelBrand
import com.jimscope.vendel.ui.theme.VendelBrandTint

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SenderFilterScreen(
    onBack: () -> Unit,
    viewModel: SenderFilterViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.filter_screen_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { viewModel.openAddSheet() },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.filter_add)) },
                containerColor = VendelBrand
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp)
        ) {
            Spacer(modifier = Modifier.height(8.dp))
            ModeSelector(
                mode = uiState.mode,
                onModeSelected = { viewModel.setMode(it) }
            )

            Spacer(modifier = Modifier.height(16.dp))

            if (uiState.mode == SenderFilterMode.ALLOW && uiState.filters.isEmpty()) {
                EmptyAllowWarning()
                Spacer(modifier = Modifier.height(12.dp))
            }

            FilterList(
                filters = uiState.filters,
                onDelete = { viewModel.delete(it) }
            )
        }

        if (uiState.showAddSheet) {
            AddFilterSheet(
                onDismiss = { viewModel.closeAddSheet() },
                onSave = { pattern, isPrefix, label ->
                    viewModel.add(pattern, isPrefix, label)
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModeSelector(
    mode: SenderFilterMode,
    onModeSelected: (SenderFilterMode) -> Unit
) {
    val options = listOf(
        SenderFilterMode.OFF to stringResource(R.string.filter_mode_off),
        SenderFilterMode.ALLOW to stringResource(R.string.filter_mode_allow),
        SenderFilterMode.BLOCK to stringResource(R.string.filter_mode_block)
    )
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        options.forEachIndexed { index, (value, label) ->
            SegmentedButton(
                selected = mode == value,
                onClick = { onModeSelected(value) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                colors = SegmentedButtonDefaults.colors(
                    activeContainerColor = VendelBrandTint,
                    activeContentColor = MaterialTheme.colorScheme.onBackground
                )
            ) {
                Text(label)
            }
        }
    }
}

@Composable
private fun EmptyAllowWarning() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = VendelBrandTint),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Warning,
                contentDescription = null,
                tint = VendelBrand
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                stringResource(R.string.filter_empty_allow_warning),
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
private fun FilterList(
    filters: List<SenderFilterEntity>,
    onDelete: (Long) -> Unit
) {
    if (filters.isEmpty()) return
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(12.dp)
    ) {
        LazyColumn(contentPadding = PaddingValues(vertical = 4.dp)) {
            items(filters, key = { it.id }) { filter ->
                FilterRow(filter = filter, onDelete = onDelete)
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun FilterRow(
    filter: SenderFilterEntity,
    onDelete: (Long) -> Unit
) {
    val isAlpha = filter.pattern.none { it.isDigit() || it == '+' }
    val icon: ImageVector = when {
        filter.label != null -> Icons.Default.Person
        isAlpha -> Icons.Default.Storefront
        else -> Icons.Default.Phone
    }
    val descriptor = when {
        filter.isPrefix -> stringResource(R.string.filter_descriptor_prefix)
        isAlpha -> stringResource(R.string.filter_descriptor_alpha_exact)
        else -> stringResource(R.string.filter_descriptor_exact)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                filter.label ?: filter.pattern,
                style = MaterialTheme.typography.bodyLarge
            )
            if (filter.label != null) {
                Text(
                    filter.pattern,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                descriptor,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        IconButton(onClick = { onDelete(filter.id) }) {
            Icon(
                Icons.Default.Close,
                contentDescription = stringResource(R.string.filter_delete_cd),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

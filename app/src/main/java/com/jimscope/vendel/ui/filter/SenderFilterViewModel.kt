package com.jimscope.vendel.ui.filter

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jimscope.vendel.data.local.entity.SenderFilterEntity
import com.jimscope.vendel.data.repository.SenderFilterMode
import com.jimscope.vendel.data.repository.SenderFilterRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@Immutable
data class SenderFilterUiState(
    val mode: SenderFilterMode = SenderFilterMode.OFF,
    val filters: List<SenderFilterEntity> = emptyList(),
    val showAddSheet: Boolean = false
)

@HiltViewModel
class SenderFilterViewModel @Inject constructor(
    private val repository: SenderFilterRepository
) : ViewModel() {

    private val _showAddSheet = MutableStateFlow(false)

    val uiState: StateFlow<SenderFilterUiState> = combine(
        repository.mode,
        repository.filters,
        _showAddSheet
    ) { mode, filters, showSheet ->
        SenderFilterUiState(mode = mode, filters = filters, showAddSheet = showSheet)
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        SenderFilterUiState()
    )

    fun setMode(mode: SenderFilterMode) {
        repository.setMode(mode)
    }

    fun openAddSheet() { _showAddSheet.value = true }

    fun closeAddSheet() { _showAddSheet.value = false }

    fun add(pattern: String, isPrefix: Boolean, label: String?) {
        if (pattern.isBlank()) return
        viewModelScope.launch {
            repository.add(pattern, isPrefix, label)
            _showAddSheet.value = false
        }
    }

    fun delete(id: Long) {
        viewModelScope.launch { repository.delete(id) }
    }
}

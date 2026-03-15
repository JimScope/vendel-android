package com.jimscope.vendel.ui.settings

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jimscope.vendel.data.preferences.SecurePreferences
import com.jimscope.vendel.data.repository.ConfigRepository
import com.jimscope.vendel.data.repository.ConnectionConfig
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@Immutable
data class SettingsUiState(
    val config: ConnectionConfig = ConnectionConfig(),
    val incomingSmsEnabled: Boolean = false
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val configRepository: ConfigRepository,
    private val securePreferences: SecurePreferences
) : ViewModel() {

    private val _incomingSmsEnabled = MutableStateFlow(securePreferences.incomingSmsEnabled)

    val uiState: StateFlow<SettingsUiState> = combine(
        configRepository.config,
        _incomingSmsEnabled
    ) { config, smsEnabled ->
        SettingsUiState(
            config = config,
            incomingSmsEnabled = smsEnabled
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = SettingsUiState(
            config = configRepository.config.value,
            incomingSmsEnabled = securePreferences.incomingSmsEnabled
        )
    )

    fun toggleIncomingSms(enabled: Boolean) {
        securePreferences.incomingSmsEnabled = enabled
        _incomingSmsEnabled.value = enabled
    }

    fun disconnect() {
        configRepository.disconnect()
    }
}

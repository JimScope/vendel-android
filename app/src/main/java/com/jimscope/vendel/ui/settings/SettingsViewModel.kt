package com.jimscope.vendel.ui.settings

import androidx.lifecycle.ViewModel
import com.jimscope.vendel.data.preferences.SecurePreferences
import com.jimscope.vendel.data.repository.ConfigRepository
import com.jimscope.vendel.data.repository.ConnectionConfig
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

data class SettingsUiState(
    val config: ConnectionConfig = ConnectionConfig(),
    val incomingSmsEnabled: Boolean = true
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val configRepository: ConfigRepository,
    private val securePreferences: SecurePreferences
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        SettingsUiState(
            config = configRepository.config.value,
            incomingSmsEnabled = securePreferences.incomingSmsEnabled
        )
    )
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    fun toggleIncomingSms(enabled: Boolean) {
        securePreferences.incomingSmsEnabled = enabled
        _uiState.value = _uiState.value.copy(incomingSmsEnabled = enabled)
    }

    fun disconnect() {
        configRepository.disconnect()
    }
}

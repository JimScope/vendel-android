package com.jimscope.vendel.ui.settings

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jimscope.vendel.BuildConfig
import com.jimscope.vendel.data.preferences.SecurePreferences
import com.jimscope.vendel.data.repository.ConfigRepository
import com.jimscope.vendel.data.repository.ConnectionConfig
import com.jimscope.vendel.domain.CheckForUpdateUseCase
import com.jimscope.vendel.domain.UpdateInfo
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@Immutable
data class SettingsUiState(
    val config: ConnectionConfig = ConnectionConfig(),
    val incomingSmsEnabled: Boolean = false,
    val updateInfo: UpdateInfo? = null
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val configRepository: ConfigRepository,
    private val securePreferences: SecurePreferences,
    private val checkForUpdate: CheckForUpdateUseCase
) : ViewModel() {

    private val _incomingSmsEnabled = MutableStateFlow(securePreferences.incomingSmsEnabled)
    private val _updateInfo = MutableStateFlow<UpdateInfo?>(null)

    val uiState: StateFlow<SettingsUiState> = combine(
        configRepository.config,
        _incomingSmsEnabled,
        _updateInfo
    ) { config, smsEnabled, update ->
        SettingsUiState(
            config = config,
            incomingSmsEnabled = smsEnabled,
            updateInfo = update
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = SettingsUiState(
            config = configRepository.config.value,
            incomingSmsEnabled = securePreferences.incomingSmsEnabled
        )
    )

    init {
        viewModelScope.launch {
            checkForUpdate(BuildConfig.VERSION_NAME).onSuccess { info ->
                if (info.isUpdateAvailable &&
                    info.latestVersion != securePreferences.dismissedUpdateVersion
                ) {
                    _updateInfo.value = info
                }
            }
        }
    }

    fun dismissUpdate(version: String) {
        securePreferences.dismissedUpdateVersion = version
        _updateInfo.value = null
    }

    fun toggleIncomingSms(enabled: Boolean) {
        securePreferences.incomingSmsEnabled = enabled
        _incomingSmsEnabled.value = enabled
    }

    fun disconnect() {
        configRepository.disconnect()
    }
}

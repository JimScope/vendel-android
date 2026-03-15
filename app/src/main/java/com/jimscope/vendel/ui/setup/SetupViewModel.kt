package com.jimscope.vendel.ui.setup

import android.util.Log
import com.jimscope.vendel.BuildConfig
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jimscope.vendel.data.preferences.SecurePreferences
import com.jimscope.vendel.data.repository.ConfigRepository
import com.jimscope.vendel.data.repository.SmsRepository
import com.squareup.moshi.Moshi
import com.squareup.moshi.JsonClass
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@JsonClass(generateAdapter = true)
data class QrPayload(
    val server_instance: String,
    val api_key: String,
    val version: String
)

data class SetupUiState(
    val serverUrl: String = "",
    val apiKey: String = "",
    val isLoading: Boolean = false,
    val error: String? = null,
    val isConnected: Boolean = false
)

@HiltViewModel
class SetupViewModel @Inject constructor(
    private val configRepository: ConfigRepository,
    private val securePreferences: SecurePreferences,
    private val smsRepository: SmsRepository,
    private val moshi: Moshi
) : ViewModel() {

    private val _uiState = MutableStateFlow(SetupUiState())
    val uiState: StateFlow<SetupUiState> = _uiState.asStateFlow()

    fun updateServerUrl(url: String) {
        _uiState.value = _uiState.value.copy(serverUrl = url, error = null)
    }

    fun updateApiKey(key: String) {
        _uiState.value = _uiState.value.copy(apiKey = key, error = null)
    }

    fun onQrScanned(rawValue: String) {
        try {
            val adapter = moshi.adapter(QrPayload::class.java)
            val payload = adapter.fromJson(rawValue)
            if (payload != null) {
                _uiState.value = _uiState.value.copy(
                    serverUrl = payload.server_instance,
                    apiKey = payload.api_key,
                    error = null
                )
                connect()
            } else {
                _uiState.value = _uiState.value.copy(error = "QR inválido")
            }
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.e(TAG, "QR parse error", e)
            _uiState.value = _uiState.value.copy(error = "QR inválido: ${e.message}")
        }
    }

    fun connect() {
        val state = _uiState.value
        if (state.serverUrl.isBlank() || state.apiKey.isBlank()) {
            _uiState.value = state.copy(error = "Completa ambos campos")
            return
        }

        _uiState.value = state.copy(isLoading = true, error = null)

        viewModelScope.launch {
            try {
                // Save config first so Retrofit picks up the new base URL
                configRepository.saveConfig(state.serverUrl, state.apiKey)

                // Validate by fetching pending messages
                smsRepository.fetchAndProcessPending().getOrThrow()

                // Register pending FCM token if any
                val pendingToken = securePreferences.pendingFcmToken
                if (pendingToken.isNotBlank()) {
                    smsRepository.updateFcmToken(pendingToken)
                    securePreferences.pendingFcmToken = ""
                }

                _uiState.value = _uiState.value.copy(isLoading = false, isConnected = true)
            } catch (e: Exception) {
                if (BuildConfig.DEBUG) Log.e(TAG, "Connection failed", e)
                configRepository.disconnect()
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "Conexión fallida: ${e.message}"
                )
            }
        }
    }

    companion object {
        private const val TAG = "SetupViewModel"
    }
}

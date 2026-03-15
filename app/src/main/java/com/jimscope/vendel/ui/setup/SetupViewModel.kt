package com.jimscope.vendel.ui.setup

import android.app.Application
import android.util.Log
import com.jimscope.vendel.BuildConfig
import com.jimscope.vendel.R
import androidx.compose.runtime.Immutable
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.jimscope.vendel.domain.ConnectDeviceUseCase
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

@Immutable
data class SetupUiState(
    val serverUrl: String = "",
    val apiKey: String = "",
    val isLoading: Boolean = false,
    val error: String? = null,
    val isConnected: Boolean = false
)

@HiltViewModel
class SetupViewModel @Inject constructor(
    application: Application,
    private val connectDevice: ConnectDeviceUseCase,
    private val moshi: Moshi
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(SetupUiState())
    val uiState: StateFlow<SetupUiState> = _uiState.asStateFlow()

    private fun getString(resId: Int): String = getApplication<Application>().getString(resId)
    private fun getString(resId: Int, vararg args: Any): String = getApplication<Application>().getString(resId, *args)

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
                _uiState.value = _uiState.value.copy(error = getString(R.string.setup_qr_invalid))
            }
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.e(TAG, "QR parse error", e)
            _uiState.value = _uiState.value.copy(
                error = getString(R.string.setup_qr_invalid_detail, e.message ?: "")
            )
        }
    }

    fun connect() {
        val state = _uiState.value
        if (state.serverUrl.isBlank() || state.apiKey.isBlank()) {
            _uiState.value = state.copy(error = getString(R.string.setup_fields_required))
            return
        }

        _uiState.value = state.copy(isLoading = true, error = null)

        viewModelScope.launch {
            connectDevice(state.serverUrl, state.apiKey)
                .onSuccess {
                    _uiState.value = _uiState.value.copy(isLoading = false, isConnected = true)
                }
                .onFailure { e ->
                    if (BuildConfig.DEBUG) Log.e(TAG, "Connection failed", e)
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = getString(R.string.setup_connection_failed, e.message ?: "")
                    )
                }
        }
    }

    companion object {
        private const val TAG = "SetupViewModel"
    }
}

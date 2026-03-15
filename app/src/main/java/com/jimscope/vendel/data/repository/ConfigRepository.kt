package com.jimscope.vendel.data.repository

import com.jimscope.vendel.data.preferences.SecurePreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

data class ConnectionConfig(
    val serverUrl: String = "",
    val apiKey: String = "",
    val deviceId: String = "",
    val isConfigured: Boolean = false
)

@Singleton
class ConfigRepository @Inject constructor(
    private val securePreferences: SecurePreferences
) {
    private val _config = MutableStateFlow(loadConfig())
    val config: StateFlow<ConnectionConfig> = _config.asStateFlow()

    private fun loadConfig(): ConnectionConfig {
        return ConnectionConfig(
            serverUrl = securePreferences.serverUrl,
            apiKey = securePreferences.apiKey,
            deviceId = securePreferences.deviceId,
            isConfigured = securePreferences.isConfigured
        )
    }

    fun saveConfig(serverUrl: String, apiKey: String) {
        securePreferences.serverUrl = serverUrl
        securePreferences.apiKey = apiKey
        _config.value = loadConfig()
    }

    fun saveDeviceId(deviceId: String) {
        securePreferences.deviceId = deviceId
        _config.value = loadConfig()
    }

    fun disconnect() {
        securePreferences.clear()
        _config.value = ConnectionConfig()
    }

    fun refresh() {
        _config.value = loadConfig()
    }
}

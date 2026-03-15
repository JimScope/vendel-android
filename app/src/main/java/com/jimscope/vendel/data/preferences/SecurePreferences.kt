package com.jimscope.vendel.data.preferences

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SecurePreferences @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs: SharedPreferences = EncryptedSharedPreferences.create(
        "vendel_secure_prefs",
        MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC),
        context,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    var serverUrl: String
        get() = prefs.getString(KEY_SERVER_URL, "") ?: ""
        set(value) = prefs.edit().putString(KEY_SERVER_URL, value).apply()

    var apiKey: String
        get() = prefs.getString(KEY_API_KEY, "") ?: ""
        set(value) = prefs.edit().putString(KEY_API_KEY, value).apply()

    var deviceId: String
        get() = prefs.getString(KEY_DEVICE_ID, "") ?: ""
        set(value) = prefs.edit().putString(KEY_DEVICE_ID, value).apply()

    var pendingFcmToken: String
        get() = prefs.getString(KEY_PENDING_FCM_TOKEN, "") ?: ""
        set(value) = prefs.edit().putString(KEY_PENDING_FCM_TOKEN, value).apply()

    var incomingSmsEnabled: Boolean
        get() = prefs.getBoolean(KEY_INCOMING_SMS_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_INCOMING_SMS_ENABLED, value).apply()

    val isConfigured: Boolean
        get() = serverUrl.isNotBlank() && apiKey.isNotBlank()

    fun clear() {
        prefs.edit().clear().apply()
    }

    companion object {
        private const val KEY_SERVER_URL = "server_url"
        private const val KEY_API_KEY = "api_key"
        private const val KEY_DEVICE_ID = "device_id"
        private const val KEY_PENDING_FCM_TOKEN = "pending_fcm_token"
        private const val KEY_INCOMING_SMS_ENABLED = "incoming_sms_enabled"
    }
}

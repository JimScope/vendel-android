package com.jimscope.vendel.domain

import android.util.Log
import com.jimscope.vendel.data.preferences.SecurePreferences
import com.jimscope.vendel.data.repository.SmsRepository
import javax.inject.Inject

class RegisterFcmTokenUseCase @Inject constructor(
    private val smsRepository: SmsRepository,
    private val securePreferences: SecurePreferences
) {
    suspend operator fun invoke(token: String) {
        if (securePreferences.isConfigured) {
            try {
                smsRepository.updateFcmToken(token)
                securePreferences.pendingFcmToken = ""
            } catch (e: Exception) {
                Log.e(TAG, "Failed to register FCM token, saving for later", e)
                securePreferences.pendingFcmToken = token
            }
        } else {
            securePreferences.pendingFcmToken = token
        }
    }

    suspend fun flushPending() {
        val pending = securePreferences.pendingFcmToken
        if (pending.isNotBlank()) {
            invoke(pending)
        }
    }

    companion object {
        private const val TAG = "RegisterFcmTokenUseCase"
    }
}

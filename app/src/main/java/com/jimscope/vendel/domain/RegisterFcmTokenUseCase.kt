package com.jimscope.vendel.domain

import android.util.Log
import com.google.firebase.messaging.FirebaseMessaging
import com.jimscope.vendel.data.preferences.SecurePreferences
import com.jimscope.vendel.data.repository.SmsRepository
import kotlinx.coroutines.tasks.await
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
            return
        }

        // If no pending token, proactively fetch the current one from Firebase
        if (securePreferences.isConfigured) {
            try {
                val currentToken = FirebaseMessaging.getInstance().token.await()
                invoke(currentToken)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to fetch current FCM token", e)
            }
        }
    }

    companion object {
        private const val TAG = "RegisterFcmTokenUseCase"
    }
}

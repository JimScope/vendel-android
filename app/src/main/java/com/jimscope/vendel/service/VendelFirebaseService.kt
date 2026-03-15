package com.jimscope.vendel.service

import android.util.Log
import com.jimscope.vendel.BuildConfig
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.jimscope.vendel.domain.RegisterFcmTokenUseCase
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class VendelFirebaseService : FirebaseMessagingService() {

    @Inject lateinit var registerFcmToken: RegisterFcmTokenUseCase

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        if (BuildConfig.DEBUG) Log.d(TAG, "FCM message received: ${message.data}")

        val type = message.data["type"]
        if (type == "tickle") {
            val count = message.data["count"]?.toIntOrNull() ?: 0
            if (BuildConfig.DEBUG) Log.d(TAG, "Tickle received, $count messages pending")
            SmsSenderService.start(this)
        }
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        if (BuildConfig.DEBUG) Log.d(TAG, "New FCM token received")

        serviceScope.launch {
            registerFcmToken(token)
        }
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "VendelFirebaseService"
    }
}

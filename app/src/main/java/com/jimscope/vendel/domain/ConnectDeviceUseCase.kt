package com.jimscope.vendel.domain

import com.jimscope.vendel.data.repository.ConfigRepository
import com.jimscope.vendel.data.repository.SmsRepository
import javax.inject.Inject

class ConnectDeviceUseCase @Inject constructor(
    private val configRepository: ConfigRepository,
    private val smsRepository: SmsRepository,
    private val registerFcmToken: RegisterFcmTokenUseCase
) {
    suspend operator fun invoke(serverUrl: String, apiKey: String): Result<Unit> {
        return runCatching {
            configRepository.saveConfig(serverUrl, apiKey)
            smsRepository.fetchAndProcessPending().getOrThrow()
            registerFcmToken.flushPending()
        }.onFailure {
            configRepository.disconnect()
        }
    }
}

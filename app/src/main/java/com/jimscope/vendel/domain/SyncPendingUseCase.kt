package com.jimscope.vendel.domain

import com.jimscope.vendel.data.remote.dto.PendingMessage
import com.jimscope.vendel.data.repository.SmsRepository
import javax.inject.Inject

class SyncPendingUseCase @Inject constructor(
    private val smsRepository: SmsRepository
) {
    suspend operator fun invoke(): Result<List<PendingMessage>> {
        smsRepository.flushQueuedReports()
        return smsRepository.fetchAndProcessPending()
    }
}

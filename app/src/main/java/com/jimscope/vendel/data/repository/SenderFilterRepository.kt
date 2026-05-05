package com.jimscope.vendel.data.repository

import com.jimscope.vendel.data.local.dao.SenderFilterDao
import com.jimscope.vendel.data.local.entity.SenderFilterEntity
import com.jimscope.vendel.data.preferences.SecurePreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SenderFilterRepository @Inject constructor(
    private val dao: SenderFilterDao,
    private val securePreferences: SecurePreferences
) {
    private val _mode = MutableStateFlow(securePreferences.senderFilterMode)
    val mode: StateFlow<SenderFilterMode> = _mode.asStateFlow()

    val filters: Flow<List<SenderFilterEntity>> = dao.observeAll()
    val count: Flow<Int> = dao.countFlow()

    fun setMode(mode: SenderFilterMode) {
        securePreferences.senderFilterMode = mode
        _mode.value = mode
    }

    suspend fun shouldForward(rawSender: String?): Boolean {
        val currentMode = _mode.value
        val hasSender = rawSender != null
        val matched = if (hasSender) {
            dao.matches(rawSender!!.trim().lowercase(Locale.ROOT))
        } else {
            false
        }
        return SenderFilterDecision.decide(currentMode, matched, hasSender)
    }

    suspend fun add(pattern: String, isPrefix: Boolean, label: String?) {
        val trimmedPattern = pattern.trim()
        if (trimmedPattern.isEmpty()) return
        dao.insert(
            SenderFilterEntity(
                pattern = trimmedPattern,
                isPrefix = isPrefix,
                label = label?.trim()?.takeIf { it.isNotBlank() }
            )
        )
    }

    suspend fun delete(id: Long) = dao.delete(id)

    suspend fun reset() {
        dao.deleteAll()
        setMode(SenderFilterMode.OFF)
    }
}

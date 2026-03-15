package com.jimscope.vendel.ui.log

import androidx.lifecycle.ViewModel
import com.jimscope.vendel.data.local.dao.MessageLogDao
import com.jimscope.vendel.data.local.entity.MessageLogEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

@HiltViewModel
class MessageLogViewModel @Inject constructor(
    messageLogDao: MessageLogDao
) : ViewModel() {
    val messages: Flow<List<MessageLogEntity>> = messageLogDao.observeAll()
}

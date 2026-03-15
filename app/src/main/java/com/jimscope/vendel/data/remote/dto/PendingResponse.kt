package com.jimscope.vendel.data.remote.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class PendingResponse(
    @Json(name = "device_id") val deviceId: String,
    @Json(name = "messages") val messages: List<PendingMessage>
)

@JsonClass(generateAdapter = true)
data class PendingMessage(
    @Json(name = "message_id") val messageId: String,
    @Json(name = "recipient") val recipient: String,
    @Json(name = "body") val body: String
)

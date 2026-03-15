package com.jimscope.vendel.data.remote.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class IncomingSmsRequest(
    @Json(name = "from_number") val fromNumber: String,
    @Json(name = "body") val body: String,
    @Json(name = "timestamp") val timestamp: String
)

@JsonClass(generateAdapter = true)
data class IncomingSmsResponse(
    @Json(name = "success") val success: Boolean,
    @Json(name = "message_id") val messageId: String
)

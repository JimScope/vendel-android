package com.jimscope.vendel.data.remote.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class StatusReportRequest(
    @Json(name = "message_id") val messageId: String,
    @Json(name = "status") val status: String,
    @Json(name = "error_message") val errorMessage: String? = null
)

@JsonClass(generateAdapter = true)
data class StatusReportResponse(
    @Json(name = "success") val success: Boolean,
    @Json(name = "message_id") val messageId: String,
    @Json(name = "status") val status: String
)

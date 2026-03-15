package com.jimscope.vendel.data.remote.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class FcmTokenRequest(
    @Json(name = "fcm_token") val fcmToken: String
)

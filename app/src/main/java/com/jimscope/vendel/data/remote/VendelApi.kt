package com.jimscope.vendel.data.remote

import com.jimscope.vendel.data.remote.dto.FcmTokenRequest
import com.jimscope.vendel.data.remote.dto.IncomingSmsRequest
import com.jimscope.vendel.data.remote.dto.IncomingSmsResponse
import com.jimscope.vendel.data.remote.dto.PendingResponse
import com.jimscope.vendel.data.remote.dto.StatusReportRequest
import com.jimscope.vendel.data.remote.dto.StatusReportResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

interface VendelApi {

    @GET("api/sms/pending")
    suspend fun fetchPending(): Response<PendingResponse>

    @POST("api/sms/report")
    suspend fun reportStatus(@Body request: StatusReportRequest): Response<StatusReportResponse>

    @POST("api/sms/incoming")
    suspend fun reportIncoming(@Body request: IncomingSmsRequest): Response<IncomingSmsResponse>

    @POST("api/sms/fcm-token")
    suspend fun updateFcmToken(@Body request: FcmTokenRequest): Response<Unit>
}

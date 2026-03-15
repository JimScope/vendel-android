package com.jimscope.vendel.data.remote

import com.jimscope.vendel.data.preferences.SecurePreferences
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject

class ApiKeyInterceptor @Inject constructor(
    private val securePreferences: SecurePreferences
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        val apiKey = securePreferences.apiKey

        val request = if (apiKey.isNotBlank()) {
            original.newBuilder()
                .header("X-API-Key", apiKey)
                .build()
        } else {
            original
        }

        return chain.proceed(request)
    }
}

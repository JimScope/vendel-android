package com.jimscope.vendel.data.remote

import com.jimscope.vendel.data.preferences.SecurePreferences
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject

class DynamicBaseUrlInterceptor @Inject constructor(
    private val securePreferences: SecurePreferences
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        val serverUrl = securePreferences.serverUrl

        if (serverUrl.isBlank()) {
            return chain.proceed(original)
        }

        val newBaseUrl = serverUrl.toHttpUrlOrNull() ?: return chain.proceed(original)
        val newUrl = original.url.newBuilder()
            .scheme(newBaseUrl.scheme)
            .host(newBaseUrl.host)
            .port(newBaseUrl.port)
            .build()

        val newRequest = original.newBuilder()
            .url(newUrl)
            .build()

        return chain.proceed(newRequest)
    }
}

package com.star.schedule.feature.importing.wakeup.data

import com.star.schedule.feature.importing.wakeup.domain.WakeUpShareSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

class OkHttpWakeUpShareSource(
    private val client: OkHttpClient = defaultClient(),
) : WakeUpShareSource {
    override suspend fun fetch(key: String): String = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("https://i.wakeup.fun/share_schedule/get?key=$key")
            .get()
            .addHeader("User-Agent", "StarSchedule/1.0")
            .build()

        client.newCall(request).execute().use { response ->
            check(response.isSuccessful) {
                "WakeUp request failed: ${response.code} ${response.message}"
            }
            requireNotNull(response.body?.string()) { "WakeUp response body is empty" }
        }
    }

    private companion object {
        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }
}

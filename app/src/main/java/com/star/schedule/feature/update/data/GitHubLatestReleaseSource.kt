package com.star.schedule.feature.update.data

import android.util.Log
import com.star.schedule.feature.update.domain.LatestReleaseSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLHandshakeException

class GitHubLatestReleaseSource(
    private val client: OkHttpClient = defaultClient(),
) : LatestReleaseSource {
    override suspend fun fetchLatestReleaseTag(): String? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(LATEST_RELEASE_URL)
                .header("Accept", "application/vnd.github+json")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.w(
                        LOG_TAG,
                        "GitHub API request failed with code: ${response.code}, message: ${response.message}",
                    )
                    return@withContext null
                }

                val body = response.body?.string()
                if (body.isNullOrBlank()) {
                    Log.w(LOG_TAG, "Empty response body from GitHub API")
                    return@withContext null
                }

                parseLatestReleaseTag(body).also { tag ->
                    Log.d(LOG_TAG, "Successfully fetched latest release tag: $tag")
                }
            }
        } catch (exception: SSLHandshakeException) {
            Log.w(LOG_TAG, "SSL handshake failed, network may be unstable", exception)
            null
        } catch (exception: SocketTimeoutException) {
            Log.w(LOG_TAG, "Request timeout, network may be slow", exception)
            null
        } catch (exception: UnknownHostException) {
            Log.w(LOG_TAG, "Cannot resolve host, network unavailable", exception)
            null
        } catch (exception: ConnectException) {
            Log.w(LOG_TAG, "Connection failed, network may be unavailable", exception)
            null
        } catch (exception: Exception) {
            Log.w(LOG_TAG, "Failed to fetch latest release tag", exception)
            null
        }
    }

    private companion object {
        const val LOG_TAG = "StarSchedule"
        const val LATEST_RELEASE_URL =
            "https://api.github.com/repos/lightStarrr/starSchedule/releases/latest"

        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }
}

internal fun parseLatestReleaseTag(responseBody: String): String =
    Json.parseToJsonElement(responseBody)
        .jsonObject["tag_name"]
        ?.jsonPrimitive
        ?.content
        ?: "v1.0.0"

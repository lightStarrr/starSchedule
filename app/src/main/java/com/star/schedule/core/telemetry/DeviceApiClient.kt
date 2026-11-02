package com.star.schedule.core.telemetry

import com.star.schedule.core.network.ApiClient
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import okhttp3.OkHttpClient

/** 所有设备接口共用认证与续期回调，避免各 feature 自建令牌缓存。 */
internal fun createDeviceApiClient(
    httpClient: OkHttpClient,
    baseUrl: String,
    session: () -> DeviceSession?,
    ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
): ApiClient = ApiClient(
    httpClient = httpClient,
    baseUrl = baseUrl,
    tokenProvider = { session()?.currentToken() },
    onUnauthorized = { failedToken -> session()?.refreshAfter401(failedToken) },
    ioDispatcher = ioDispatcher,
    onBanned = { session()?.markBanned() },
    onRefreshedToken = { sentToken, refreshedToken ->
        session()?.acceptRefreshedToken(sentToken, refreshedToken)
    },
    requestError = { session()?.requestError() },
)

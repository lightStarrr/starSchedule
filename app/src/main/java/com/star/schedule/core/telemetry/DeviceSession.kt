package com.star.schedule.core.telemetry

import com.star.schedule.core.network.ApiError
import com.star.schedule.core.network.ApiResult
import com.star.schedule.core.network.ClientInfo
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * 设备会话：token 从哪来、过期了怎么办。
 *
 * 「静默重注册」必须单飞：多个请求同时 401 时，只允许一个去注册，其余等结果复用新 token。
 * 否则同 install_id 触发「5 次/分」的注册限流，会把自己限成 429。
 * 为此 [refreshAfter401] 里做了双检——拿到锁后再看一眼 token 是不是已经被别人换掉了。
 */
class DeviceSession(
    private val api: DeviceApi,
    private val credentials: DeviceCredentialStore,
    private val identity: DeviceIdentity,
    private val clientInfo: () -> ClientInfo,
    private val nowMillis: () -> Long = System::currentTimeMillis,
) {
    private val mutex = Mutex()

    suspend fun currentToken(): String? = credentials.token()

    /** 保证有可用 token；没有就注册一次。 */
    suspend fun ensureRegistered(): ApiResult<String> {
        if (isBannedNow()) return ApiResult.Failure(ApiError.Banned())
        credentials.token()?.let { return ApiResult.Success(it) }
        return registerAndStore()
    }

    /**
     * 401 后的续期入口。返回 null 表示放弃（被禁、注册失败、仍在冷却期）。
     * [failedToken] 是刚才被拒的那个 token，用来判断别人是否已经刷新过。
     */
    suspend fun refreshAfter401(failedToken: String?): String? = mutex.withLock {
        if (isBannedNow()) return@withLock null

        val current = credentials.token()
        if (current != null && current != failedToken) return@withLock current

        when (val result = registerAndStore()) {
            is ApiResult.Success -> result.value
            is ApiResult.Failure -> null
        }
    }

    /** 校验设备状态；调用方负责控制频率，这里不做节流。 */
    suspend fun checkDevice(): ApiResult<DeviceMe> {
        val result = api.me()
        if (result is ApiResult.Success) {
            credentials.markDeviceChecked()
            if (result.value.isBanned) credentials.markBanned()
        }
        if (result is ApiResult.Failure && result.error is ApiError.Banned) {
            credentials.markBanned()
        }
        return result
    }

    private suspend fun isBannedNow(): Boolean {
        val until = credentials.bannedUntil() ?: return false
        return until > nowMillis()
    }

    private suspend fun registerAndStore(): ApiResult<String> {
        val info = clientInfo()
        val body = RegisterBody(
            installId = credentials.installId(),
            androidId = identity.androidId(),
            appVersion = info.normalizedVersionName,
            versionCode = info.versionCode,
            osVersion = info.osVersion.take(16).ifBlank { "unknown" },
            sdkInt = info.sdkInt,
            arch = info.arch.take(32).ifBlank { "unknown" },
            locale = info.locale.take(16),
            timezone = info.timezone.take(64),
        )
        return when (val result = api.register(body)) {
            is ApiResult.Success -> {
                credentials.saveRegistration(result.value.deviceId, result.value.accessToken)
                ApiResult.Success(result.value.accessToken)
            }

            is ApiResult.Failure -> {
                if (result.error is ApiError.Banned) credentials.markBanned()
                result
            }
        }
    }
}

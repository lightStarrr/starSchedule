package com.star.schedule.core.telemetry

import com.star.schedule.core.network.ApiClient
import com.star.schedule.core.network.ApiJson
import com.star.schedule.core.network.ApiRequest
import com.star.schedule.core.network.ApiResult
import com.star.schedule.core.network.RetryPolicy
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class RegisterBody(
    @SerialName("install_id") val installId: String,
    @SerialName("android_id") val androidId: String,
    val platform: String = "android",
    @SerialName("app_version") val appVersion: String,
    @SerialName("version_code") val versionCode: Int,
    @SerialName("os_version") val osVersion: String,
    @SerialName("sdk_int") val sdkInt: Int,
    val arch: String,
    val locale: String = "",
    val timezone: String = "",
)

@Serializable
data class RegisterResponse(
    @SerialName("device_id") val deviceId: String,
    @SerialName("access_token") val accessToken: String,
    @SerialName("token_type") val tokenType: String = "Bearer",
    @SerialName("expires_in") val expiresIn: Long = 0,
    @SerialName("expires_at") val expiresAt: String = "",
    @SerialName("is_new") val isNew: Boolean = false,
    @SerialName("server_time") val serverTime: String = "",
)

@Serializable
data class DeviceMe(
    @SerialName("device_id") val deviceId: String = "",
    val status: String = "",
    val platform: String = "",
    @SerialName("app_version") val appVersion: String = "",
    @SerialName("first_seen_at") val firstSeenAt: String = "",
    @SerialName("last_seen_at") val lastSeenAt: String = "",
    @SerialName("launch_count") val launchCount: Long = 0,
    @SerialName("suspect_reinstall") val suspectReinstall: Boolean = false,
) {
    val isBanned: Boolean get() = status == STATUS_BANNED

    companion object {
        const val STATUS_BANNED = "banned"
    }
}

@Serializable
data class LaunchBody(
    @SerialName("session_id") val sessionId: String,
    @SerialName("launched_at") val launchedAt: String? = null,
    @SerialName("launch_type") val launchType: String = "cold",
    @SerialName("app_version") val appVersion: String,
    @SerialName("version_code") val versionCode: Int,
    @SerialName("os_version") val osVersion: String,
    @SerialName("sdk_int") val sdkInt: Int,
    val arch: String,
    /** 目前只有 `cold_start_ms` 一个键；服务端限制整个 extra ≤1KB。 */
    val extra: Map<String, Long>? = null,
)

interface DeviceApi {
    suspend fun register(body: RegisterBody): ApiResult<RegisterResponse>
    suspend fun me(): ApiResult<DeviceMe>
    suspend fun reportLaunch(body: LaunchBody): ApiResult<Unit>
}

/**
 * 契约里本期要用的三个设备侧接口。路径都相对 `BuildConfig.API_BASE_URL`，
 * 认证由 [ApiClient] 统一加，这里不碰请求头。
 */
class HttpDeviceApi(
    private val client: ApiClient,
    private val json: Json = ApiJson,
) : DeviceApi {

    override suspend fun register(body: RegisterBody): ApiResult<RegisterResponse> =
        client.call(
            ApiRequest(
                method = "POST",
                path = "devices/register",
                body = json.encodeToString(body),
                authorized = false,
                retryPolicy = RetryPolicy.Register,
            ),
        ) { json.decodeFromString<RegisterResponse>(it) }

    override suspend fun me(): ApiResult<DeviceMe> =
        client.call(
            ApiRequest(method = "GET", path = "devices/me", retryPolicy = RetryPolicy.Read),
        ) { json.decodeFromString<DeviceMe>(it) }

    override suspend fun reportLaunch(body: LaunchBody): ApiResult<Unit> =
        client.call(
            ApiRequest(
                method = "POST",
                path = "launches",
                body = json.encodeToString(body),
                retryPolicy = RetryPolicy.Launch,
            ),
        ) { }
}

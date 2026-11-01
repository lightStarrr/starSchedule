package com.star.schedule.core.telemetry

import com.star.schedule.core.network.ApiError
import com.star.schedule.core.network.ApiResult
import com.star.schedule.core.network.ClientInfo
import kotlinx.coroutines.delay

internal class TestKeyValueStore : KeyValueStore {
    val values = mutableMapOf<String, String>()

    override fun getString(key: String): String? = values[key]

    override fun putString(key: String, value: String) {
        values[key] = value
    }

    override fun remove(key: String) {
        values.remove(key)
    }
}

internal class FakeCipher(
    var encryptFails: Boolean = false,
    var decryptFails: Boolean = false,
) : SecretCipher {

    override fun encrypt(plain: String): String? = if (encryptFails) null else "enc:$plain"

    override fun decrypt(stored: String): String? =
        if (decryptFails) null else stored.substringAfter("enc:")
}

internal val telemetryClientInfo = ClientInfo(
    versionName = "1.2.0",
    versionCode = 120,
    osVersion = "14",
    sdkInt = 34,
    arch = "arm64-v8a",
    locale = "zh-CN",
    timezone = "Asia/Shanghai",
)

internal fun registerResponseOf(
    deviceId: String = "device-1",
    token: String = "token-1",
    isNew: Boolean = true,
) = RegisterResponse(
    deviceId = deviceId,
    accessToken = token,
    isNew = isNew,
)

internal class FakeDeviceApi : DeviceApi {
    var registerCalls = 0
    var meCalls = 0
    var launchCalls = 0
    var registerDelayMillis = 0L
    var registerResult: () -> ApiResult<RegisterResponse> = { ApiResult.Success(registerResponseOf()) }
    var meResult: () -> ApiResult<DeviceMe> = { ApiResult.Success(DeviceMe(status = "active")) }
    var launchResult: () -> ApiResult<Unit> = { ApiResult.Success(Unit) }
    val registeredBodies = mutableListOf<RegisterBody>()
    val launchedBodies = mutableListOf<LaunchBody>()

    override suspend fun register(body: RegisterBody): ApiResult<RegisterResponse> {
        registerCalls++
        registeredBodies += body
        if (registerDelayMillis > 0) delay(registerDelayMillis)
        return registerResult()
    }

    override suspend fun me(): ApiResult<DeviceMe> {
        meCalls++
        return meResult()
    }

    override suspend fun reportLaunch(body: LaunchBody): ApiResult<Unit> {
        launchCalls++
        launchedBodies += body
        return launchResult()
    }
}

internal fun networkError(): ApiError = ApiError.Network(java.io.IOException("网络不可用"))

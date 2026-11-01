package com.star.schedule.core.telemetry

import android.app.Application
import android.content.Context
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import com.star.schedule.BuildConfig
import com.star.schedule.core.network.AndroidClientInfo
import com.star.schedule.core.network.ApiClient
import com.star.schedule.core.network.ApiUrlValidator
import com.star.schedule.core.network.ServerClock
import com.star.schedule.core.network.SharedApiHttpClient
import com.star.schedule.core.network.buildApiHttpClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * 启动统计的装配壳：只做接线，不含业务逻辑。
 *
 * [install] 必须是零 IO 的——它在 `Application.onCreate` 里调用，任何磁盘读写都会拖慢冷启动。
 */
object Telemetry {

    private const val TAG = "Telemetry"

    private var recorder: LaunchRecorder? = null

    fun install(
        application: Application,
        scope: CoroutineScope,
        processStartElapsedMillis: Long,
    ) {
        if (recorder != null) return

        val baseUrl = BuildConfig.API_BASE_URL.trim()
        if (!ApiUrlValidator.isUsable(baseUrl)) {
            // 地址没配好时整体停用，避免把无效请求打到一半配置的域名上
            Log.w(TAG, "API 地址不可用，启动统计已停用：$baseUrl")
            return
        }

        val context = application.applicationContext
        val credentials = DeviceCredentialStore(
            store = SharedPreferencesStore(
                context.getSharedPreferences(
                    DeviceCredentialStore.PREFERENCES_NAME,
                    Context.MODE_PRIVATE,
                ),
            ),
            cipher = KeystoreAesGcmCipher(),
        )
        val queue = LaunchQueue(
            store = SharedPreferencesStore(
                context.getSharedPreferences(LaunchQueue.PREFERENCES_NAME, Context.MODE_PRIVATE),
            ),
        )
        val clock = ServerClock()
        val identity = DeviceIdentity(
            androidIdProvider = {
                Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
            },
            installIdProvider = { credentials.installId() },
        )
        val clientInfo = { AndroidClientInfo.read() }

        SharedApiHttpClient.install {
            buildApiHttpClient(clientInfo, clock) { refreshedToken ->
                // 拦截器线程上不能阻塞，落盘丢到 IO 协程
                scope.launch(Dispatchers.IO) {
                    runCatching { credentials.updateToken(refreshedToken) }
                }
            }
        }

        // session 与 client 互相引用：401 的续期入口要回调 session，所以用可变持有者打破循环。
        var session: DeviceSession? = null
        val api = HttpDeviceApi(
            ApiClient(
                httpClient = SharedApiHttpClient.get(),
                baseUrl = baseUrl,
                tokenProvider = { credentials.token() },
                onUnauthorized = { failedToken -> session?.refreshAfter401(failedToken) },
            ),
        )
        val deviceSession = DeviceSession(
            api = api,
            credentials = credentials,
            identity = identity,
            clientInfo = clientInfo,
        )
        session = deviceSession

        recorder = LaunchRecorder(
            queue = queue,
            api = api,
            session = deviceSession,
            clock = clock,
            clientInfo = clientInfo,
            scope = scope,
            processStartElapsedMillis = processStartElapsedMillis,
            elapsedMillis = { SystemClock.elapsedRealtime() },
            log = { message -> Log.w(TAG, message) },
        )
    }

    fun onProcessStart() {
        recorder?.onProcessStart()
    }

    fun onForeground() {
        recorder?.onForeground()
    }

    val isEnabled: Boolean get() = recorder != null
}

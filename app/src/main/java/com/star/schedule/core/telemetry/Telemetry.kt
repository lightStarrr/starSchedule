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

/**
 * 启动统计的装配壳：只做接线，不含业务逻辑。
 *
 * [install] 必须是零 IO 的——它在 `Application.onCreate` 里调用，任何磁盘读写都会拖慢冷启动。
 */
object Telemetry {

    private const val TAG = "Telemetry"

    private var recorder: LaunchRecorder? = null
    private var client: ApiClient? = null

    private val startup = TelemetryStartupGate(
        onProcessStart = { recorder?.onProcessStart() },
        onForeground = { recorder?.onForeground() },
        onConsentChanged = { enabled -> recorder?.onDataCollectionChanged(enabled) },
    )

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

        // Token 在挂起请求中落盘，保证本次调用返回后所有接口立即看到续期结果。
        SharedApiHttpClient.install { buildApiHttpClient(clientInfo, clock) {} }

        // session 与 client 互相引用：401 的续期入口要回调 session，所以用可变持有者打破循环。
        var session: DeviceSession? = null
        val apiClient = createDeviceApiClient(
            httpClient = SharedApiHttpClient.get(),
            baseUrl = baseUrl,
            session = { session },
        )
        val api = HttpDeviceApi(apiClient)
        val deviceSession = DeviceSession(
            api = api,
            credentials = credentials,
            identity = identity,
            clientInfo = clientInfo,
            enabled = { startup.enabled },
        )
        session = deviceSession
        client = apiClient

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
            enabled = { startup.enabled },
        )
    }

    /**
     * 隐私开关。关闭后不再记录、不再上报，并清掉本地积压。
     *
     * 云端 AI 共用同一个开关：AI 请求必须带设备令牌，而令牌只能靠注册拿到，
     * 关掉统计就等于关掉云端能力，所以这里不单独维护第二个状态。
     */
    fun setDataCollectionEnabled(enabled: Boolean) {
        startup.updateConsent(enabled)
    }

    fun onProcessStart() {
        startup.processStarted()
    }

    fun onForeground() {
        startup.foregrounded()
    }

    val isEnabled: Boolean get() = recorder != null

    internal fun apiClient(): ApiClient = checkNotNull(client) { "设备网络会话未初始化" }
}

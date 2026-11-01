package com.star.schedule.core.network

import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient

/**
 * 构造后端专用的 OkHttpClient。
 *
 * `retryOnConnectionFailure(false)` 是刻意的：默认值会让 OkHttp 在连接失败时自行换路由重试，
 * 与 [RetryPolicy] 的 1s/3s/7s 叠加后实际请求次数不可控，很容易把「同 install_id 5 次/分」
 * 的注册限流撞满。
 */
fun buildApiHttpClient(
    clientInfo: () -> ClientInfo,
    serverClock: ServerClock,
    onRefreshedToken: (String) -> Unit,
): OkHttpClient = OkHttpClient.Builder()
    .callTimeout(15, TimeUnit.SECONDS)
    .connectTimeout(10, TimeUnit.SECONDS)
    .readTimeout(15, TimeUnit.SECONDS)
    .writeTimeout(15, TimeUnit.SECONDS)
    .retryOnConnectionFailure(false)
    .addInterceptor(CommonHeadersInterceptor(clientInfo))
    .addInterceptor(ResponseMetadataInterceptor(serverClock, onRefreshedToken))
    .build()

/**
 * 进程级共享 client 的薄壳，风格对齐 `DatabaseProvider`。
 *
 * 只做「幂等安装 + 惰性持有」，真正的构造逻辑在 [buildApiHttpClient] 里，测试可以直接
 * 拼自己的 client，不必碰这个单例。故意不提供 `close()`：连接池空闲会自行回收，
 * 主动关闭只会让后来者拿到不可用的实例。
 */
object SharedApiHttpClient {
    private var factory: (() -> OkHttpClient)? = null
    private var instance: OkHttpClient? = null
    private val lock = Any()

    fun install(factory: () -> OkHttpClient) {
        synchronized(lock) {
            this.factory = factory
            this.instance = null
        }
    }

    fun get(): OkHttpClient {
        synchronized(lock) {
            instance?.let { return it }
            val created = factory?.invoke() ?: error("SharedApiHttpClient 未初始化")
            instance = created
            return created
        }
    }
}

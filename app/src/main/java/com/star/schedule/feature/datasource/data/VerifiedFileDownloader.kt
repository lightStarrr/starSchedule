package com.star.schedule.feature.datasource.data

import java.io.File
import java.io.IOException
import java.security.MessageDigest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * 带校验的文件下载器：按顺序尝试镜像列表，下载到临时文件，
 * 大小与 SHA-256 全部对得上才原子改名落到目标位置。
 *
 * 不依赖 Android，可在 JVM 单测中跑（镜像失败通过 [onMirrorFailed] 上报，
 * 由调用方决定记日志还是别的处理）。取消会打断阻塞读并清掉临时文件。
 */
class VerifiedFileDownloader(
    private val httpClient: OkHttpClient,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val onMirrorFailed: (String, Throwable) -> Unit = { _, _ -> },
) {

    /**
     * @param urls 镜像列表，按顺序尝试
     * @param expectedSize 期望字节数，读到超过它立即中止（防止被喂大文件）
     * @param expectedSha256 期望的 SHA-256（十六进制）
     * @param target 目标文件，原子落地（同目录 [target].part 临时文件）
     * @param onProgress 已下载字节数回调，每个读块至多回调一次
     */
    suspend fun download(
        urls: List<String>,
        expectedSize: Long,
        expectedSha256: String,
        target: File,
        onProgress: (Long) -> Unit,
    ) = withContext(ioDispatcher) {
        var lastError: Throwable? = null
        for (url in urls) {
            coroutineContext.ensureActive()
            try {
                fetchFrom(url, expectedSize, expectedSha256, target, onProgress)
                return@withContext
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                onMirrorFailed(url, e)
                lastError = e
            }
        }
        throw lastError ?: IOException("no download urls provided")
    }

    private suspend fun fetchFrom(
        url: String,
        expectedSize: Long,
        expectedSha256: String,
        target: File,
        onProgress: (Long) -> Unit,
    ) {
        val part = File(target.parentFile, target.name + ".part")
        try {
            val call = httpClient.newCall(
                Request.Builder()
                    .url(url)
                    .header("User-Agent", "starSchedule-android")
                    .build(),
            )
            // execute() 的阻塞读不吃协程取消，挂在 Job 上让 OkHttp 层一起断。
            coroutineContext[Job]?.invokeOnCompletion { call.cancel() }
            call.execute().use { response ->
                if (!response.isSuccessful) {
                    throw IOException("HTTP ${response.code} from $url")
                }
                val body = response.body ?: throw IOException("empty body from $url")
                val digest = MessageDigest.getInstance("SHA-256")
                var downloaded = 0L
                part.outputStream().buffered().use { out ->
                    val source = body.byteStream()
                    val buffer = ByteArray(READ_CHUNK)
                    while (true) {
                        coroutineContext.ensureActive()
                        val read = source.read(buffer)
                        if (read < 0) break
                        downloaded += read
                        if (downloaded > expectedSize) {
                            throw IOException("response larger than expected $expectedSize bytes")
                        }
                        digest.update(buffer, 0, read)
                        out.write(buffer, 0, read)
                        onProgress(downloaded)
                    }
                }
                if (downloaded != expectedSize) {
                    throw IOException(
                        "size mismatch: got $downloaded, expected $expectedSize",
                    )
                }
                val actualSha = digest.digest().toHex()
                if (!actualSha.equals(expectedSha256, ignoreCase = true)) {
                    throw IOException("sha256 mismatch: got $actualSha")
                }
            }
            // 校验通过才落位：先删旧目标再改名。
            if (target.exists() && !target.delete()) {
                throw IOException("cannot remove previous ${target.name}")
            }
            if (!part.renameTo(target)) {
                throw IOException("cannot move ${part.name} into place")
            }
        } finally {
            // 任何失败或取消都删掉临时文件（成功时 part 已被改名移走）。
            part.delete()
        }
    }

    private fun ByteArray.toHex(): String = buildString(size * 2) {
        for (b in this@toHex) append("%02x".format(b))
    }

    private companion object {
        const val READ_CHUNK = 64 * 1024
    }
}

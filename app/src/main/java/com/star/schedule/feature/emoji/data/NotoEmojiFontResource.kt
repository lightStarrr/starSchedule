package com.star.schedule.feature.emoji.data

import android.util.Log
import com.star.schedule.feature.datasource.data.VerifiedFileDownloader
import com.star.schedule.feature.datasource.domain.OptionalResource
import java.io.File
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient

/**
 * Google Noto Emoji 黑白表情字体（可变字重版），之后给表情壁纸用。
 *
 * 版本固定在 google/fonts 的某个提交上，大小与 SHA-256 实测写死，
 * 下载时逐字节校验，镜像源不可信也不会落进错文件。
 * 文件放 filesDir 而不是 cacheDir：系统清缓存不该删用户主动下载的资源。
 */
class NotoEmojiFontResource(
    private val httpClient: OkHttpClient,
    filesDir: File,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : OptionalResource {

    override val id: String = ID
    override val expectedSizeBytes: Long = SIZE_BYTES

    private val dir = File(filesDir, "optional-resources/noto-emoji")
    private val file = File(dir, FILE_NAME)

    override fun installedFile(): File? =
        file.takeIf { it.isFile && it.length() == expectedSizeBytes }

    override suspend fun download(onProgress: (Long) -> Unit) {
        dir.mkdirs()
        VerifiedFileDownloader(httpClient, ioDispatcher) { url, error ->
            Log.w(TAG, "镜像 $url 下载失败，尝试下一个", error)
        }.download(URLS, expectedSizeBytes, SHA256, file, onProgress)
    }

    override suspend fun delete() = withContext(ioDispatcher) {
        EmojiTypefaceLoader.evict(file)
        // 删掉已安装文件与取消/失败后残留的 .part。
        dir.listFiles()?.forEach { it.delete() }
        Unit
    }

    companion object {
        const val ID = "noto_emoji_font"

        fun fontFile(filesDir: File): File =
            File(File(filesDir, "optional-resources/noto-emoji"), FILE_NAME)

        /** google/fonts 提交与路径（[wght] 必须 URL 编码）。 */
        const val FONT_VERSION = "3.002"
        private const val COMMIT = "b979dba422e445492b0eb9951ac52ee0b4d648c3"
        private const val FONT_PATH = "ofl/notoemoji/NotoEmoji%5Bwght%5D.ttf"
        private const val FILE_NAME = "NotoEmoji-3.002.ttf"
        private const val SIZE_BYTES = 1_982_596L
        private const val SHA256 =
            "de6c18832938afc99caf132b39d6a30a19bac7f2e812e28db2535b4608d27551"
        private const val TAG = "NotoEmojiFontResource"

        // jsDelivr 多域名在前、GitHub raw 兜底，与节假日数据源同一理由：
        // 任一 CDN 域名不通时还有机会换下一个，而不是整次下载失败。
        private val URLS = listOf(
            "https://fastly.jsdelivr.net/gh/google/fonts@$COMMIT/$FONT_PATH",
            "https://testingcf.jsdelivr.net/gh/google/fonts@$COMMIT/$FONT_PATH",
            "https://cdn.jsdelivr.net/gh/google/fonts@$COMMIT/$FONT_PATH",
            "https://gcore.jsdelivr.net/gh/google/fonts@$COMMIT/$FONT_PATH",
            "https://raw.githubusercontent.com/google/fonts/$COMMIT/$FONT_PATH",
        )
    }
}

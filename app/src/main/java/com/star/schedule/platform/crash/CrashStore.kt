package com.star.schedule.platform.crash

import android.content.Context
import android.os.Build
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/** 一次崩溃的可展示信息。 */
data class CrashReport(
    val summary: String,
    val trace: String,
    val time: String,
    val appVersion: String,
    val device: String,
    val threadName: String,
) {
    /** 复制/分享用的纯文本。 */
    fun asShareText(): String = buildString {
        appendLine("时间：$time")
        appendLine("应用版本：$appVersion")
        appendLine("设备：$device")
        appendLine("线程：$threadName")
        appendLine()
        appendLine(summary)
        appendLine()
        append(trace)
    }
}

/**
 * 崩溃报告落盘。
 *
 * 崩溃页跑在 `:crash` 进程里，和崩溃的进程不是同一个，所以信息只能通过文件传过去；
 * 用 Intent 传 trace 有可能撞上 Binder 事务大小上限。
 */
object CrashStore {

    private const val DIR_NAME = "crashes"
    private const val LATEST_FILE = "latest.txt"
    private const val KEEP_FILES = 5

    private const val SECTION_SUMMARY = "===summary"
    private const val SECTION_TRACE = "===trace"

    fun save(context: Context, thread: Thread, throwable: Throwable): CrashReport? {
        val report = buildReport(context, thread, throwable)
        val dir = dir(context) ?: return report
        runCatching {
            File(dir, LATEST_FILE).writeText(encode(report))
            // 同时留一份带时间戳的，方便用户挑一次具体崩溃反馈
            val stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"))
            File(dir, "crash-$stamp.txt").writeText(encode(report))
            prune(dir)
        }
        return report
    }

    fun loadLatest(context: Context): CrashReport? {
        val file = File(dir(context) ?: return null, LATEST_FILE)
        if (!file.exists()) return null
        return runCatching { decode(file.readText()) }.getOrNull()
    }

    fun clear(context: Context) {
        dir(context)?.listFiles()?.forEach { runCatching { it.delete() } }
    }

    private fun buildReport(context: Context, thread: Thread, throwable: Throwable): CrashReport {
        // 带上 versionCode：用户反馈时才能对上当时那一份 mapping.txt。
        val version = runCatching {
            val info = context.packageManager.getPackageInfo(context.packageName, 0)
            "${info.versionName} (${info.longVersionCode})"
        }.getOrNull().orEmpty()
        // 展示最内层的 cause：框架常把真正的异常包在「Unable to start activity」之类的外层里。
        val root = generateSequence(throwable) { it.cause }.lastOrNull() ?: throwable
        return CrashReport(
            summary = "${root.javaClass.name}: ${root.message.orEmpty()}".trim(),
            trace = throwable.stackTraceToString(),
            time = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")),
            appVersion = version.ifEmpty { "未知" },
            device = "${Build.MANUFACTURER} ${Build.MODEL} / Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
            threadName = thread.name,
        )
    }

    private fun encode(report: CrashReport): String = buildString {
        appendLine(SECTION_SUMMARY)
        appendLine(report.summary)
        appendLine("time: ${report.time}")
        appendLine("version: ${report.appVersion}")
        appendLine("device: ${report.device}")
        appendLine("thread: ${report.threadName}")
        appendLine(SECTION_TRACE)
        append(report.trace)
    }

    private fun decode(text: String): CrashReport {
        val summaryBlock = text.substringAfter(SECTION_SUMMARY, "").substringBefore(SECTION_TRACE)
        val trace = text.substringAfter(SECTION_TRACE, "")
        val lines = summaryBlock.trim().lines()
        fun field(name: String): String =
            lines.firstOrNull { it.startsWith("$name: ") }?.removePrefix("$name: ").orEmpty()
        return CrashReport(
            summary = lines.firstOrNull().orEmpty(),
            trace = trace,
            time = field("time"),
            appVersion = field("version"),
            device = field("device"),
            threadName = field("thread"),
        )
    }

    private fun dir(context: Context): File? = runCatching {
        File(context.filesDir, DIR_NAME).apply { if (!exists()) mkdirs() }
    }.getOrNull()

    private fun prune(dir: File) {
        dir.listFiles()
            ?.filter { it.name.startsWith("crash-") }
            ?.sortedByDescending { it.lastModified() }
            ?.drop(KEEP_FILES)
            ?.forEach { runCatching { it.delete() } }
    }
}

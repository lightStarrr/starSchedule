package com.star.schedule.platform.crash

import android.content.Context
import android.content.Intent
import android.os.Process
import com.star.schedule.CrashActivity
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

/**
 * 全局崩溃拦截：落盘 → 起崩溃页 → 交回系统原流程。
 *
 * 崩溃页跑在独立的 `:crash` 进程，所以主进程被杀掉也不影响它显示；
 * 这也是为什么异常信息要先写文件，而不是塞进 Intent。
 */
object CrashHandler {

    private val handling = AtomicBoolean(false)

    fun install(context: Context) {
        val appContext = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            // 同一个进程可能同时炸多条线程，只处理第一条，避免连开好几个崩溃页。
            if (!handling.compareAndSet(false, true)) {
                previous?.uncaughtException(thread, throwable)
                return@setDefaultUncaughtExceptionHandler
            }

            runCatching { CrashStore.save(appContext, thread, throwable) }
            runCatching { startCrashScreen(appContext) }

            // 给系统一点时间把崩溃页拉起来，然后自己结束本进程。
            // 不交给系统默认处理器：那样会先弹一个「屡次停止运行」的系统对话框，把崩溃页压在下面。
            runCatching {
                thread(name = "crash-exit") {
                    runCatching { Thread.sleep(CRASH_SCREEN_LAUNCH_DELAY_MILLIS) }
                    Process.killProcess(Process.myPid())
                }
            }
        }
    }

    private fun startCrashScreen(context: Context) {
        val intent = Intent(context, CrashActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
        }
        context.startActivity(intent)
    }

    private const val CRASH_SCREEN_LAUNCH_DELAY_MILLIS = 700L
}

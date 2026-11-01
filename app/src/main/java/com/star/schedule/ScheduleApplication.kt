package com.star.schedule

import android.app.Application
import android.os.SystemClock
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.star.schedule.core.common.Constants
import com.star.schedule.core.database.DatabaseProvider
import com.star.schedule.core.telemetry.Telemetry
import com.star.schedule.platform.crash.CrashHandler
import com.star.schedule.platform.recents.RecentsTaskVisibility
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

class ScheduleApplication : Application() {

    private companion object {
        const val CRASH_PROCESS_SUFFIX = ":crash"
    }

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    // 进程起点，用于冷启动耗时统计。属性初始化早于 onCreate，不掺杂任何初始化开销。
    private val processStartElapsedMillis = SystemClock.elapsedRealtime()

    override fun onCreate() {
        super.onCreate()
        // 崩溃页跑在独立的 :crash 进程，那边不用再装一遍——否则崩溃页自己出错就没人接了。
        // 启动统计同理：:crash 进程不参与，否则一次崩溃会多记一条启动。
        if (!isCrashProcess()) {
            CrashHandler.install(this)
            Telemetry.install(this, applicationScope, processStartElapsedMillis)
            Telemetry.onProcessStart()
        }
        DatabaseProvider.init(this)
        observeRecentsPreference()
        observePrivacyPreference()
        observeProcessLifecycle()
    }

    private fun isCrashProcess(): Boolean =
        applicationInfo.processName?.endsWith(CRASH_PROCESS_SUFFIX) == true

    /**
     * 把「从最近任务隐藏」的偏好同步到内存，供生命周期回调同步读取。
     *
     * 关闭开关时立即解除隐藏：否则在设置页改完直接回到桌面，任务要等到下次回到前台
     * 才恢复显示。
     */
    private fun observeRecentsPreference() {
        applicationScope.launch {
            DatabaseProvider.dao()
                .getPreferenceFlow(Constants.PREF_HIDE_FROM_RECENTS)
                .collect { value ->
                    val enabled = value == "true"
                    RecentsTaskVisibility.hideFromRecents = enabled
                    if (!enabled) {
                        RecentsTaskVisibility.apply(this@ScheduleApplication, excluded = false)
                    }
                }
        }
    }

    /**
     * 隐私开关要在这里订阅而不是在设置页里处理：用户在设置页关掉后可能直接划掉应用，
     * 只有进程级订阅能保证下一次启动一开始就是关闭状态。
     */
    private fun observePrivacyPreference() {
        applicationScope.launch {
            DatabaseProvider.dao()
                .getPreferenceFlow(Constants.PREF_PRIVACY_DATA_ENABLED)
                .collect { value ->
                    Telemetry.setDataCollectionEnabled(Constants.isDataCollectionEnabled(value))
                }
        }
    }

    /**
     * 用进程级生命周期而不是单个 Activity 的：从设置页直接回到桌面时，主界面早已处于
     * stop 状态，收不到任何回调。
     */
    private fun observeProcessLifecycle() {
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                RecentsTaskVisibility.apply(this@ScheduleApplication, excluded = false)
                Telemetry.onForeground()
            }

            override fun onStop(owner: LifecycleOwner) {
                if (RecentsTaskVisibility.hideFromRecents) {
                    RecentsTaskVisibility.apply(this@ScheduleApplication, excluded = true)
                }
            }
        })
    }
}

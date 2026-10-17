package com.star.schedule.platform.recents

import android.app.ActivityManager
import android.content.Context

/**
 * 控制本应用的任务是否出现在最近任务列表中。
 *
 * 只调整任务的可见性，不结束 Activity：应用会继续留在后台，从桌面图标回来时仍在
 * 原来的界面。
 *
 * 设置项本身存在数据库里，生命周期回调无法同步读取，因此由
 * [com.star.schedule.ScheduleApplication] 订阅偏好后写入 [hideFromRecents]，
 * 回调里只读这个内存标志。
 */
object RecentsTaskVisibility {

    /** 是否从最近任务列表隐藏。由数据库偏好同步而来。 */
    @Volatile
    var hideFromRecents: Boolean = false

    /**
     * 对当前应用的所有任务设置（或取消）[ActivityManager.AppTask.setExcludeFromRecents]。
     *
     * 逐项 runCatching：任务可能恰好在遍历过程中被销毁，个别失败不应影响其余任务。
     */
    fun apply(context: Context, excluded: Boolean) {
        val activityManager = context.getSystemService(ActivityManager::class.java) ?: return
        activityManager.appTasks.forEach { task ->
            runCatching { task.setExcludeFromRecents(excluded) }
        }
    }
}

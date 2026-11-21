package com.star.schedule.service

import android.content.Context
import android.util.Log
import androidx.glance.appwidget.GlanceAppWidgetManager
import com.star.schedule.widget.TwoDaysWidget
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 小组件刷新协调器，统一在后台 IO 线程执行并挂起等待更新完成。
 */
object WidgetRefreshManager {

    private const val TAG = "WidgetRefreshManager"

    /**
     * 立即刷新小组件，由调用方的协程生命周期负责取消与完成回调。
     */
    suspend fun refreshWidgetImmediately(context: Context) = withContext(Dispatchers.IO) {
        val appContext = context.applicationContext
        try {
            Log.d(TAG, "开始立即刷新小组件")
            val glanceIds = GlanceAppWidgetManager(appContext).getGlanceIds(TwoDaysWidget::class.java)
            if (glanceIds.isEmpty()) {
                Log.d(TAG, "没有找到有效的小组件实例，跳过刷新")
                return@withContext
            }
            Log.d(TAG, "找到 ${glanceIds.size} 个小组件实例，开始刷新")
            TwoDaysWidget.updateWidgetContent(appContext)
            Log.d(TAG, "小组件刷新完成")
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (e: Exception) {
            Log.e(TAG, "刷新小组件失败", e)
            throw e
        }
    }
}

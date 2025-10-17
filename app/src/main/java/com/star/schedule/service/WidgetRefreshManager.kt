package com.star.schedule.service

import android.content.Context
import android.util.Log
import androidx.glance.appwidget.GlanceAppWidgetManager
import com.star.schedule.widget.MyWidget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * 小组件刷新管理器
 * 负责在课表切换或课程数据修改时立即刷新小组件
 */
object WidgetRefreshManager {

    private const val TAG = "WidgetRefreshManager"

    /**
     * 立即刷新小组件
     * 在课表切换或课程数据修改时调用
     */
    fun refreshWidgetImmediately(context: Context) {
        try {
            Log.d(TAG, "开始立即刷新小组件")

            val manager = GlanceAppWidgetManager(context)

            CoroutineScope(Dispatchers.Main).launch {
                val glanceIds = manager.getGlanceIds(MyWidget::class.java)
                if (glanceIds.isEmpty()) {
                    Log.d(TAG, "没有找到有效的小组件实例，跳过刷新")
                    return@launch
                }

                Log.d(TAG, "找到 ${glanceIds.size} 个小组件实例，开始刷新")

                try {
                    MyWidget.updateWidgetContent(context)
                    Log.d(TAG, "小组件刷新完成")
                } catch (e: Exception) {
                    Log.e(TAG, "刷新小组件失败", e)
                }
            }

        } catch (e: Exception) {
            Log.e(TAG, "刷新小组件失败", e)
        }
    }

    /**
     * 课表切换时调用
     * 当用户切换当前课表时触发
     */
    fun onTimetableSwitched(context: Context) {
        Log.d(TAG, "检测到课表切换，立即刷新小组件")
        refreshWidgetImmediately(context)
    }

    /**
     * 课程数据修改时调用
     * 当添加、编辑或删除课程时触发
     */
    fun onCourseDataChanged(context: Context) {
        Log.d(TAG, "检测到课程数据修改，立即刷新小组件")
        refreshWidgetImmediately(context)
    }

    /**
     * 课表设置修改时调用
     * 当修改课表名称、开始日期等设置时触发
     */
    fun onTimetableSettingsChanged(context: Context) {
        Log.d(TAG, "检测到课表设置修改，立即刷新小组件")
        refreshWidgetImmediately(context)
    }
}
package com.star.schedule.notification

import android.content.Context
import androidx.work.WorkManager

/**
 * WorkManager 配置类
 * 确保 WorkManager 正确初始化
 */
object WorkManagerConfig {
    
    private var isInitialized = false
    
    /**
     * 初始化 WorkManager
     * 应在 Application 类或 MainActivity 中调用
     */
    fun initialize(context: Context) {
        if (!isInitialized) {
            try {
                WorkManager.getInstance(context)
                isInitialized = true
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
    
    /**
     * 获取 WorkManager 实例
     */
    fun getWorkManager(context: Context): WorkManager {
        return WorkManager.getInstance(context)
    }
}
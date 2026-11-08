package com.star.schedule.feature.datasource.domain

/**
 * 自动刷新调度的平台接口，由 platform 层用 WorkManager 实现。
 *
 * 仓库只告诉它目标间隔，不关心任务是周期任务还是一次性任务；
 * [intervalDays] <= 0 时实现方取消已排任务。
 */
interface DataSourceRefreshScheduler {
    fun schedule(intervalDays: Int)
}

package com.star.schedule.feature.datasource.data

import com.star.schedule.feature.datasource.domain.OptionalResource
import com.star.schedule.feature.datasource.domain.OptionalResourceDescriptor
import com.star.schedule.feature.datasource.domain.OptionalResourceRepository
import com.star.schedule.feature.datasource.domain.OptionalResourceState
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 可选资源的默认实现：状态放内存 StateFlow（文件本身即持久化），
 * 下载跑在注入的 app 级 [scope] 里——离开页面下载继续、点完删除马上退出也不会删到一半。
 */
class DefaultOptionalResourceRepository(
    override val resources: List<OptionalResourceDescriptor>,
    private val scope: CoroutineScope,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : OptionalResourceRepository {

    private val states = ConcurrentHashMap<String, MutableStateFlow<OptionalResourceState>>()
    private val downloadJobs = ConcurrentHashMap<String, Job>()

    init {
        // 启动时在 IO 上探测一遍已安装文件，首帧不需要等磁盘；
        // 探测期间用户已点开下载的话不覆盖——只有仍是初始值才写。
        scope.launch(ioDispatcher) {
            resources.forEach { descriptor ->
                val resource = descriptor.resource
                val probed = resource.installedFile()
                    ?.let { OptionalResourceState.Installed(it, it.length()) }
                    ?: OptionalResourceState.NotInstalled
                stateFlow(resource.id).compareAndSet(
                    OptionalResourceState.NotInstalled,
                    probed,
                )
            }
        }
    }

    private fun stateFlow(id: String) = states.computeIfAbsent(id) {
        MutableStateFlow(OptionalResourceState.NotInstalled)
    }

    override fun observeState(id: String): Flow<OptionalResourceState> = stateFlow(id)

    override fun download(id: String) {
        val resource = find(id) ?: return
        if (downloadJobs[id]?.isActive == true) return
        val flow = stateFlow(id)
        // 先给出即时反馈再起任务：镜像连接阶段可能好几秒，期间也要显示
        // 「下载中」并允许取消，不能等第一个数据块。
        flow.value = OptionalResourceState.Downloading(0, resource.expectedSizeBytes)
        val job = scope.launch(ioDispatcher, start = CoroutineStart.LAZY) {
            try {
                resource.download { downloaded ->
                    flow.value = OptionalResourceState.Downloading(
                        downloadedBytes = downloaded,
                        totalBytes = resource.expectedSizeBytes,
                    )
                }
                flow.value = resource.installedFile()
                    ?.let { OptionalResourceState.Installed(it, it.length()) }
                    ?: OptionalResourceState.Failed("installed file missing after download")
            } catch (e: CancellationException) {
                flow.value = OptionalResourceState.NotInstalled
                throw e
            } catch (e: Exception) {
                flow.value = OptionalResourceState.Failed(
                    e.message ?: e.javaClass.simpleName,
                )
            } finally {
                // 只移除自己：后一次的下载 job 已顶进 map，不能误删。
                downloadJobs.remove(id, coroutineContext[Job])
            }
        }
        downloadJobs[id] = job
        job.start()
    }

    override fun cancelDownload(id: String) {
        downloadJobs[id]?.cancel() ?: return
        // 任务可能还没排到执行就被取消：那种情况下协程体压根不跑、
        // catch 不执行，由这里兜底拉回未安装；正在跑的协程自己也置同值，幂等。
        stateFlow(id).update { current ->
            if (current is OptionalResourceState.Downloading) {
                OptionalResourceState.NotInstalled
            } else {
                current
            }
        }
    }

    override fun delete(id: String) {
        val resource = find(id) ?: return
        scope.launch(ioDispatcher) {
            // 等下载协程真正收尾再删文件，避免取消还没生效时它正好在改名落地。
            downloadJobs[id]?.cancelAndJoin()
            resource.delete()
            stateFlow(id).value = OptionalResourceState.NotInstalled
        }
    }

    private fun find(id: String): OptionalResource? =
        resources.firstOrNull { it.resource.id == id }?.resource
}

package com.star.schedule.feature.datasource.data

import com.star.schedule.feature.datasource.domain.OptionalResource
import com.star.schedule.feature.datasource.domain.OptionalResourceDescriptor
import com.star.schedule.feature.datasource.domain.OptionalResourceState
import java.io.File
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DefaultOptionalResourceRepositoryTest {

    private class FakeResource(
        override val id: String = "fake",
        override val expectedSizeBytes: Long = 100L,
    ) : OptionalResource {
        var installed: File? = null
        var gate = CompletableDeferred<Unit>()
        var downloadError: Throwable? = null

        /** 下载协程体是否已收尾（含被取消）。 */
        var downloadEnded = false
        var deleteCalls = 0
        var downloadEndedBeforeDelete = false

        override fun installedFile(): File? = installed

        override suspend fun download(onProgress: (Long) -> Unit) {
            try {
                gate.await()
                downloadError?.let { throw it }
                installed = File("/tmp/$id")
            } finally {
                downloadEnded = true
            }
        }

        override suspend fun delete() {
            downloadEndedBeforeDelete = downloadEnded
            deleteCalls++
            installed = null
        }
    }

    private fun TestScope.repo(resource: FakeResource): DefaultOptionalResourceRepository =
        DefaultOptionalResourceRepository(
            resources = listOf(OptionalResourceDescriptor(resource, 0, 0)),
            scope = this,
            ioDispatcher = StandardTestDispatcher(testScheduler),
        )

    @Test
    fun `download flips state to Downloading immediately`() = runTest {
        val resource = FakeResource()
        val repo = repo(resource)

        repo.download("fake")

        assertEquals(
            OptionalResourceState.Downloading(0L, 100L),
            repo.observeState("fake").first(),
        )

        repo.cancelDownload("fake")  // 收尾，避免挂着的 job 拖住 runTest
        advanceUntilIdle()
    }

    @Test
    fun `successful download ends Installed`() = runTest {
        val resource = FakeResource()
        val repo = repo(resource)

        repo.download("fake")
        resource.gate.complete(Unit)
        advanceUntilIdle()

        val state = repo.observeState("fake").first()
        assertTrue(state is OptionalResourceState.Installed)
        assertEquals(resource.installed, (state as OptionalResourceState.Installed).file)
    }

    @Test
    fun `cancel during download returns to NotInstalled`() = runTest {
        val resource = FakeResource()
        val repo = repo(resource)

        repo.download("fake")
        repo.cancelDownload("fake")
        advanceUntilIdle()

        assertEquals(
            OptionalResourceState.NotInstalled,
            repo.observeState("fake").first(),
        )
    }

    @Test
    fun `delete waits for the download coroutine before removing files`() = runTest {
        val resource = FakeResource()
        val repo = repo(resource)

        repo.download("fake")
        repo.delete("fake")
        advanceUntilIdle()

        assertEquals(1, resource.deleteCalls)
        assertTrue(resource.downloadEndedBeforeDelete)
        assertEquals(
            OptionalResourceState.NotInstalled,
            repo.observeState("fake").first(),
        )
    }

    @Test
    fun `download failure surfaces Failed with the error message`() = runTest {
        val resource = FakeResource()
        resource.downloadError = IOException("boom")
        val repo = repo(resource)

        repo.download("fake")
        resource.gate.complete(Unit)
        advanceUntilIdle()

        val state = repo.observeState("fake").first()
        assertTrue(state is OptionalResourceState.Failed)
        assertEquals("boom", (state as OptionalResourceState.Failed).error)
    }
}

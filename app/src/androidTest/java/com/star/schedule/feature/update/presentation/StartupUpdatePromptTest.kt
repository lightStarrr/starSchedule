package com.star.schedule.feature.update.presentation

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.test.platform.app.InstrumentationRegistry
import com.star.schedule.R
import com.star.schedule.feature.settings.presentation.StartupHintUiState
import com.star.schedule.feature.update.domain.UpdateChannel
import com.star.schedule.feature.update.domain.UpdateChangelog
import com.star.schedule.feature.update.domain.UpdateCheckResult
import com.star.schedule.feature.update.domain.UpdateCheckTrigger
import com.star.schedule.feature.update.domain.UpdateRelease
import com.star.schedule.feature.update.domain.UpdateRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class StartupUpdatePromptTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val store = ViewModelStore()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private fun string(id: Int): String =
        InstrumentationRegistry.getInstrumentation().targetContext.getString(id)

    @After
    fun clear() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            store.clear()
            scope.cancel()
        }
    }

    private fun release() = UpdateRelease(
        releaseId = 1,
        metadataAssetId = 2,
        apkAssetId = 3,
        tag = "v1.3.0",
        channel = UpdateChannel.Stable,
        versionName = "1.3.0",
        versionCode = 130,
        commitSha = "b".repeat(40),
        apkName = "app.apk",
        apkSize = 100,
        apkSha256 = "d".repeat(64),
        apkDownloadUrl = "https://github.com/lightStarrr/starSchedule/releases/download/v1.3.0/app.apk",
        htmlUrl = "https://github.com/lightStarrr/starSchedule/releases/tag/v1.3.0",
        changelog = UpdateChangelog(),
    )

    @Test
    fun promptWaitsForInteractionAndSurvivesRecompositionWithoutRepeating() {
        val repository = object : UpdateRepository {
            val preference = MutableStateFlow(false)

            override suspend fun check(trigger: UpdateCheckTrigger) = UpdateCheckResult(
                release(), release(), emptyList(), 1, false,
            )

            override fun observeIncludeSnapshots() = preference

            override suspend fun setIncludeSnapshots(enabled: Boolean) {
                preference.value = enabled
            }
        }
        lateinit var owner: LifecycleOwner
        lateinit var registry: LifecycleRegistry
        lateinit var viewModel: StartupUpdateViewModel
        composeRule.runOnUiThread {
            owner = object : LifecycleOwner {
                override val lifecycle: Lifecycle
                    get() = registry
            }
            registry = LifecycleRegistry(owner).apply {
                currentState = Lifecycle.State.CREATED
            }
            viewModel = ViewModelProvider(
                store,
                StartupUpdateViewModelFactory(StartupUpdateGate(repository, scope), repository),
            )[StartupUpdateViewModel::class.java]
        }
        val hint = mutableStateOf<StartupHintUiState>(StartupHintUiState.Visible())
        var opened = 0
        composeRule.setContent {
            MaterialTheme {
                CompositionLocalProvider(LocalLifecycleOwner provides owner) {
                    StartupUpdateRoute(viewModel, hint.value) { opened++ }
                }
            }
        }
        val title = string(R.string.update_prompt_title)
        composeRule.onNodeWithText(title).assertDoesNotExist()
        composeRule.runOnIdle { registry.currentState = Lifecycle.State.RESUMED }
        composeRule.onNodeWithText(title).assertDoesNotExist()
        composeRule.runOnIdle { hint.value = StartupHintUiState.Hidden }
        composeRule.onNodeWithText(title).assertIsDisplayed()
        composeRule.runOnIdle { registry.currentState = Lifecycle.State.CREATED }
        composeRule.onNodeWithText(title).assertDoesNotExist()
        composeRule.runOnIdle { registry.currentState = Lifecycle.State.RESUMED }
        composeRule.onNodeWithText(title).assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.update_prompt_view)).performClick()
        assertEquals(1, opened)
        composeRule.onNodeWithText(title).assertDoesNotExist()
        composeRule.runOnIdle { hint.value = StartupHintUiState.Visible() }
        composeRule.runOnIdle { hint.value = StartupHintUiState.Hidden }
        composeRule.onNodeWithText(title).assertDoesNotExist()
    }
}

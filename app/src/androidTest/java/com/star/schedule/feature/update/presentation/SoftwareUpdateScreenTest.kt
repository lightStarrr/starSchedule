package com.star.schedule.feature.update.presentation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import android.text.format.Formatter
import com.star.schedule.R
import com.star.schedule.feature.update.domain.CurrentInstallation
import com.star.schedule.feature.update.domain.UpdateChannel
import com.star.schedule.feature.update.domain.UpdateChangelog
import com.star.schedule.feature.update.domain.UpdateCheckResult
import com.star.schedule.feature.update.domain.UpdateDownloadState
import com.star.schedule.feature.update.domain.UpdateDownloadFailure
import com.star.schedule.feature.update.domain.UpdateIssue
import com.star.schedule.feature.update.domain.UpdateIssueReason
import com.star.schedule.feature.update.domain.UpdateRelease
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class SoftwareUpdateScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun string(id: Int, vararg args: Any): String =
        InstrumentationRegistry.getInstrumentation().targetContext.getString(id, *args)

    private fun button(id: Int) = composeRule.onNode(hasText(string(id)) and hasClickAction())

    private fun showLog(text: String) {
        composeRule.onNodeWithTag("update-content").performScrollToNode(hasText(text))
        composeRule.onNodeWithText(text).assertIsDisplayed()
    }

    private val installed = CurrentInstallation(
        versionName = "1.2.0",
        versionCode = 120,
        commitSha = "a".repeat(40),
        channel = UpdateChannel.Stable,
    )

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
        changelog = UpdateChangelog(added = listOf("New thing"), fixed = listOf("Fix")),
    )

    private fun screen(
        state: SoftwareUpdateUiState,
        downloadState: UpdateDownloadState = UpdateDownloadState.Idle,
        onDownload: (UpdateRelease) -> Unit = {},
        onInstall: () -> Unit = {},
        onCancelDownload: () -> Unit = {},
        onCheck: () -> Unit = {},
        onConfirmPermission: () -> Unit = {},
        onOpenRelease: (UpdateRelease) -> Unit = {},
        onFailureShown: (Long) -> Unit = {},
        narrow: Boolean = false,
        dark: Boolean = false,
    ) {
        composeRule.setContent {
            MaterialTheme(colorScheme = if (dark) darkColorScheme() else lightColorScheme()) {
                Box(
                    modifier = (if (narrow) Modifier.width(320.dp) else Modifier)
                        .testTag("test-root"),
                ) {
                    SoftwareUpdateScreen(
                        state = state,
                        downloadState = downloadState,
                        onBack = {},
                        onCheckForUpdates = onCheck,
                        onIncludeSnapshotsChange = {},
                        onDownload = onDownload,
                        onCancelDownload = onCancelDownload,
                        onInstall = onInstall,
                        onOpenRelease = onOpenRelease,
                        onConfirmInstallPermission = onConfirmPermission,
                        onDismissInstallPermission = {},
                        onDownloadFailureShown = onFailureShown,
                    )
                }
            }
        }
    }

    @Test
    fun updateAvailableShowsTargetAndChangelog() {
        screen(
            state = SoftwareUpdateUiState(
                installed = installed,
                result = UpdateCheckResult(
                    target = release(),
                    displayedRelease = release(),
                    issues = emptyList(),
                    checkedAtEpochMillis = 1,
                    includeSnapshots = false,
                ),
            ),
        )
        showLog("New thing")
        button(R.string.update_download).assertIsDisplayed()
        composeRule.onAllNodesWithText("1.3.0", substring = true).assertCountEquals(1)
        composeRule.onAllNodesWithText("1.2.0", substring = true).assertCountEquals(0)
        composeRule.onNodeWithText(string(R.string.update_status_new_stable))
            .assertIsDisplayed()
        composeRule.onAllNodesWithText(
            string(R.string.update_recheck),
        ).assertCountEquals(0)
        composeRule.onAllNodesWithText(
            string(R.string.update_view_release_page),
        ).assertCountEquals(0)
    }

    @Test
    fun availableGithubAndDownloadActionsAreIndependent() {
        var opened: UpdateRelease? = null
        var downloaded: UpdateRelease? = null
        screen(
            state = SoftwareUpdateUiState(
                installed = installed,
                result = UpdateCheckResult(
                    target = release(), displayedRelease = release(),
                    issues = emptyList(), checkedAtEpochMillis = 1,
                    includeSnapshots = false,
                ),
            ),
            onDownload = { downloaded = it },
            onOpenRelease = { opened = it },
        )
        composeRule.onNodeWithContentDescription(
            string(R.string.update_open_github_download),
        ).performClick()
        assertEquals(release(), opened)
        assertNull(downloaded)

        button(R.string.update_download).performClick()
        assertEquals(release(), downloaded)
        assertEquals(release(), opened)
    }

    @Test
    fun narrowDarkAvailableActionsFit() {
        screen(
            state = SoftwareUpdateUiState(
                installed = installed,
                result = UpdateCheckResult(
                    target = release(), displayedRelease = release(),
                    issues = emptyList(), checkedAtEpochMillis = 1,
                    includeSnapshots = false,
                ),
            ),
            narrow = true,
            dark = true,
        )
        val root = composeRule.onNodeWithTag("test-root").fetchSemanticsNode().boundsInRoot
        val github = composeRule.onNodeWithContentDescription(
            string(R.string.update_open_github_download),
        ).assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val download = button(R.string.update_download)
            .assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        assertEquals(github.width, github.height, 1f)
        assertTrue(github.right <= download.left + 1)
        listOf(github, download).forEach { b ->
            assertTrue(b.left >= root.left - 1 && b.right <= root.right + 1)
            assertTrue(b.top >= root.top - 1 && b.bottom <= root.bottom + 1)
        }
    }

    @Test
    fun loadingShowsCheckingText() {
        screen(state = SoftwareUpdateUiState(installed = installed, isChecking = true))
        composeRule.onNode(hasText(string(R.string.update_checking)) and hasClickAction())
            .assertIsDisplayed()
            .assertIsNotEnabled()
    }

    @Test
    fun channelIssuesDoNotSurfaceWhenTargetExists() {
        screen(
            state = SoftwareUpdateUiState(
                installed = installed,
                result = UpdateCheckResult(
                    target = release(),
                    displayedRelease = release(),
                    issues = listOf(
                        UpdateIssue(
                            UpdateChannel.Snapshot,
                            UpdateIssueReason.RateLimited,
                            1_800_000_000_000L,
                        ),
                    ),
                    checkedAtEpochMillis = 1,
                    includeSnapshots = true,
                ),
            ),
        )
        composeRule.onNodeWithText(
            string(R.string.update_status_new_stable),
        ).assertIsDisplayed()
        button(R.string.update_download).assertIsDisplayed()
        showLog("New thing")
        composeRule.onAllNodesWithText("Retry", substring = true).assertCountEquals(0)
    }

    @Test
    fun upToDateShowsLatestTextAndCheckButton() {
        screen(
            state = SoftwareUpdateUiState(
                installed = installed,
                result = UpdateCheckResult(
                    target = null,
                    displayedRelease = release(),
                    issues = emptyList(),
                    checkedAtEpochMillis = 1,
                    includeSnapshots = false,
                ),
            ),
        )
        composeRule.onNodeWithText(
            string(R.string.update_latest_version),
        ).assertIsDisplayed()
        button(R.string.update_check_for_updates).assertIsDisplayed()
    }

    @Test
    fun totalCheckFailureShowsFailedTextAndCheckButton() {
        screen(
            state = SoftwareUpdateUiState(
                installed = installed,
                result = UpdateCheckResult(
                    target = null,
                    displayedRelease = null,
                    issues = listOf(
                        UpdateIssue(null, UpdateIssueReason.Network),
                    ),
                    checkedAtEpochMillis = 1,
                    includeSnapshots = false,
                ),
            ),
        )
        composeRule.onNodeWithText(
            string(R.string.update_status_check_failed_network),
        ).assertIsDisplayed()
        button(R.string.update_check_for_updates).assertIsDisplayed()
    }

    @Test
    fun permissionDialogShowsOnPrompt() {
        var confirmations = 0
        screen(
            state = SoftwareUpdateUiState(
                installed = installed,
                showInstallPermissionPrompt = true,
            ),
            onConfirmPermission = { confirmations++ },
        )
        composeRule.onNodeWithText(string(R.string.update_install_permission_title))
            .assertIsDisplayed()
        button(R.string.update_install_permission_confirm).performClick()
        assertEquals(1, confirmations)
    }

    @Test
    fun readyStateShowsInstallAction() {
        var installs = 0
        screen(
            state = SoftwareUpdateUiState(
                installed = installed,
                canInstallPackages = true,
                result = UpdateCheckResult(
                    target = release(), displayedRelease = release(),
                    issues = emptyList(), checkedAtEpochMillis = 1,
                    includeSnapshots = false,
                ),
            ),
            downloadState = UpdateDownloadState.Ready(release()),
            onInstall = { installs++ },
        )
        button(R.string.update_install).performClick()
        assertEquals(1, installs)
        composeRule.onAllNodesWithText(
            string(R.string.update_status_new_stable),
        ).assertCountEquals(0)
        composeRule.onAllNodesWithText(string(R.string.update_cancel)).assertCountEquals(0)
        composeRule.onAllNodesWithText(string(R.string.update_recheck)).assertCountEquals(0)
        composeRule.onAllNodesWithContentDescription(
            string(R.string.update_open_github_download),
        ).assertCountEquals(0)
    }

    @Test
    fun readyOnlyInstallActionFitsNarrowDarkScreen() {
        screen(
            state = SoftwareUpdateUiState(
                installed = installed,
                canInstallPackages = true,
                result = UpdateCheckResult(
                    target = release(), displayedRelease = release(),
                    issues = emptyList(), checkedAtEpochMillis = 1,
                    includeSnapshots = false,
                ),
            ),
            downloadState = UpdateDownloadState.Ready(release()),
            narrow = true,
            dark = true,
        )
        val root = composeRule.onNodeWithTag("test-root").fetchSemanticsNode().boundsInRoot
        val install = button(R.string.update_install)
            .assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        assertTrue(install.left >= root.left - 1 && install.right <= root.right + 1)
        assertTrue(install.top >= root.top - 1 && install.bottom <= root.bottom + 1)
        composeRule.onAllNodesWithText(string(R.string.update_cancel)).assertCountEquals(0)
        composeRule.onAllNodesWithText(string(R.string.update_recheck)).assertCountEquals(0)
        composeRule.onAllNodesWithContentDescription(
            string(R.string.update_open_github_download),
        ).assertCountEquals(0)
    }

    @Test
    fun restoringDisablesManualChecks() {
        screen(SoftwareUpdateUiState(installed, isRestoringDownloads = true))
        composeRule.onNode(hasText(string(R.string.update_checking)) and hasClickAction())
            .assertIsNotEnabled()
    }

    @Test
    fun emptyChangelogShowsAnExplicitEmptyMessage() {
        val target = release().copy(changelog = UpdateChangelog())
        screen(
            SoftwareUpdateUiState(
                installed,
                result = UpdateCheckResult(target, target, emptyList(), 1, false),
            ),
        )
        showLog(string(R.string.update_changelog_empty))
    }

    @Test
    fun knownDownloadSizeExposesDeterminateProgress() {
        screen(
            SoftwareUpdateUiState(installed),
            UpdateDownloadState.Downloading(release(), 7, 50, 100, false),
        )
        composeRule.onNode(
            SemanticsMatcher.expectValue(
                SemanticsProperties.ProgressBarRangeInfo,
                ProgressBarRangeInfo(0.5f, 0f..1f),
            ),
        ).assertIsDisplayed()
        composeRule.onAllNodesWithText(
            string(R.string.update_status_new_stable),
        ).assertCountEquals(0)
    }

    @Test
    fun unknownDownloadSizeExposesIndeterminateProgress() {
        screen(
            SoftwareUpdateUiState(installed),
            UpdateDownloadState.Downloading(release(), 7, 50, null, true),
        )
        composeRule.onNode(
            SemanticsMatcher.expectValue(
                SemanticsProperties.ProgressBarRangeInfo,
                ProgressBarRangeInfo.Indeterminate,
            ),
        ).assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.update_download_waiting)).assertIsDisplayed()
    }

    @Test
    fun checkingAnotherReleaseDoesNotReplaceTheDownloadTarget() {
        val active = release()
        val newer = release().copy(
            versionName = "1.4.0",
            changelog = UpdateChangelog(added = listOf("Another release")),
        )
        screen(
            SoftwareUpdateUiState(
                installed,
                result = UpdateCheckResult(newer, newer, emptyList(), 1, false),
            ),
            UpdateDownloadState.Downloading(active, 7, 10, 100, false),
        )
        composeRule.onNodeWithText(active.versionName).assertIsDisplayed()
        showLog("New thing")
        composeRule.onAllNodesWithText("Another release").assertCountEquals(0)
        composeRule.onAllNodesWithText(newer.versionName).assertCountEquals(0)
        composeRule.onAllNodesWithText(
            string(R.string.update_target_version, active.versionName),
        ).assertCountEquals(0)
    }

    @Test
    fun downloadingShowsInlineProgressAndCircularCancel() {
        var cancels = 0
        screen(
            SoftwareUpdateUiState(installed),
            UpdateDownloadState.Downloading(release(), 7, 50, 100, false),
            onCancelDownload = { cancels++ },
        )
        composeRule.onAllNodesWithContentDescription(
            string(R.string.update_open_github_download),
        ).assertCountEquals(0)
        composeRule.onAllNodesWithText(
            string(R.string.update_download),
        ).assertCountEquals(0)
        composeRule.onAllNodesWithText(
            string(R.string.update_cancel),
        ).assertCountEquals(0)
        val progress = composeRule.onNode(
            SemanticsMatcher.expectValue(
                SemanticsProperties.ProgressBarRangeInfo,
                ProgressBarRangeInfo(0.5f, 0f..1f),
            ),
        ).assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val cancel = composeRule.onNodeWithContentDescription(
            string(R.string.update_cancel),
        ).assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        assertEquals(cancel.width, cancel.height, 1f)
        assertEquals(progress.height, cancel.height, 1f)
        assertTrue(progress.right <= cancel.left + 1)
        val root = composeRule.onNodeWithTag("test-root").fetchSemanticsNode().boundsInRoot
        listOf(progress, cancel).forEach { b ->
            assertTrue(b.left >= root.left - 1 && b.right <= root.right + 1)
        }
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val label = string(
            R.string.update_download_progress,
            Formatter.formatShortFileSize(context, 50),
            Formatter.formatShortFileSize(context, 100),
        )
        val labelNode = composeRule.onAllNodesWithText(
            label,
            useUnmergedTree = true,
        ).assertCountEquals(1)[0].fetchSemanticsNode().boundsInRoot
        assertTrue(labelNode.left >= progress.left - 1 && labelNode.right <= progress.right + 1)
        assertTrue(labelNode.top >= progress.top - 1 && labelNode.bottom <= progress.bottom + 1)
        composeRule.onNodeWithContentDescription(
            string(R.string.update_cancel),
        ).performClick()
        assertEquals(1, cancels)
    }

    @Test
    fun downloadingActionRowFitsNarrowDarkScreen() {
        screen(
            SoftwareUpdateUiState(installed),
            UpdateDownloadState.Downloading(release(), 7, 50, 100, false),
            narrow = true,
            dark = true,
        )
        val root = composeRule.onNodeWithTag("test-root").fetchSemanticsNode().boundsInRoot
        val progress = composeRule.onNode(
            SemanticsMatcher.expectValue(
                SemanticsProperties.ProgressBarRangeInfo,
                ProgressBarRangeInfo(0.5f, 0f..1f),
            ),
        ).assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val cancel = composeRule.onNodeWithContentDescription(
            string(R.string.update_cancel),
        ).assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        assertEquals(cancel.width, cancel.height, 1f)
        assertEquals(progress.height, cancel.height, 1f)
        assertTrue(progress.right <= cancel.left + 1)
        listOf(progress, cancel).forEach { b ->
            assertTrue(b.left >= root.left - 1 && b.right <= root.right + 1)
            assertTrue(b.top >= root.top - 1 && b.bottom <= root.bottom + 1)
        }
    }

    @Test
    fun verifyingUsesFullCapsuleAndCircularCancel() {
        var cancels = 0
        screen(
            SoftwareUpdateUiState(installed),
            UpdateDownloadState.Verifying(release()),
            onCancelDownload = { cancels++ },
        )
        val progress = composeRule.onNode(
            SemanticsMatcher.expectValue(
                SemanticsProperties.ProgressBarRangeInfo,
                ProgressBarRangeInfo(1f, 0f..1f),
            ),
        ).assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val label = composeRule.onAllNodesWithText(
            string(R.string.update_download_verifying),
            useUnmergedTree = true,
        ).assertCountEquals(1)[0].fetchSemanticsNode().boundsInRoot
        assertTrue(label.left >= progress.left - 1 && label.right <= progress.right + 1)
        val cancel = composeRule.onNodeWithContentDescription(
            string(R.string.update_cancel),
        ).assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        assertEquals(progress.height, cancel.height, 1f)
        composeRule.onAllNodesWithText(
            string(R.string.update_download),
        ).assertCountEquals(0)
        composeRule.onAllNodesWithContentDescription(
            string(R.string.update_open_github_download),
        ).assertCountEquals(0)
        composeRule.onAllNodesWithText(
            string(R.string.update_cancel),
        ).assertCountEquals(0)
        composeRule.onAllNodesWithText(
            string(R.string.update_status_new_stable),
        ).assertCountEquals(0)
        composeRule.onNodeWithContentDescription(
            string(R.string.update_cancel),
        ).performClick()
        assertEquals(1, cancels)
    }

    @Test
    fun verifyingCapsuleFitsNarrowDarkScreen() {
        screen(
            SoftwareUpdateUiState(installed),
            UpdateDownloadState.Verifying(release()),
            narrow = true,
            dark = true,
        )
        val root = composeRule.onNodeWithTag("test-root").fetchSemanticsNode().boundsInRoot
        val progress = composeRule.onNode(
            SemanticsMatcher.expectValue(
                SemanticsProperties.ProgressBarRangeInfo,
                ProgressBarRangeInfo(1f, 0f..1f),
            ),
        ).assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val cancel = composeRule.onNodeWithContentDescription(
            string(R.string.update_cancel),
        ).assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        assertEquals(progress.height, cancel.height, 1f)
        val label = composeRule.onAllNodesWithText(
            string(R.string.update_download_verifying),
            useUnmergedTree = true,
        ).assertCountEquals(1)[0].fetchSemanticsNode().boundsInRoot
        assertTrue(label.left >= progress.left - 1 && label.right <= progress.right + 1)
        listOf(progress, cancel).forEach { b ->
            assertTrue(b.left >= root.left - 1 && b.right <= root.right + 1)
        }
    }

    @Test
    fun verifyingRetainsCompletedBarWhenLabelChanges() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val downloadLabel = context.getString(
            R.string.update_download_progress,
            Formatter.formatShortFileSize(context, 100),
            Formatter.formatShortFileSize(context, 100),
        )
        var cancels = 0
        val downloadState = mutableStateOf<UpdateDownloadState>(
            UpdateDownloadState.Downloading(release(), 7, 100, 100, false),
        )
        composeRule.setContent {
            MaterialTheme {
                Box(modifier = Modifier.testTag("test-root")) {
                    UpdateActionArea(
                        state = SoftwareUpdateUiState(installed),
                        downloadState = downloadState.value,
                        target = release(),
                        onCheckForUpdates = {},
                        onDownload = {},
                        onCancelDownload = { cancels++ },
                        onInstall = {},
                        onOpenRelease = {},
                    )
                }
            }
        }
        val before = composeRule.onNode(
            SemanticsMatcher.expectValue(
                SemanticsProperties.ProgressBarRangeInfo,
                ProgressBarRangeInfo(1f, 0f..1f),
            ),
        ).assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        composeRule.onNodeWithText(downloadLabel).assertIsDisplayed()
        val cancelBefore = composeRule.onNodeWithContentDescription(
            string(R.string.update_cancel),
        ).fetchSemanticsNode().boundsInRoot

        composeRule.runOnIdle {
            downloadState.value = UpdateDownloadState.Verifying(release())
        }
        val after = composeRule.onNode(
            SemanticsMatcher.expectValue(
                SemanticsProperties.ProgressBarRangeInfo,
                ProgressBarRangeInfo(1f, 0f..1f),
            ),
        ).assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        assertEquals(before, after)
        composeRule.onAllNodesWithText(downloadLabel).assertCountEquals(0)
        composeRule.onAllNodesWithText(
            string(R.string.update_download_verifying),
        ).assertCountEquals(1)
        val cancelAfter = composeRule.onNodeWithContentDescription(
            string(R.string.update_cancel),
        ).fetchSemanticsNode().boundsInRoot
        assertEquals(cancelBefore, cancelAfter)
    }

    @Test
    fun preparingDisablesInstallationAndLaunchFailureIsVisible() {
        screen(
            SoftwareUpdateUiState(
                installed,
                isPreparingInstall = true,
                installLaunchFailed = true,
            ),
            UpdateDownloadState.Ready(release()),
        )
        composeRule.onNode(
            hasText(string(R.string.update_download_verifying)) and hasClickAction(),
        ).assertIsNotEnabled()
        composeRule.onNodeWithText(string(R.string.update_open_installer_failed))
            .assertIsDisplayed()
    }

    @Test
    fun failedDownloadActionsFitANarrowDarkScreen() {
        var opened: UpdateRelease? = null
        var downloaded: UpdateRelease? = null
        val newer = release().copy(versionName = "1.4.0")
        screen(
            SoftwareUpdateUiState(
                installed,
                result = UpdateCheckResult(
                    newer, newer, emptyList(), 1, false,
                ),
            ),
            UpdateDownloadState.Failed(release(), UpdateDownloadFailure.HashMismatch),
            onDownload = { downloaded = it },
            onOpenRelease = { opened = it },
            narrow = true,
            dark = true,
        )
        val root = composeRule.onNodeWithTag("test-root").fetchSemanticsNode().boundsInRoot
        val github = composeRule.onNodeWithContentDescription(
            string(R.string.update_open_github_download),
        ).assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val download = button(R.string.update_download)
            .assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        listOf(github, download).forEach { b ->
            assertTrue(b.left >= root.left - 1 && b.right <= root.right + 1)
        }
        composeRule.onAllNodesWithText(string(R.string.update_retry_download)).assertCountEquals(0)
        composeRule.onAllNodesWithText(string(R.string.update_cancel)).assertCountEquals(0)
        composeRule.onAllNodesWithText(string(R.string.update_recheck)).assertCountEquals(0)
        composeRule.onAllNodesWithText(string(R.string.update_download_failed_hash))
            .assertCountEquals(0)
        composeRule.onNodeWithContentDescription(
            string(R.string.update_open_github_download),
        ).performClick()
        assertEquals(release(), opened)
        button(R.string.update_download).performClick()
        assertEquals(release(), downloaded)
    }

    @Test
    fun failedDownloadShowsBarAndRestoresActions() {
        var shown: Long? = null
        val uiState = mutableStateOf(
            SoftwareUpdateUiState(
                installed,
                result = UpdateCheckResult(
                    release(), release(), emptyList(), 1, false,
                ),
                downloadFailureMessage = UpdateDownloadFailureMessage(
                    1L, UpdateDownloadFailure.HashMismatch,
                ),
            ),
        )
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            MaterialTheme {
                Box(modifier = Modifier.testTag("test-root")) {
                    SoftwareUpdateScreen(
                        state = uiState.value,
                        downloadState = UpdateDownloadState.Failed(
                            release(), UpdateDownloadFailure.HashMismatch,
                        ),
                        onBack = {},
                        onCheckForUpdates = {},
                        onIncludeSnapshotsChange = {},
                        onDownload = {},
                        onCancelDownload = {},
                        onInstall = {},
                        onOpenRelease = {},
                        onConfirmInstallPermission = {},
                        onDismissInstallPermission = {},
                        onDownloadFailureShown = { shown = it },
                    )
                }
            }
        }
        composeRule.mainClock.advanceTimeBy(250)
        composeRule.onNodeWithText(
            string(R.string.update_download_failed_hash),
        ).assertIsDisplayed()
        composeRule.onNodeWithContentDescription(
            string(R.string.update_open_github_download),
        ).assertIsDisplayed()
        button(R.string.update_download).assertIsDisplayed()
        composeRule.mainClock.advanceTimeBy(4500)
        composeRule.waitForIdle()
        assertEquals(1L, shown)
        composeRule.onAllNodesWithText(
            string(R.string.update_download_failed_hash),
        ).assertCountEquals(0)
        button(R.string.update_download).assertIsDisplayed()
    }

    @Test
    fun failedDownloadSameReasonRestartsCountdown() {
        val shown = mutableListOf<Long>()
        val uiState = mutableStateOf(
            SoftwareUpdateUiState(
                installed,
                result = UpdateCheckResult(
                    release(), release(), emptyList(), 1, false,
                ),
                downloadFailureMessage = UpdateDownloadFailureMessage(
                    1L, UpdateDownloadFailure.HashMismatch,
                ),
            ),
        )
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            MaterialTheme {
                Box(modifier = Modifier.testTag("test-root")) {
                    SoftwareUpdateScreen(
                        state = uiState.value,
                        downloadState = UpdateDownloadState.Failed(
                            release(), UpdateDownloadFailure.HashMismatch,
                        ),
                        onBack = {},
                        onCheckForUpdates = {},
                        onIncludeSnapshotsChange = {},
                        onDownload = {
                            uiState.value = uiState.value.copy(
                                downloadFailureMessage = UpdateDownloadFailureMessage(
                                    2L, UpdateDownloadFailure.HashMismatch,
                                ),
                            )
                        },
                        onCancelDownload = {},
                        onInstall = {},
                        onOpenRelease = {},
                        onConfirmInstallPermission = {},
                        onDismissInstallPermission = {},
                        onDownloadFailureShown = { token ->
                            shown += token
                            uiState.value = uiState.value.copy(
                                downloadFailureMessage = null,
                            )
                        },
                    )
                }
            }
        }
        composeRule.mainClock.advanceTimeBy(250)
        composeRule.onNodeWithText(
            string(R.string.update_download_failed_hash),
        ).assertIsDisplayed()
        composeRule.mainClock.advanceTimeBy(2500)
        button(R.string.update_download).performClick()
        composeRule.mainClock.advanceTimeBy(250)
        composeRule.mainClock.advanceTimeBy(2000)
        composeRule.onNodeWithText(
            string(R.string.update_download_failed_hash),
        ).assertIsDisplayed()
        assertTrue(1L !in shown)
        composeRule.mainClock.advanceTimeBy(2500)
        composeRule.waitForIdle()
        composeRule.onAllNodesWithText(
            string(R.string.update_download_failed_hash),
        ).assertCountEquals(0)
        assertEquals(listOf(2L), shown)
        button(R.string.update_download).assertIsDisplayed()
    }

    @Test
    fun failedDownloadBarFitsNarrowDarkScreen() {
        composeRule.mainClock.autoAdvance = false
        screen(
            SoftwareUpdateUiState(
                installed,
                downloadFailureMessage = UpdateDownloadFailureMessage(
                    1L, UpdateDownloadFailure.HashMismatch,
                ),
            ),
            UpdateDownloadState.Failed(release(), UpdateDownloadFailure.HashMismatch),
            narrow = true,
            dark = true,
        )
        composeRule.mainClock.advanceTimeBy(1000)
        composeRule.onNodeWithText(
            string(R.string.update_download_failed_hash),
        ).assertIsDisplayed()
        composeRule.onNodeWithContentDescription(
            string(R.string.update_open_github_download),
        ).assertIsDisplayed()
        button(R.string.update_download).assertIsDisplayed()
    }

}

package com.star.schedule.feature.update.presentation

import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.provider.Settings
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.star.schedule.SoftwareUpdateActivity
import com.star.schedule.platform.update.AndroidUpdateInstaller
import com.star.schedule.platform.update.AndroidUpdatePackageVerifier
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class SoftwareUpdatePlatformTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    private fun updatesDirectory(): File =
        File(requireNotNull(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)), "updates")
            .apply { mkdirs() }

    private fun waitForPackage(predicate: (String) -> Boolean): String {
        val deadline = SystemClock.uptimeMillis() + 10_000
        var packageName = ""
        while (SystemClock.uptimeMillis() < deadline) {
            packageName = instrumentation.uiAutomation.rootInActiveWindow
                ?.packageName?.toString().orEmpty()
            if (predicate(packageName)) return packageName
            SystemClock.sleep(100)
        }
        throw AssertionError("Unexpected foreground package: $packageName")
    }

    private fun captureScreenshot(name: String) {
        instrumentation.uiAutomation.waitForIdle(1_000, 10_000)
        val screenshot = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
        File(requireNotNull(context.externalCacheDir), name).outputStream().use {
            screenshot.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        screenshot.recycle()
    }

    private fun waitForVisibleText(text: String) {
        val deadline = SystemClock.uptimeMillis() + 10_000
        while (SystemClock.uptimeMillis() < deadline) {
            val nodes = instrumentation.uiAutomation.rootInActiveWindow
                ?.findAccessibilityNodeInfosByText(text).orEmpty()
            if (nodes.any { it.isVisibleToUser }) return
            SystemClock.sleep(100)
        }
        captureScreenshot("update-platform-failure.png")
        throw AssertionError("Expected visible system UI text: $text")
    }

    private fun goBack() {
        ParcelFileDescriptor.AutoCloseInputStream(
            instrumentation.uiAutomation.executeShellCommand("input keyevent KEYCODE_BACK"),
        ).use { it.readBytes() }
    }

    @Test
    fun archiveAndInstalledPackageExposeTheSameNonemptySigningCertificates() = runBlocking(Dispatchers.IO) {
        val verifier = AndroidUpdatePackageVerifier(context)
        val archive = requireNotNull(verifier.packageFacts(context.applicationInfo.sourceDir))
        val installed = requireNotNull(verifier.installedPackageFacts())
        assertEquals(context.packageName, archive.packageName)
        assertEquals(installed.packageName, archive.packageName)
        assertEquals(installed.versionName, archive.versionName)
        assertEquals(installed.versionCode, archive.versionCode)
        assertTrue(archive.signerSha256.isNotEmpty())
        assertEquals(installed.signerSha256, archive.signerSha256)
    }

    @Test
    fun installerUsesARestrictedContentUriAndPackageScopedPermissionSettings() {
        val directory = updatesDirectory()
        val installer = AndroidUpdateInstaller(context, directory)
        val owned = File.createTempFile("provider-test-", ".apk", directory)
        val outside = File.createTempFile("outside-test-", ".apk", context.cacheDir)
        try {
            val bytes = byteArrayOf(1, 2, 3, 4)
            owned.writeBytes(bytes)
            val intent = installer.installIntent(owned.absolutePath)
            assertEquals(Intent.ACTION_VIEW, intent.action)
            assertEquals("application/vnd.android.package-archive", intent.type)
            val uri = requireNotNull(intent.data)
            assertEquals("content", uri.scheme)
            assertEquals("${context.packageName}.updates.fileprovider", uri.authority)
            assertTrue(intent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
            val restored = context.contentResolver.openInputStream(uri)!!.use { it.readBytes() }
            assertTrue(bytes.contentEquals(restored))
            val provider = context.packageManager.resolveContentProvider(
                uri.authority!!,
                PackageManager.ComponentInfoFlags.of(0),
            )
            assertNotNull(provider)
            assertFalse(provider!!.exported)
            assertTrue(provider.grantUriPermissions)
            assertThrows(IllegalArgumentException::class.java) {
                installer.installIntent(outside.absolutePath)
            }
            val settings = installer.unknownSourcesIntent()
            assertEquals(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, settings.action)
            assertEquals("package:${context.packageName}", settings.dataString)
        } finally {
            owned.delete()
            outside.delete()
        }
    }

    @Test
    fun systemInstallerOpensAnOwnedSignedApkWithoutConfirmingInstallation() {
        val directory = updatesDirectory()
        val owned = File.createTempFile("installer-test-", ".apk", directory)
        try {
            File(context.applicationInfo.sourceDir).copyTo(owned, overwrite = true)
            context.startActivity(AndroidUpdateInstaller(context, directory).installIntent(owned.path))
            waitForPackage {
                it.contains("packageinstaller") || it.contains("permissioncontroller")
            }
            waitForVisibleText(context.applicationInfo.loadLabel(context.packageManager).toString())
            captureScreenshot("update-installer-entry.png")
        } finally {
            goBack()
            owned.delete()
        }
    }

    @Test
    fun updatePageReturnsFromPackageScopedSettingsWithoutStartingAnInstaller() {
        ActivityScenario.launch<SoftwareUpdateActivity>(
            Intent(context, SoftwareUpdateActivity::class.java),
        ).use { scenario ->
            waitForPackage { it == context.packageName }
            instrumentation.waitForIdleSync()
            scenario.onActivity { activity ->
                assertTrue(activity.window.decorView.isShown)
            }
            captureScreenshot("update-page.png")
            scenario.onActivity { activity ->
                activity.startActivity(
                    AndroidUpdateInstaller(context, updatesDirectory()).unknownSourcesIntent(),
                )
            }
            try {
                waitForPackage { it.contains("settings") }
                instrumentation.waitForIdleSync()
                captureScreenshot("update-install-settings.png")
            } finally {
                goBack()
            }
            waitForPackage { it == context.packageName }
            scenario.onActivity { activity ->
                assertTrue(activity.lifecycle.currentState.isAtLeast(
                    androidx.lifecycle.Lifecycle.State.RESUMED,
                ))
            }
        }
    }
}

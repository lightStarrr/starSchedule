package com.star.schedule.platform.update

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import java.io.File

class AndroidUpdateInstaller(
    context: Context,
    private val updatesDirectory: File?,
) {
    private val appContext = context.applicationContext

    fun canRequestPackageInstalls(): Boolean =
        appContext.packageManager.canRequestPackageInstalls()

    fun unknownSourcesIntent(): Intent = Intent(
        Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
        "package:${appContext.packageName}".toUri(),
    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    fun installIntent(localPath: String): Intent {
        val directory = updatesDirectory
            ?: throw IllegalStateException("updates directory unavailable")
        val canonical = File(localPath).canonicalFile
        val root = directory.canonicalFile.path + File.separator
        require(canonical.path.startsWith(root)) { "path outside updates dir" }
        val uri = FileProvider.getUriForFile(
            appContext,
            "${appContext.packageName}.updates.fileprovider",
            canonical,
        )
        return Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, APK_MIME_TYPE)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    private companion object {
        const val APK_MIME_TYPE = "application/vnd.android.package-archive"
    }
}

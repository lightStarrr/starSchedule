package com.star.schedule.platform.update

import android.content.Context
import android.content.pm.PackageManager
import com.star.schedule.feature.update.domain.UpdatePackageFacts
import com.star.schedule.feature.update.domain.UpdatePackageVerifier
import java.security.MessageDigest
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AndroidUpdatePackageVerifier(
    context: Context,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : UpdatePackageVerifier {
    private val appContext = context.applicationContext

    override suspend fun packageFacts(filePath: String): UpdatePackageFacts? =
        withContext(ioDispatcher) {
            val info = appContext.packageManager.getPackageArchiveInfo(
                filePath,
                PackageManager.PackageInfoFlags.of(PackageManager.GET_SIGNING_CERTIFICATES.toLong()),
            ) ?: return@withContext null
            val signers = info.signingInfo?.apkContentsSigners
                ?.map { sha256Hex(it.toByteArray()) }?.toSet()
                ?: return@withContext null
            UpdatePackageFacts(
                packageName = info.packageName,
                versionName = info.versionName.orEmpty(),
                versionCode = info.longVersionCode,
                signerSha256 = signers,
            )
        }

    override suspend fun installedPackageFacts(): UpdatePackageFacts? =
        withContext(ioDispatcher) {
            val info = appContext.packageManager.getPackageInfo(
                appContext.packageName,
                PackageManager.PackageInfoFlags.of(PackageManager.GET_SIGNING_CERTIFICATES.toLong()),
            )
            val signers = info.signingInfo?.apkContentsSigners
                ?.map { sha256Hex(it.toByteArray()) }?.toSet()
                ?: return@withContext null
            UpdatePackageFacts(
                packageName = info.packageName,
                versionName = info.versionName.orEmpty(),
                versionCode = info.longVersionCode,
                signerSha256 = signers,
            )
        }

    private fun sha256Hex(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString("") { "%02x".format(it) }
}

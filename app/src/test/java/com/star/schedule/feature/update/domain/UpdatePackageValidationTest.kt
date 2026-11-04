package com.star.schedule.feature.update.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UpdatePackageValidationTest {
    private val sha = "a".repeat(64)
    private val signers = setOf("f".repeat(64))

    private fun target() = UpdateRelease(
        releaseId = 1,
        metadataAssetId = 2,
        apkAssetId = 3,
        tag = "v1.3.0",
        channel = UpdateChannel.Stable,
        versionName = "1.3.0",
        versionCode = 130,
        commitSha = "b".repeat(40),
        apkName = "app.apk",
        apkSize = 1024,
        apkSha256 = sha,
        apkDownloadUrl = "https://github.com/lightStarrr/starSchedule/releases/download/v1.3.0/app.apk",
        htmlUrl = "https://github.com/lightStarrr/starSchedule/releases/tag/v1.3.0",
        changelog = UpdateChangelog(),
    )

    private fun facts(
        packageName: String = "com.star.schedule",
        versionName: String = "1.3.0",
        versionCode: Long = 130,
        signerSha256: Set<String> = signers,
    ) = UpdatePackageFacts(packageName, versionName, versionCode, signerSha256)

    private fun validate(
        fileSize: Long = 1024,
        fileSha256: String = sha,
        facts: UpdatePackageFacts = facts(),
        installedCode: Long = 120,
        signers: Set<String> = this.signers,
    ) = UpdatePackageValidation.validate(
        fileSize = fileSize,
        fileSha256 = fileSha256,
        facts = facts,
        target = target(),
        installedVersionCode = installedCode,
        expectedPackageName = "com.star.schedule",
        expectedSigners = signers,
    )

    @Test
    fun `matching package passes`() {
        assertNull(validate())
    }

    @Test
    fun `size mismatch rejects`() {
        assertEquals(UpdateDownloadFailure.SizeMismatch, validate(fileSize = 999))
    }

    @Test
    fun `hash mismatch rejects`() {
        assertEquals(UpdateDownloadFailure.HashMismatch, validate(fileSha256 = "c".repeat(64)))
    }

    @Test
    fun `package name mismatch rejects`() {
        assertEquals(
            UpdateDownloadFailure.PackageMismatch,
            validate(facts = facts(packageName = "com.other.app")),
        )
    }

    @Test
    fun `version name mismatch rejects`() {
        assertEquals(
            UpdateDownloadFailure.VersionMismatch,
            validate(facts = facts(versionName = "1.4.0")),
        )
    }

    @Test
    fun `version code mismatch rejects`() {
        assertEquals(
            UpdateDownloadFailure.VersionMismatch,
            validate(facts = facts(versionCode = 131)),
        )
    }

    @Test
    fun `code below the actual installed code rejects`() {
        assertEquals(
            UpdateDownloadFailure.VersionMismatch,
            validate(facts = facts(versionCode = 130), installedCode = 131),
        )
    }

    @Test
    fun `same code as installed is allowed`() {
        assertNull(validate(facts = facts(versionCode = 130), installedCode = 130))
    }

    @Test
    fun `different signer set rejects`() {
        assertEquals(
            UpdateDownloadFailure.SignatureMismatch,
            validate(facts = facts(signerSha256 = setOf("e".repeat(64)))),
        )
    }

    @Test
    fun `empty signers on either side rejects`() {
        assertEquals(
            UpdateDownloadFailure.SignatureMismatch,
            validate(facts = facts(signerSha256 = emptySet())),
        )
        assertEquals(
            UpdateDownloadFailure.SignatureMismatch,
            validate(signers = emptySet()),
        )
        assertEquals(
            UpdateDownloadFailure.SignatureMismatch,
            validate(facts = facts(signerSha256 = emptySet()), signers = emptySet()),
        )
    }
}

package com.star.schedule.feature.update.domain

enum class UpdateChannel { Stable, Snapshot }

data class CurrentInstallation(
    val versionName: String,
    val versionCode: Long,
    val commitSha: String,
    val channel: UpdateChannel,
)

data class UpdateChangelog(
    val added: List<String> = emptyList(),
    val changed: List<String> = emptyList(),
    val fixed: List<String> = emptyList(),
)

data class UpdateRelease(
    val releaseId: Long,
    val metadataAssetId: Long,
    val apkAssetId: Long,
    val tag: String,
    val channel: UpdateChannel,
    val versionName: String,
    val versionCode: Long,
    val commitSha: String,
    val apkName: String,
    val apkSize: Long,
    val apkSha256: String,
    val apkDownloadUrl: String,
    val htmlUrl: String,
    val changelog: UpdateChangelog,
) {
    val identity: String
        get() = "$releaseId:$metadataAssetId:$apkAssetId:$apkSha256"
}

enum class UpdateIssueReason {
    Network,
    RateLimited,
    ReleaseUnavailable,
    MetadataUnavailable,
    InvalidMetadata,
    UnknownCommitRelation,
}

data class UpdateIssue(
    val channel: UpdateChannel?,
    val reason: UpdateIssueReason,
    val retryAtEpochMillis: Long? = null,
)

enum class UpdateCheckTrigger { ColdStart, PageEnter, Manual }

data class UpdateCheckResult(
    val target: UpdateRelease?,
    val displayedRelease: UpdateRelease?,
    val issues: List<UpdateIssue>,
    val checkedAtEpochMillis: Long,
    val includeSnapshots: Boolean,
)

enum class CommitRelation { Ahead, Behind, Identical, Diverged, Unknown }

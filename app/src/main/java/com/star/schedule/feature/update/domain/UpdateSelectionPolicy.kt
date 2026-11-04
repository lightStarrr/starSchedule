package com.star.schedule.feature.update.domain

object UpdateSelectionPolicy {

    fun select(
        installed: CurrentInstallation,
        includeSnapshots: Boolean,
        stable: UpdateRelease?,
        snapshot: UpdateRelease?,
        snapshotVsInstalled: CommitRelation?,
        snapshotVsStable: CommitRelation?,
    ): SelectionResult {
        val issues = mutableListOf<UpdateIssue>()
        val stableEligible = stable != null && stable.versionCode > installed.versionCode
        val snapshotEligible = snapshotEligible(
            installed = installed,
            includeSnapshots = includeSnapshots,
            snapshot = snapshot,
            snapshotVsInstalled = snapshotVsInstalled,
            issues = issues,
        )

        val target = when {
            stableEligible && snapshotEligible -> {
                requireNotNull(stable)
                requireNotNull(snapshot)
                if (snapshot.versionCode != stable.versionCode) {
                    if (snapshot.versionCode > stable.versionCode) snapshot else stable
                } else if (snapshot.commitSha == stable.commitSha) {
                    stable
                } else {
                    when (snapshotVsStable ?: CommitRelation.Unknown) {
                        CommitRelation.Ahead -> snapshot
                        CommitRelation.Behind, CommitRelation.Identical -> stable
                        CommitRelation.Diverged, CommitRelation.Unknown -> {
                            issues += UpdateIssue(
                                channel = UpdateChannel.Snapshot,
                                reason = UpdateIssueReason.UnknownCommitRelation,
                            )
                            stable
                        }
                    }
                }
            }

            stableEligible -> stable
            snapshotEligible -> snapshot
            else -> null
        }

        return SelectionResult(
            target = target,
            displayedRelease = target ?: stable ?: snapshot,
            issues = issues,
        )
    }

    private fun snapshotEligible(
        installed: CurrentInstallation,
        includeSnapshots: Boolean,
        snapshot: UpdateRelease?,
        snapshotVsInstalled: CommitRelation?,
        issues: MutableList<UpdateIssue>,
    ): Boolean {
        if (!includeSnapshots || snapshot == null) return false
        if (snapshot.versionCode != installed.versionCode) {
            return snapshot.versionCode > installed.versionCode
        }
        if (snapshot.commitSha == installed.commitSha) return false
        if (installed.commitSha.isBlank() || snapshot.commitSha.isBlank()) {
            issues += UpdateIssue(
                channel = UpdateChannel.Snapshot,
                reason = UpdateIssueReason.UnknownCommitRelation,
            )
            return false
        }
        return when (snapshotVsInstalled ?: CommitRelation.Unknown) {
            CommitRelation.Ahead -> true
            CommitRelation.Identical, CommitRelation.Behind -> false
            CommitRelation.Diverged, CommitRelation.Unknown -> {
                issues += UpdateIssue(
                    channel = UpdateChannel.Snapshot,
                    reason = UpdateIssueReason.UnknownCommitRelation,
                )
                false
            }
        }
    }

    data class SelectionResult(
        val target: UpdateRelease?,
        val displayedRelease: UpdateRelease?,
        val issues: List<UpdateIssue>,
    )
}

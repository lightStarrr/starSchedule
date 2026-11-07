package com.star.schedule.feature.update.domain

object UpdateSelectionPolicy {

    fun select(
        installed: CurrentInstallation,
        includeSnapshots: Boolean,
        stable: UpdateRelease?,
        snapshot: UpdateRelease?,
    ): SelectionResult {
        val stableEligible = stable != null && stable.versionCode > installed.versionCode
        val snapshotEligible = includeSnapshots && snapshot != null && when {
            snapshot.versionCode != installed.versionCode ->
                snapshot.versionCode > installed.versionCode

            // 版本号相同就不分谁新谁旧：sha 不同就是另一个构建，当成新版。
            else -> snapshot.commitSha != installed.commitSha
        }

        val target = when {
            stableEligible && snapshotEligible -> {
                requireNotNull(stable)
                requireNotNull(snapshot)
                when {
                    snapshot.versionCode != stable.versionCode ->
                        if (snapshot.versionCode > stable.versionCode) snapshot else stable

                    // 同一个版本号下 sha 相同就是同一份代码，优先给正式版；
                    // 不同则快照走开发线，用户已勾选检测快照。
                    snapshot.commitSha == stable.commitSha -> stable
                    else -> snapshot
                }
            }

            stableEligible -> stable
            snapshotEligible -> snapshot
            else -> null
        }

        return SelectionResult(
            target = target,
            displayedRelease = target ?: stable ?: snapshot,
        )
    }

    data class SelectionResult(
        val target: UpdateRelease?,
        val displayedRelease: UpdateRelease?,
    )
}

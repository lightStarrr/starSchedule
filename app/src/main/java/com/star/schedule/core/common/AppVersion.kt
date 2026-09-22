package com.star.schedule.core.common

fun isNewerVersion(latestTag: String, currentVersion: String): Boolean {
    val latestParts = latestTag.trimStart('v', 'V').split(".")
    val currentParts = currentVersion.trimStart('v', 'V').split(".")
    val partCount = maxOf(latestParts.size, currentParts.size)

    for (index in 0 until partCount) {
        val latestPart = latestParts.getOrNull(index)?.toIntOrNull() ?: 0
        val currentPart = currentParts.getOrNull(index)?.toIntOrNull() ?: 0
        if (latestPart > currentPart) return true
        if (latestPart < currentPart) return false
    }

    return false
}

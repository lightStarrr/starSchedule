package com.star.schedule.wakeup

import android.content.Context
import android.os.Build

object WakeUpSupport {
    const val PROXY_PACKAGE = "com.suda.yzune.wakeupschedule"
    const val PROXY_AUTHORITY = "$PROXY_PACKAGE.provider"

    fun isColorOs(): Boolean {
        val manufacturer = Build.MANUFACTURER.orEmpty().lowercase()
        val brand = Build.BRAND.orEmpty().lowercase()
        if (manufacturer in COLOR_OS_BRANDS || brand in COLOR_OS_BRANDS) return true
        return runCatching {
            val properties = Class.forName("android.os.SystemProperties")
            val get = properties.getMethod("get", String::class.java)
            (get.invoke(null, "ro.build.version.opporom") as? String).orEmpty().isNotBlank()
        }.getOrDefault(false)
    }

    fun isProxyInstalled(context: Context): Boolean =
        context.packageManager.resolveContentProvider(PROXY_AUTHORITY, 0) != null

    private val COLOR_OS_BRANDS = setOf("oppo", "realme", "oneplus")
}

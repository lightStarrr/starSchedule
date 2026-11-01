package com.star.schedule.core.network

import android.os.Build
import com.star.schedule.BuildConfig
import java.util.Locale
import java.util.TimeZone

/** 唯一读取 `android.os.Build` 的地方，所以不参与 JVM 单测。 */
object AndroidClientInfo {

    fun read(): ClientInfo = ClientInfo(
        versionName = BuildConfig.VERSION_NAME,
        versionCode = BuildConfig.VERSION_CODE,
        osVersion = Build.VERSION.RELEASE.orEmpty(),
        sdkInt = Build.VERSION.SDK_INT,
        arch = Build.SUPPORTED_ABIS.firstOrNull().orEmpty(),
        locale = runCatching { Locale.getDefault().toLanguageTag() }.getOrDefault(""),
        timezone = runCatching { TimeZone.getDefault().id }.getOrDefault(""),
    )
}

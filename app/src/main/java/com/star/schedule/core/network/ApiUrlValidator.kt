package com.star.schedule.core.network

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * 校验 `API_BASE_URL` 是否可用。
 *
 * 配置错（空、少了 scheme、写成了纯域名）时应当整体停用上报，而不是让每个请求都在
 * 构造 URL 时抛异常。
 */
object ApiUrlValidator {

    fun isUsable(raw: String): Boolean {
        if (raw.isBlank()) return false
        val url = raw.toHttpUrlOrNull() ?: return false
        return url.scheme == "http" || url.scheme == "https"
    }
}

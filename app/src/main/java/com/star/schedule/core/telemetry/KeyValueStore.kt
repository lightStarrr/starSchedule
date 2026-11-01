package com.star.schedule.core.telemetry

import android.content.SharedPreferences

/**
 * 极小的键值抽象。
 *
 * 存在的唯一理由是可测性：`android.content.SharedPreferences` 在 JVM 单测里是抛异常的桩，
 * 不抽象就没法验证「解密失败要保留 install_id」「队列损坏要能容错」这类关键分支。
 */
interface KeyValueStore {
    fun getString(key: String): String?
    fun putString(key: String, value: String)
    fun remove(key: String)
}

class SharedPreferencesStore(private val prefs: SharedPreferences) : KeyValueStore {

    override fun getString(key: String): String? = prefs.getString(key, null)

    override fun putString(key: String, value: String) {
        prefs.edit().putString(key, value).apply()
    }

    override fun remove(key: String) {
        prefs.edit().remove(key).apply()
    }
}

package com.star.schedule.feature.emoji.data

import com.star.schedule.core.common.Constants
import com.star.schedule.core.database.ScheduleDao
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/**
 * 表情选择器的「最近使用」：JSON 字符串数组存偏好，最多 32 个、最新在前。
 */
class EmojiPickerRecentsRepository(
    private val dao: ScheduleDao,
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val serializer = ListSerializer(String.serializer())

    fun observe(): Flow<List<String>> =
        dao.getPreferenceFlow(Constants.PREF_EMOJI_PICKER_RECENTS).map { value ->
            value?.takeIf(String::isNotBlank)
                ?.let { runCatching { json.decodeFromString(serializer, it) }.getOrNull() }
                .orEmpty()
        }

    /** 记一次使用：提到最前、去重、截到 32。 */
    suspend fun record(emoji: String) {
        val current = dao.getPreferenceFlow(Constants.PREF_EMOJI_PICKER_RECENTS).firstOrNull()
        val list = current?.takeIf(String::isNotBlank)
            ?.let { runCatching { json.decodeFromString(serializer, it) }.getOrNull() }
            .orEmpty()
        val updated = (listOf(emoji) + list.filter { it != emoji }).take(MAX_RECENTS)
        dao.setPreference(Constants.PREF_EMOJI_PICKER_RECENTS, json.encodeToString(serializer, updated))
    }

    private companion object {
        const val MAX_RECENTS = 32
    }
}

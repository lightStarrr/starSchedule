package com.star.schedule.utils

import kotlinx.serialization.Serializable

@Serializable
data class LessonTimeTemplateExportBundle(
    val formatVersion: Int = 1,
    val templates: List<LessonTimeTemplateExport>
)

@Serializable
data class LessonTimeTemplateExport(
    val formatVersion: Int = 1,
    val templateName: String,
    val createdAt: Long,
    val updatedAt: Long,
    val lessonTimes: List<LessonTime>
) {
    @Serializable
    data class LessonTime(
        val period: Int,
        val startTime: String,
        val endTime: String
    )
}


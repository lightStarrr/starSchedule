package com.star.schedule.feature.timetable.domain

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/**
 * 模板列表里的「最后更新时间」。
 *
 * 用系统区域的短日期时间格式，不自己拼「年月日」：中文是「2026/10/30 14:05」，
 * 英文是「10/30/26, 2:05 PM」，跟着系统语言走就够了。
 * 纯函数、只依赖入参，方便单测，也不把 Locale 的选择散在 Composable 里。
 */
fun formatTemplateUpdatedAt(
    updatedAtMillis: Long,
    zone: ZoneId = ZoneId.systemDefault(),
    locale: Locale = Locale.getDefault(),
): String = DateTimeFormatter
    .ofLocalizedDateTime(FormatStyle.SHORT)
    .withLocale(locale)
    .withZone(zone)
    .format(Instant.ofEpochMilli(updatedAtMillis))

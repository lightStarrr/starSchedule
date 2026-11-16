package com.star.schedule.feature.timetable.presentation

/**
 * 预览卡缩放比顶栏折叠慢几帧，期间列表的 maxValue 还按大卡片算。
 * 若放任列表滚到这个范围里，卡片缩完后滚动值会被钳回，内容先被顶上去再掉下来。
 * 这里按「卡片缩完之后」的滚动上限，把超出的上滑量提前吃掉。
 *
 * @param availableY 顶栏消费后剩下的滚动量，上滑为负
 * @param pendingShrinkPx 卡片当前高度减去目标高度；>0 表示还要再缩
 * @return 需要消费掉的 y（<=0）
 */
internal fun previewScrollOverflow(
    availableY: Float,
    scrollValue: Int,
    maxScrollValue: Int,
    pendingShrinkPx: Float,
): Float {
    if (availableY >= 0f) return 0f
    val settledMax = (maxScrollValue - pendingShrinkPx).coerceAtLeast(0f)
    val room = (settledMax - scrollValue).coerceAtLeast(0f)
    val excess = -availableY - room
    return if (excess > 0f) -excess else 0f
}

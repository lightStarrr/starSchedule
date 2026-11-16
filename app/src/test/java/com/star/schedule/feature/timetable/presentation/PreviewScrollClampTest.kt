package com.star.schedule.feature.timetable.presentation

import org.junit.Assert.assertEquals
import org.junit.Test

/** 预览卡还在缩小时，列表多出来的上滑量要按「缩完之后」的滚动上限提前吃掉。 */
class PreviewScrollClampTest {

    @Test
    fun `downward or zero delta consumes nothing`() {
        assertEquals(0f, previewScrollOverflow(50f, 0, 500, 100f), 0f)
        assertEquals(0f, previewScrollOverflow(0f, 0, 500, 100f), 0f)
    }

    @Test
    fun `enough room lets the list scroll`() {
        assertEquals(0f, previewScrollOverflow(-100f, 0, 500, 0f), 0f)
    }

    @Test
    fun `scroll beyond the settled max is fully consumed`() {
        assertEquals(-80f, previewScrollOverflow(-80f, 100, 300, 250f), 0f)
    }

    @Test
    fun `only the excess beyond the settled room is consumed`() {
        assertEquals(-150f, previewScrollOverflow(-250f, 100, 500, 300f), 0f)
    }

    @Test
    fun `a growing card keeps the full scroll range`() {
        assertEquals(0f, previewScrollOverflow(-100f, 0, 200, -50f), 0f)
    }
}

package com.star.schedule.feature.timetable.presentation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** 节次时间的校验规则：节次时间页和模板编辑页共用，边界要一致。 */
class LessonTimeValidationTest {
    private val others = listOf(9 * 60..9 * 60 + 45)

    @Test
    fun `a well formed range passes`() {
        assertNull(validateLessonTimeRange("08:00", "08:45", others))
    }

    @Test
    fun `unparsable text is an invalid time`() {
        assertEquals(
            LessonTimeError.InvalidTime,
            validateLessonTimeRange("8点", "08:45", others),
        )
    }

    @Test
    fun `an end at or before the start is rejected`() {
        assertEquals(
            LessonTimeError.EndNotAfterStart,
            validateLessonTimeRange("08:45", "08:45", others),
        )
    }

    @Test
    fun `overlapping an existing period is rejected`() {
        assertEquals(
            LessonTimeError.Overlap,
            validateLessonTimeRange("09:30", "10:15", others),
        )
    }

    @Test
    fun `touching the previous end is allowed`() {
        assertNull(validateLessonTimeRange("09:45", "10:30", others))
    }
}

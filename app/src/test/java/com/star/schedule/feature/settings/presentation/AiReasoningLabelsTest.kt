package com.star.schedule.feature.settings.presentation

import com.star.schedule.feature.settings.domain.AiReasoningEffort
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AiReasoningLabelsTest {

    @Test
    fun `every effort level has its own label`() {
        val ids = AiReasoningEffort.entries.map { it.labelRes }

        assertTrue("档位必须有文案", ids.all { it != 0 })
        // 四档共用同一个 resource id 会让菜单出现重复项，也可能让 supporting 显示错档
        assertEquals("档位文案不能互相复用", ids.size, ids.toSet().size)
    }

    @Test
    fun `every availability state has supporting text`() {
        assertTrue(
            AiReasoningAvailability.entries.all { it.supportingRes != 0 },
        )
    }
}

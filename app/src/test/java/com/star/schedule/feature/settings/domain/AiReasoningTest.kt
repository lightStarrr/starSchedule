package com.star.schedule.feature.settings.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AiReasoningTest {

    @Test
    fun `wire modes map to enum`() {
        assertEquals(AiReasoningMode.Effort, AiReasoningMode.fromWire("effort"))
        assertEquals(AiReasoningMode.Fixed, AiReasoningMode.fromWire("fixed"))
        assertEquals(AiReasoningMode.None, AiReasoningMode.fromWire("none"))
        assertEquals(AiReasoningMode.Unknown, AiReasoningMode.fromWire("unknown"))
    }

    @Test
    fun `unrecognized mode falls back to unknown instead of effort`() {
        // 认不出就当作未知：猜成 Effort 会放开一个传了必然 400 的开关
        assertEquals(AiReasoningMode.Unknown, AiReasoningMode.fromWire(""))
        assertEquals(AiReasoningMode.Unknown, AiReasoningMode.fromWire(null))
        assertEquals(AiReasoningMode.Unknown, AiReasoningMode.fromWire("qwen-minimal"))
    }

    @Test
    fun `wire efforts map to enum and unknown values resolve to null`() {
        assertEquals(AiReasoningEffort.Minimal, AiReasoningEffort.fromWire("minimal"))
        assertEquals(AiReasoningEffort.High, AiReasoningEffort.fromWire("high"))
        assertNull(AiReasoningEffort.fromWire("ultra"))
        assertNull(AiReasoningEffort.fromWire(""))
        assertNull(AiReasoningEffort.fromWire(null))
    }

    @Test
    fun `adjustable requires effort mode with non empty levels`() {
        assertTrue(
            AiReasoning(mode = AiReasoningMode.Effort, efforts = listOf(AiReasoningEffort.Low))
                .isAdjustable,
        )
        // effort 但服务端没给档位属于异常数据，按不支持处理
        assertFalse(AiReasoning(mode = AiReasoningMode.Effort).isAdjustable)
        assertFalse(
            AiReasoning(mode = AiReasoningMode.Fixed, efforts = listOf(AiReasoningEffort.Low))
                .isAdjustable,
        )
        assertFalse(AiReasoning(mode = AiReasoningMode.None).isAdjustable)
    }
}

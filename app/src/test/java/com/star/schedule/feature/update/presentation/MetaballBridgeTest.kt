package com.star.schedule.feature.update.presentation

import kotlin.math.abs
import kotlin.math.sqrt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MetaballBridgeTest {

    private fun distance(a: BridgePoint, x: Float, y: Float) = sqrt(
        (a.x - x) * (a.x - x) + (a.y - y) * (a.y - y),
    )

    private fun neckWidth(bridge: MetaballBridge) = minOf(
        abs(bridge.start1.y - bridge.end2.y),
        abs(bridge.end1.y - bridge.start2.y),
    )

    @Test
    fun `bridge is absent beyond max distance or when one circle contains the other`() {
        assertNull(metaballBridge(0f, 0f, 28f, 100f, 0f, 28f, maxDistance = 68f))
        assertNull(metaballBridge(0f, 0f, 28f, 68f, 0f, 28f, maxDistance = 68f))
        assertNull(metaballBridge(0f, 0f, 40f, 10f, 0f, 28f, maxDistance = 68f))
        assertNull(metaballBridge(0f, 0f, 28f, 0f, 0f, 28f, maxDistance = 68f))
    }

    @Test
    fun `anchors sit on their circles and mirror across the center line`() {
        for (d in listOf(40f, 60f)) {
            val bridge = metaballBridge(0f, 0f, 28f, d, 0f, 28f, maxDistance = 68f)
            assertNotNull(bridge)
            bridge!!
            assertEquals(28f, distance(bridge.start1, 0f, 0f), 1e-3f)
            assertEquals(28f, distance(bridge.end2, 0f, 0f), 1e-3f)
            assertEquals(28f, distance(bridge.end1, d, 0f), 1e-3f)
            assertEquals(28f, distance(bridge.start2, d, 0f), 1e-3f)
            assertEquals(bridge.start1.x, bridge.end2.x, 1e-3f)
            assertEquals(bridge.start1.y, -bridge.end2.y, 1e-3f)
            assertEquals(bridge.end1.x, bridge.start2.x, 1e-3f)
            assertEquals(bridge.end1.y, -bridge.start2.y, 1e-3f)
            assertTrue(bridge.start1.y > bridge.end2.y)
        }
    }

    @Test
    fun `neck narrows monotonically and vanishes at max distance`() {
        val distances = listOf(57f, 60f, 63f, 66f)
        val widths = distances.map { d ->
            val bridge = metaballBridge(0f, 0f, 28f, d, 0f, 28f, maxDistance = 68f)
            assertNotNull(bridge)
            neckWidth(bridge!!)
        }
        widths.zipWithNext().forEach { (wider, narrower) ->
            assertTrue(narrower < wider)
        }
        val tip = metaballBridge(0f, 0f, 28f, 67.99f, 0f, 28f, maxDistance = 68f)
        assertNotNull(tip)
        assertTrue(neckWidth(tip!!) < 28f * 0.01f)
    }
}

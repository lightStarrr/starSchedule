package com.star.schedule.feature.importing.wakeup.presentation

import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialShapes
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
class WakeUpIndicatorSequenceTest {

    /** 用真实的候选池：M3 的默认序列里带着一个饼干，必须被排掉，剩下的还要够挑。 */
    @Test
    fun poolDropsTheCookieAndLeavesEnoughShapes() {
        assertTrue(WakeUpCycleShapes.none { it === MaterialShapes.Cookie9Sided })
        assertTrue("候选形状只剩 ${WakeUpCycleShapes.size} 个", WakeUpCycleShapes.size >= 2)
    }

    @Test
    fun neverRepeatsThePreviousShape() {
        val random = Random(20261022)
        var current = 3
        repeat(1_000) {
            val next = pickDifferentShapeIndex(current, WakeUpCycleShapes.size, random)
            assertNotEquals(current, next)
            assertTrue(next in WakeUpCycleShapes.indices)
            current = next
        }
    }

    /** 序列第一步是从饼干出发的，饼干不在池子里，这一步不该排除任何候选。 */
    @Test
    fun firstStepAfterCookieCanBeAnyShape() {
        val random = Random(7)
        val picked = buildSet {
            repeat(500) { add(pickDifferentShapeIndex(-1, WakeUpCycleShapes.size, random)) }
        }
        assertEquals(WakeUpCycleShapes.indices.toSet(), picked)
    }

    /** 排除当前形状之后，其余每一个都还挑得到——不能只在前一个附近打转。 */
    @Test
    fun everyOtherCandidateStaysReachable() {
        val random = Random(11)
        for (current in WakeUpCycleShapes.indices) {
            val picked = buildSet {
                repeat(1_000) { add(pickDifferentShapeIndex(current, WakeUpCycleShapes.size, random)) }
            }
            assertEquals(WakeUpCycleShapes.indices.toSet() - current, picked)
        }
    }

    @Test
    fun refusesAPoolTooSmallToDiffer() {
        assertThrows(IllegalArgumentException::class.java) {
            pickDifferentShapeIndex(0, 1, Random(1))
        }
    }
}

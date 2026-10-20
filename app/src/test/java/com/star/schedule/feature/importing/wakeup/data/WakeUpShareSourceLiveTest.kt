package com.star.schedule.feature.importing.wakeup.data

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * 真机链路冒烟：只有显式传入一个未过期的分享口令时才会跑。
 *
 * 口令 30 分钟就失效，不能进 CI；改协议常量后手动跑一次，比在手机上盲点快得多。
 */
class WakeUpShareSourceLiveTest {
    @Test
    fun fetchesAndParsesARealShareCode() = runBlocking {
        val code = System.getenv("WAKEUP_TEST_CODE")?.trim().orEmpty()
        assumeTrue("WAKEUP_TEST_CODE not set", code.isNotEmpty())

        val response = OkHttpWakeUpShareSource().fetch(code)
        val plan = WakeUpShareParser().parse(response)

        println("live import: name=${plan.timetableName} start=${plan.startDate} slots=${plan.lessonTimes.size} courses=${plan.courses.size}")
        plan.lessonTimes.forEach { println("  slot ${it.period} ${it.startTime}-${it.endTime}") }
        plan.courses.forEach {
            println("  course=${it.name} day=${it.dayOfWeek} periods=${it.periods} weeks=${it.weeks} teacher=${it.teacher} room=${it.location}")
        }
        assertTrue(plan.lessonTimes.isNotEmpty())
        assertTrue(plan.courses.isNotEmpty())
    }
}

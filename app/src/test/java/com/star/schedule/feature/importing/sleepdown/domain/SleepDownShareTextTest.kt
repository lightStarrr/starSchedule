package com.star.schedule.feature.importing.sleepdown.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class SleepDownShareTextTest {

    @Test
    fun `formatted SD code extracts`() {
        assertEquals("7K3QX9MZ2A", extractSleepDownCode("SD-7K3QX-9MZ2A"))
    }

    @Test
    fun `lowercase code with spaces extracts`() {
        assertEquals("7K3QX9MZ2A", extractSleepDownCode("sd 7k3qx 9mz2a"))
    }

    @Test
    fun `bare code extracts`() {
        assertEquals("7K3QX9MZ2A", extractSleepDownCode("7K3QX9MZ2A"))
    }

    @Test
    fun `bare code with separator extracts`() {
        assertEquals("7K3QX9MZ2A", extractSleepDownCode("7K3QX-9MZ2A"))
    }

    @Test
    fun `labeled code extracts`() {
        assertEquals("7K3QX9MZ2A", extractSleepDownCode("短口令：7K3QX-9MZ2A"))
    }

    @Test
    fun `code embedded in full share text extracts`() {
        val text = "SleepDown 课表短口令（15 分钟内有效）\nSD-7K3QX-9MZ2A\n打开 App 粘贴即可导入"
        assertEquals("7K3QX9MZ2A", extractSleepDownCode(text))
    }

    @Test
    fun `confusable letters normalize to digits`() {
        assertEquals("7K3Q09MZ21", extractSleepDownCode("7K3QO9MZ2L"))
        assertEquals("7K3Q19MZ21", extractSleepDownCode("SD-7K3QI-9MZ2l"))
    }

    @Test
    fun `code with letter outside alphabet is rejected`() {
        assertEquals("", extractSleepDownCode("SD-7K3QU-9MZ2A"))
        assertEquals("", extractSleepDownCode("7K3QU9MZ2A"))
    }

    @Test
    fun `wrong length is rejected`() {
        assertEquals("", extractSleepDownCode("SD-7K3QX-9MZ2"))
        assertEquals("", extractSleepDownCode("7K3QX9MZ2AB"))
        assertEquals("", extractSleepDownCode(""))
    }

    @Test
    fun `unrelated text is rejected`() {
        assertEquals("", extractSleepDownCode("分享自 SleepDown，快去导入吧"))
    }
}

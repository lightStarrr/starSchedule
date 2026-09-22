package com.star.schedule.feature.update.data

import org.junit.Assert.assertEquals
import org.junit.Test

class GitHubLatestReleaseSourceTest {
    @Test
    fun parsesReleaseTag() {
        assertEquals(
            "v2.3.4",
            parseLatestReleaseTag("""{"tag_name":"v2.3.4"}"""),
        )
    }

    @Test
    fun preservesDefaultTagWhenFieldIsMissing() {
        assertEquals("v1.0.0", parseLatestReleaseTag("{}"))
    }
}

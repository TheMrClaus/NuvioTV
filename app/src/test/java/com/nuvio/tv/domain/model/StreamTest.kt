package com.nuvio.tv.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StreamTest {

    @Test
    fun `getStreamUrl returns primary url even when sources are present`() {
        val stream = Stream(
            name = null,
            title = null,
            description = null,
            url = "https://example.com/primary.m3u8",
            sources = listOf(
                "https://example.com/primary.m3u8",
                " https://example.com/fallback1.m3u8 ",
                "",
                "https://example.com/fallback2.m3u8"
            ),
            ytId = null,
            infoHash = null,
            fileIdx = null,
            externalUrl = null,
            behaviorHints = null,
            addonName = "Addon",
            addonLogo = null
        )

        assertEquals("https://example.com/primary.m3u8", stream.getStreamUrl())
    }

    @Test
    fun `external url is used when direct url is missing`() {
        val stream = Stream(
            name = null,
            title = null,
            description = null,
            url = null,
            sources = listOf("https://example.com/fallback1.m3u8"),
            ytId = null,
            infoHash = null,
            fileIdx = null,
            externalUrl = "https://example.com/browser",
            behaviorHints = null,
            addonName = "Addon",
            addonLogo = null
        )

        assertEquals("https://example.com/browser", stream.getStreamUrl())
        assertTrue(stream.isExternal())
    }

    @Test
    fun `external stream remains external when no playable urls exist`() {
        val stream = Stream(
            name = null,
            title = null,
            description = null,
            url = null,
            sources = emptyList(),
            ytId = null,
            infoHash = null,
            fileIdx = null,
            externalUrl = "https://example.com/browser",
            behaviorHints = null,
            addonName = "Addon",
            addonLogo = null
        )

        assertTrue(stream.isExternal())
    }
}

package com.omnio.tv.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class SourceCloudContractTest {
    @Test
    fun `source cloud services are resolved from persisted keys`() {
        assertSame(SourceCloudService.REAL_DEBRID, SourceCloudService.fromKey("real_debrid"))
        assertSame(SourceCloudService.TORBOX, SourceCloudService.fromKey("torbox"))
        assertNull(SourceCloudService.fromKey("unknown"))
    }

    @Test
    fun `settings and status expose connected service state`() {
        assertFalse(SourceCloudSettings().hasConnectedService)
        assertTrue(SourceCloudSettings(connectedServices = setOf(SourceCloudService.TORBOX)).hasConnectedService)

        val status = SourceCloudStatus(
            enabled = true,
            baseUrlConfigured = true,
            config = SourceCloudConfigState(
                status = SourceCloudConfigStatus.READY,
                label = "Ready",
                advancedConfigAvailable = true
            ),
            services = listOf(SourceCloudServiceStatus(SourceCloudService.REAL_DEBRID, connected = true))
        )

        assertTrue(status.hasConnectedService)
        assertEquals(SourceCloudConfigStatus.READY, status.config.status)
        assertTrue(status.config.advancedConfigAvailable)
    }

    @Test
    fun `config status is resolved from backend keys`() {
        assertSame(SourceCloudConfigStatus.NOT_PROVISIONED, SourceCloudConfigStatus.fromKey("not_provisioned"))
        assertSame(SourceCloudConfigStatus.READY, SourceCloudConfigStatus.fromKey("ready"))
        assertSame(SourceCloudConfigStatus.PROVISIONING_FAILED, SourceCloudConfigStatus.fromKey("provisioning_failed"))
        assertSame(SourceCloudConfigStatus.UNAVAILABLE, SourceCloudConfigStatus.fromKey("unavailable"))
        assertSame(SourceCloudConfigStatus.INVALID, SourceCloudConfigStatus.fromKey("invalid"))
        assertSame(SourceCloudConfigStatus.UNKNOWN, SourceCloudConfigStatus.fromKey("missing"))
    }

    @Test
    fun `stream can carry optional source cloud metadata without changing existing construction`() {
        val stream = Stream(
            name = null,
            title = "Example",
            description = null,
            url = "https://example.com/video.mp4",
            ytId = null,
            infoHash = null,
            fileIdx = null,
            externalUrl = null,
            behaviorHints = null,
            addonName = "Addon",
            addonLogo = null,
            sourceCloudMetadata = SourceCloudStreamMetadata(
                quality = "1080p",
                sourceService = SourceCloudService.REAL_DEBRID
            )
        )

        assertEquals("1080p", stream.sourceCloudMetadata?.quality)
        assertSame(SourceCloudService.REAL_DEBRID, stream.sourceCloudMetadata?.sourceService)
    }
}

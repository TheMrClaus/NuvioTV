package com.omnio.tv.data.remote.dto.sourcecloud

import com.omnio.tv.domain.model.SourceCloudService
import com.omnio.tv.domain.model.SourceCloudConfigStatus
import com.squareup.moshi.Moshi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SourceCloudDtoMappingTest {
    private val moshi = Moshi.Builder().build()

    @Test
    fun `maps cloud stream dto to domain stream`() {
        val dto = SourceCloudStreamDto(
            name = "Best 4K",
            title = "Movie 4K DV",
            description = "4K • 20 GB • cached",
            url = "https://stream.example/movie.mkv",
            behaviorHints = SourceCloudBehaviorHintsDto(filename = "movie.mkv", videoSize = 20_000L),
            metadata = SourceCloudStreamMetadataDto(
                quality = "4K",
                sizeBytes = 20_000L,
                codec = "HEVC",
                hdr = "DV",
                cached = true,
                sourceConfidence = 0.97,
                sourceService = "real_debrid"
            )
        )

        val stream = dto.toDomain()

        assertEquals("Omnio Source Cloud", stream.addonName)
        assertEquals("https://stream.example/movie.mkv", stream.url)
        assertEquals("movie.mkv", stream.behaviorHints?.filename)
        assertEquals(SourceCloudService.REAL_DEBRID, stream.sourceCloudMetadata?.sourceService)
        assertTrue(stream.sourceCloudMetadata?.cached == true)
    }

    @Test
    fun `drops unknown null and missing service statuses decoded from json`() {
        val json = """
            {
              "config": {
                "status": "ready",
                "label": "Private source config ready",
                "message": "Configured per profile",
                "advancedConfigAvailable": true,
                "canReset": true
              },
              "services": [
                { "service": "real_debrid", "connected": true },
                { "service": "unknown", "connected": true },
                { "service": null, "connected": true },
                { "connected": true }
              ]
            }
        """.trimIndent()

        val dto = moshi.adapter(SourceCloudStatusResponseDto::class.java).fromJson(json)!!
        val status = dto.toDomain(enabled = true, baseUrlConfigured = true)

        assertEquals(1, status.services.size)
        assertEquals(SourceCloudService.REAL_DEBRID, status.services.single().service)
        assertEquals(SourceCloudConfigStatus.READY, status.config.status)
        assertEquals("Private source config ready", status.config.label)
        assertTrue(status.config.advancedConfigAvailable)
        assertTrue(status.config.canReset)
    }

    @Test
    fun `maps advanced config session without exposing secrets beyond short lived url`() {
        val dto = SourceCloudAdvancedConfigSessionResponseDto(
            url = "https://source.omnio.tv/advanced/session/abc",
            expiresAtEpochMillis = 1_770_000_000_000L,
            message = "Scan to open advanced source config"
        )

        val session = dto.toDomain()

        assertEquals("https://source.omnio.tv/advanced/session/abc", session.url)
        assertEquals(1_770_000_000_000L, session.expiresAtEpochMillis)
        assertEquals("Scan to open advanced source config", session.message)
    }

    @Test
    fun `sanitizes proxy headers decoded from json`() {
        val json = """
            {
              "streams": [
                {
                  "url": "https://stream.example/movie.mkv",
                  "behaviorHints": {
                    "requestHeaders": {
                      "Authorization": "Bearer token",
                      "Range": "bytes=0-",
                      "X-Blank": " ",
                      " ": "missing"
                    },
                    "responseHeaders": {
                      "Content-Type": "video/mp4",
                      "range": "bytes=0-1/2",
                      "Empty": ""
                    }
                  }
                }
              ]
            }
        """.trimIndent()

        val dto = moshi.adapter(SourceCloudSearchResponseDto::class.java).fromJson(json)!!
        val stream = dto.toDomain()!!.streams.single()

        assertEquals(mapOf("Authorization" to "Bearer token"), stream.behaviorHints?.proxyHeaders?.request)
        assertEquals(mapOf("Content-Type" to "video/mp4"), stream.behaviorHints?.proxyHeaders?.response)
    }
}

package com.omnio.tv.data.stream

import com.omnio.tv.domain.model.SourceCloudStreamMetadata
import com.omnio.tv.domain.model.Stream
import com.omnio.tv.domain.model.StreamPrefAudioTag
import com.omnio.tv.domain.model.StreamPrefCodec
import com.omnio.tv.domain.model.StreamPrefEncode
import com.omnio.tv.domain.model.StreamPrefMinQuality
import com.omnio.tv.domain.model.StreamPrefSortCriterion
import com.omnio.tv.domain.model.StreamPrefSortDirection
import com.omnio.tv.domain.model.StreamPrefSortKey
import com.omnio.tv.domain.model.StreamPrefVisualTag
import com.omnio.tv.domain.model.StreamPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StreamPrefFilterTest {

    private fun stream(
        name: String = "Test Stream",
        quality: String? = null,
        sizeBytes: Long? = null,
        codec: String? = null,
        audio: String? = null,
        hdr: String? = null,
        language: String? = null,
        cached: Boolean? = null,
        sourceConfidence: Double? = null,
        sourceService: com.omnio.tv.domain.model.SourceCloudService? = null
    ): Stream = Stream(
        name = name,
        title = null,
        description = null,
        url = "https://example.com/stream",
        ytId = null,
        infoHash = null,
        fileIdx = null,
        externalUrl = null,
        behaviorHints = null,
        addonName = "Source Cloud",
        addonLogo = null,
        sourceProvider = "source_cloud",
        sourceCloudMetadata = SourceCloudStreamMetadata(
            quality = quality,
            sizeBytes = sizeBytes,
            codec = codec,
            audio = audio,
            hdr = hdr,
            language = language,
            cached = cached,
            sourceConfidence = sourceConfidence,
            sourceService = sourceService
        )
    )

    // ---- 1. Empty / disabled prefs return input unchanged ----

    @Test
    fun emptyPrefsReturnsInputUnchanged() {
        val streams = listOf(stream("S1"), stream("S2"))
        val result = StreamPrefFilter.apply(streams, StreamPreferences.DEFAULT)
        assertEquals(streams, result)
    }

    @Test
    fun disabledPrefsReturnsInputUnchanged() {
        val streams = listOf(stream("S1"))
        val prefs = StreamPreferences.DEFAULT.copy(enabled = false)
        val result = StreamPrefFilter.apply(streams, prefs)
        assertEquals(streams, result)
    }

    @Test
    fun enabledWithNoFiltersReturnsInput() {
        val streams = listOf(stream("S1"), stream("S2"))
        val prefs = StreamPreferences(enabled = true)
        val result = StreamPrefFilter.apply(streams, prefs)
        assertEquals(streams, result)
    }

    // ---- 2. Min-resolution filter ----

    @Test
    fun minResolutionFiltersLowRes() {
        val hd = stream("HD", quality = "720p")
        val fhd = stream("FHD", quality = "1080p")
        val uhd = stream("UHD", quality = "2160p")
        val streams = listOf(hd, fhd, uhd)
        val prefs = StreamPreferences(
            enabled = true,
            minResolution = StreamPrefMinQuality.P1080,
            sortCriteria = emptyList()
        )
        val result = StreamPrefFilter.apply(streams, prefs)
        assertEquals(listOf(fhd, uhd), result)
    }

    @Test
    fun minResolutionNoneKeepsAll() {
        val hd = stream("HD", quality = "720p")
        val uhd = stream("UHD", quality = "2160p")
        val result = StreamPrefFilter.apply(
            listOf(hd, uhd),
            StreamPreferences(enabled = true, minResolution = StreamPrefMinQuality.NONE, sortCriteria = emptyList())
        )
        assertEquals(2, result.size)
    }

    // ---- 3. Required tags include ----

    @Test
    fun requiredVisualTagKeepsOnlyMatch() {
        val hdr10 = stream("HDR10", hdr = "HDR10")
        val dv = stream("DV", hdr = "Dolby Vision")
        val sdr = stream("SDR", hdr = "SDR")
        val streams = listOf(hdr10, dv, sdr)
        val prefs = StreamPreferences(
            enabled = true,
            requiredVisualTags = setOf(StreamPrefVisualTag.DOLBY_VISION)
        )
        val result = StreamPrefFilter.apply(streams, prefs)
        assertEquals(listOf(dv), result)
    }

    @Test
    fun requiredAudioTagKeepsOnlyMatch() {
        val atmos = stream("Atmos", audio = "Atmos")
        val aac = stream("AAC", audio = "AAC")
        val streams = listOf(atmos, aac)
        val prefs = StreamPreferences(
            enabled = true,
            requiredAudioTags = setOf(StreamPrefAudioTag.ATMOS)
        )
        val result = StreamPrefFilter.apply(streams, prefs)
        assertEquals(listOf(atmos), result)
    }

    // ---- 4. Excluded tags exclude ----

    @Test
    fun excludedVisualTagRemovesMatch() {
        val hdr10 = stream("HDR10", hdr = "HDR10")
        val sdr = stream("SDR", hdr = "SDR")
        val streams = listOf(hdr10, sdr)
        val prefs = StreamPreferences(
            enabled = true,
            excludedVisualTags = setOf(StreamPrefVisualTag.HDR10)
        )
        val result = StreamPrefFilter.apply(streams, prefs)
        assertEquals(listOf(sdr), result)
    }

    @Test
    fun excludedCodecRemovesMatch() {
        val hevc = stream("HEVC", codec = "HEVC")
        val av1 = stream("AV1", codec = "AV1")
        val streams = listOf(hevc, av1)
        val prefs = StreamPreferences(
            enabled = true,
            excludedCodecs = setOf(StreamPrefCodec.HEVC)
        )
        val result = StreamPrefFilter.apply(streams, prefs)
        assertEquals(listOf(av1), result)
    }

    // ---- 5. Required + excluded combined ----

    @Test
    fun requiredAndExcludedCombined() {
        val s1 = stream("HEVC+DV", codec = "HEVC", hdr = "Dolby Vision")
        val s2 = stream("HEVC+SDR", codec = "HEVC", hdr = "SDR")
        val s3 = stream("AV1+DV", codec = "AV1", hdr = "Dolby Vision")
        val streams = listOf(s1, s2, s3)
        val prefs = StreamPreferences(
            enabled = true,
            requiredCodecs = setOf(StreamPrefCodec.HEVC),
            excludedVisualTags = setOf(StreamPrefVisualTag.SDR)
        )
        // s1: HEVC+DV -> match (HEVC req, DV not excluded)
        // s2: HEVC+SDR -> no (SDR excluded)
        // s3: AV1+DV -> no (HEVC required but has AV1)
        val result = StreamPrefFilter.apply(streams, prefs)
        assertEquals(listOf(s1), result)
    }

    // ---- 6. Sort by resolution DESC ----

    @Test
    fun sortByResolutionDesc() {
        val uhd = stream("4K", quality = "2160p")
        val hd = stream("HD", quality = "720p")
        val fhd = stream("FHD", quality = "1080p")
        val streams = listOf(hd, uhd, fhd)
        val prefs = StreamPreferences(
            enabled = true,
            sortCriteria = listOf(
                StreamPrefSortCriterion(StreamPrefSortKey.RESOLUTION, StreamPrefSortDirection.DESC)
            )
        )
        val result = StreamPrefFilter.apply(streams, prefs)
        assertEquals(listOf(uhd, fhd, hd), result)
    }

    // ---- 7. Sort by size DESC ----

    @Test
    fun sortBySizeDesc() {
        val small = stream("Small", sizeBytes = 100_000_000)
        val large = stream("Large", sizeBytes = 10_000_000_000)
        val medium = stream("Medium", sizeBytes = 1_000_000_000)
        val streams = listOf(small, large, medium)
        val prefs = StreamPreferences(
            enabled = true,
            sortCriteria = listOf(
                StreamPrefSortCriterion(StreamPrefSortKey.SIZE, StreamPrefSortDirection.DESC)
            )
        )
        val result = StreamPrefFilter.apply(streams, prefs)
        assertEquals(listOf(large, medium, small), result)
    }

    // ---- 8. Sort by size ASC ----

    @Test
    fun sortBySizeAsc() {
        val small = stream("Small", sizeBytes = 100_000_000)
        val large = stream("Large", sizeBytes = 10_000_000_000)
        val streams = listOf(large, small)
        val prefs = StreamPreferences(
            enabled = true,
            sortCriteria = listOf(
                StreamPrefSortCriterion(StreamPrefSortKey.SIZE, StreamPrefSortDirection.ASC)
            )
        )
        val result = StreamPrefFilter.apply(streams, prefs)
        assertEquals(listOf(small, large), result)
    }

    // ---- 9. Mixed criterion ordering ----

    @Test
    fun mixedCriteriaResolutionThenSize() {
        val fhdLarge = stream("FHD Large", quality = "1080p", sizeBytes = 5_000_000_000)
        val fhdSmall = stream("FHD Small", quality = "1080p", sizeBytes = 1_000_000_000)
        val uhd = stream("4K", quality = "2160p", sizeBytes = 500_000_000)
        val streams = listOf(fhdSmall, uhd, fhdLarge)
        val prefs = StreamPreferences(
            enabled = true,
            sortCriteria = listOf(
                StreamPrefSortCriterion(StreamPrefSortKey.RESOLUTION, StreamPrefSortDirection.DESC),
                StreamPrefSortCriterion(StreamPrefSortKey.SIZE, StreamPrefSortDirection.DESC)
            )
        )
        val result = StreamPrefFilter.apply(streams, prefs)
        assertEquals(listOf(uhd, fhdLarge, fhdSmall), result)
    }

    // ---- 10. Streams with null metadata ----

    @Test
    fun nullMetadataDoesNotCrash() {
        val noMeta = Stream(
            name = "No Meta",
            title = null,
            description = null,
            url = "https://example.com",
            ytId = null,
            infoHash = null,
            fileIdx = null,
            externalUrl = null,
            behaviorHints = null,
            addonName = "Test",
            addonLogo = null,
            sourceProvider = null,
            sourceCloudMetadata = null
        )
        val withMeta = stream("With Meta", quality = "1080p")
        val prefs = StreamPreferences(
            enabled = true,
            minResolution = StreamPrefMinQuality.P720
        )
        val result = StreamPrefFilter.apply(listOf(noMeta, withMeta), prefs)
        // noMeta has null resolution, so it gets filtered out by minResolution
        assertEquals(listOf(withMeta), result)
    }

    @Test
    fun nullMetadataKeptWhenNoFiltersActive() {
        val noMeta = Stream(
            name = "No Meta",
            title = null,
            description = null,
            url = "https://example.com",
            ytId = null,
            infoHash = null,
            fileIdx = null,
            externalUrl = null,
            behaviorHints = null,
            addonName = "Test",
            addonLogo = null,
            sourceProvider = null,
            sourceCloudMetadata = null
        )
        val prefs = StreamPreferences(enabled = true)
        val result = StreamPrefFilter.apply(listOf(noMeta), prefs)
        assertEquals(1, result.size)
        assertEquals(noMeta, result[0])
    }

    // ---- 11. Require cached ----

    @Test
    fun requireCachedFilters() {
        val cached = stream("Cached", cached = true)
        val notCached = stream("Not Cached", cached = false)
        val unspecified = stream("Unspecified", cached = null)
        val streams = listOf(cached, notCached, unspecified)
        val prefs = StreamPreferences(enabled = true, requireCached = true)
        val result = StreamPrefFilter.apply(streams, prefs)
        assertEquals(listOf(cached), result)
    }

    // ---- 12. Size range ----

    @Test
    fun sizeRangeFilter() {
        val tiny = stream("Tiny", sizeBytes = 10_000_000)
        val ok = stream("OK", sizeBytes = 500_000_000)
        val huge = stream("Huge", sizeBytes = 100_000_000_000)
        val streams = listOf(tiny, ok, huge)
        val prefs = StreamPreferences(
            enabled = true,
            minSizeBytes = 100_000_000,
            maxSizeBytes = 1_000_000_000
        )
        val result = StreamPrefFilter.apply(streams, prefs)
        assertEquals(listOf(ok), result)
    }

    // ---- 13. Language filter ----

    @Test
    fun languageFilter() {
        val en = stream("EN", language = "en")
        val fr = stream("FR", language = "fr")
        val streams = listOf(en, fr)
        val prefs = StreamPreferences(
            enabled = true,
            requiredLanguages = setOf("en")
        )
        val result = StreamPrefFilter.apply(streams, prefs)
        assertEquals(listOf(en), result)
    }

    @Test
    fun excludedLanguage() {
        val en = stream("EN", language = "en")
        val fr = stream("FR", language = "fr")
        val streams = listOf(en, fr)
        val prefs = StreamPreferences(
            enabled = true,
            excludedLanguages = setOf("en")
        )
        val result = StreamPrefFilter.apply(streams, prefs)
        assertEquals(listOf(fr), result)
    }

    // ---- 14. Multiple audio tags recognized ----

    @Test
    fun multiTagAudioParsing() {
        val multi = stream("Multi Audio", audio = "Atmos, TrueHD")
        val streams = listOf(multi)
        val prefs = StreamPreferences(
            enabled = true,
            requiredAudioTags = setOf(StreamPrefAudioTag.TRUEHD)
        )
        val result = StreamPrefFilter.apply(streams, prefs)
        assertEquals(1, result.size)
    }

    // ---- 15. Encode filtering ----

    @Test
    fun encodeFilter() {
        val remux = stream("Remux", quality = "REMUX")
        val webdl = stream("WebDL", quality = "WEB-DL")
        val streams = listOf(remux, webdl)
        val prefs = StreamPreferences(
            enabled = true,
            requiredEncodes = setOf(StreamPrefEncode.REMUX)
        )
        val result = StreamPrefFilter.apply(streams, prefs)
        assertEquals(listOf(remux), result)
    }
}

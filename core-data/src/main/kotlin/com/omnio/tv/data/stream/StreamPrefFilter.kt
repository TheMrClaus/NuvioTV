package com.omnio.tv.data.stream

import com.omnio.tv.domain.model.Stream
import com.omnio.tv.domain.model.StreamPrefAudioChannel
import com.omnio.tv.domain.model.StreamPrefAudioTag
import com.omnio.tv.domain.model.StreamPrefCodec
import com.omnio.tv.domain.model.StreamPrefEncode
import com.omnio.tv.domain.model.StreamPrefMinQuality
import com.omnio.tv.domain.model.StreamPrefSortCriterion
import com.omnio.tv.domain.model.StreamPrefSortDirection
import com.omnio.tv.domain.model.StreamPrefSortKey
import com.omnio.tv.domain.model.StreamPrefVisualTag
import com.omnio.tv.domain.model.StreamPreferences

object StreamPrefFilter {

    fun apply(streams: List<Stream>, prefs: StreamPreferences): List<Stream> {
        if (!prefs.enabled) return streams
        if (streams.isEmpty()) return streams

        val parsed = streams.mapIndexed { idx, stream ->
            stream to extractFacts(stream, idx)
        }

        val filtered = parsed.filter { (_, facts) ->
            matchesFilters(facts, prefs)
        }

        if (hasSortCriteria(prefs)) {
            return filtered.sortedWith { (_, left), (_, right) ->
                compareFacts(left, right, prefs.sortCriteria)
            }.map { it.first }
        }

        return filtered.map { it.first }
    }

    private fun hasSortCriteria(prefs: StreamPreferences): Boolean {
        return prefs.sortCriteria.isNotEmpty()
    }

    // ---- Fact extraction ----

    private data class StreamFacts(
        val originalIndex: Int,
        val resolutionHeight: Int?,
        val visualTags: Set<StreamPrefVisualTag>,
        val audioTags: Set<StreamPrefAudioTag>,
        val audioChannels: Set<StreamPrefAudioChannel>,
        val codecs: Set<StreamPrefCodec>,
        val encodes: Set<StreamPrefEncode>,
        val languageCode: String?,
        val sizeBytes: Long?,
        val isCached: Boolean,
        val sourceConfidence: Double?
    )

    private fun extractFacts(stream: Stream, index: Int): StreamFacts {
        val meta = stream.sourceCloudMetadata
        if (meta == null) {
            return StreamFacts(
                originalIndex = index,
                resolutionHeight = null,
                visualTags = emptySet(),
                audioTags = emptySet(),
                audioChannels = emptySet(),
                codecs = emptySet(),
                encodes = emptySet(),
                languageCode = null,
                sizeBytes = null,
                isCached = false,
                sourceConfidence = null
            )
        }

        val qualityText = meta.quality.orEmpty()
        val codecText = meta.codec.orEmpty()
        val audioText = meta.audio.orEmpty()
        val hdrText = meta.hdr.orEmpty()
        val langText = meta.language.orEmpty()

        val combinedText = buildString {
            append(stream.name.orEmpty()).append(' ')
            append(stream.title.orEmpty()).append(' ')
            append(stream.description.orEmpty()).append(' ')
            append(qualityText).append(' ')
            append(codecText).append(' ')
            append(audioText).append(' ')
            append(hdrText).append(' ')
        }

        return StreamFacts(
            originalIndex = index,
            resolutionHeight = extractResolution(qualityText, combinedText),
            visualTags = extractVisualTags(hdrText, combinedText),
            audioTags = extractAudioTags(audioText, combinedText),
            audioChannels = extractAudioChannels(audioText, combinedText),
            codecs = extractCodecs(codecText, combinedText),
            encodes = extractEncodes(qualityText, combinedText),
            languageCode = extractLanguage(langText),
            sizeBytes = meta.sizeBytes,
            isCached = meta.cached == true,
            sourceConfidence = meta.sourceConfidence
        )
    }

    // ---- Filtering ----

    private fun matchesFilters(facts: StreamFacts, prefs: StreamPreferences): Boolean {
        if (prefs.minResolution != StreamPrefMinQuality.NONE) {
            val minH = prefs.minResolution.minResolution
            if (minH > 0 && (facts.resolutionHeight == null || facts.resolutionHeight < minH)) {
                return false
            }
        }

        if (prefs.requiredVisualTags.isNotEmpty() &&
            facts.visualTags.none { it in prefs.requiredVisualTags }
        ) return false
        if (facts.visualTags.any { it in prefs.excludedVisualTags }) return false

        if (prefs.requiredAudioTags.isNotEmpty() &&
            facts.audioTags.none { it in prefs.requiredAudioTags }
        ) return false
        if (facts.audioTags.any { it in prefs.excludedAudioTags }) return false

        if (prefs.requiredCodecs.isNotEmpty() &&
            facts.codecs.none { it in prefs.requiredCodecs }
        ) return false
        if (facts.codecs.any { it in prefs.excludedCodecs }) return false

        if (prefs.requiredEncodes.isNotEmpty() &&
            facts.encodes.none { it in prefs.requiredEncodes }
        ) return false
        if (facts.encodes.any { it in prefs.excludedEncodes }) return false

        if (prefs.requiredLanguages.isNotEmpty()) {
            val lang = facts.languageCode ?: return false
            if (lang !in prefs.requiredLanguages) return false
        }
        if (prefs.excludedLanguages.isNotEmpty()) {
            val lang = facts.languageCode
            if (lang != null && lang in prefs.excludedLanguages) return false
        }

        if (prefs.requireCached && !facts.isCached) return false

        prefs.minSizeBytes?.let { min ->
            if (facts.sizeBytes != null && facts.sizeBytes < min) return false
        }
        prefs.maxSizeBytes?.let { max ->
            if (facts.sizeBytes != null && facts.sizeBytes > max) return false
        }

        return true
    }

    // ---- Sorting ----

    private fun compareFacts(
        left: StreamFacts,
        right: StreamFacts,
        criteria: List<StreamPrefSortCriterion>
    ): Int {
        for (criterion in criteria) {
            val comparison = compareKey(left, right, criterion)
            if (comparison != 0) return comparison
        }
        return left.originalIndex.compareTo(right.originalIndex)
    }

    private fun compareKey(
        left: StreamFacts,
        right: StreamFacts,
        criterion: StreamPrefSortCriterion
    ): Int {
        val multiplier = if (criterion.direction == StreamPrefSortDirection.ASC) 1 else -1
        return when (criterion.key) {
            StreamPrefSortKey.RESOLUTION -> {
                val l = left.resolutionHeight ?: 0
                val r = right.resolutionHeight ?: 0
                l.compareTo(r) * multiplier
            }
            StreamPrefSortKey.QUALITY -> {
                val l = rankQuality(left.encodes.firstOrNull())
                val r = rankQuality(right.encodes.firstOrNull())
                l.compareTo(r) * multiplier
            }
            StreamPrefSortKey.SIZE -> {
                val l = left.sizeBytes ?: 0L
                val r = right.sizeBytes ?: 0L
                l.compareTo(r) * multiplier
            }
            StreamPrefSortKey.CACHED -> {
                val l = if (left.isCached) 1 else 0
                val r = if (right.isCached) 1 else 0
                l.compareTo(r) * multiplier
            }
            StreamPrefSortKey.SOURCE_CONFIDENCE -> {
                val l = left.sourceConfidence ?: 0.0
                val r = right.sourceConfidence ?: 0.0
                l.compareTo(r) * multiplier
            }
            StreamPrefSortKey.AUDIO -> {
                val l = rankAudioTag(left.audioTags.firstOrNull())
                val r = rankAudioTag(right.audioTags.firstOrNull())
                l.compareTo(r) * multiplier
            }
            StreamPrefSortKey.VISUAL_TAG -> {
                val l = rankVisualTag(left.visualTags.firstOrNull())
                val r = rankVisualTag(right.visualTags.firstOrNull())
                l.compareTo(r) * multiplier
            }
            StreamPrefSortKey.ENCODE -> {
                val l = rankCodec(left.codecs.firstOrNull())
                val r = rankCodec(right.codecs.firstOrNull())
                l.compareTo(r) * multiplier
            }
        }
    }

    private fun rankCodec(codec: StreamPrefCodec?): Int = when (codec) {
        StreamPrefCodec.AV1 -> 0
        StreamPrefCodec.HEVC -> 1
        StreamPrefCodec.VP9 -> 2
        StreamPrefCodec.H264 -> 3
        null -> Int.MAX_VALUE
    }

    private fun rankQuality(encode: StreamPrefEncode?): Int = when (encode) {
        StreamPrefEncode.REMUX -> 0
        StreamPrefEncode.BLURAY -> 1
        StreamPrefEncode.WEB_DL -> 2
        StreamPrefEncode.WEBRIP -> 3
        StreamPrefEncode.HDTV -> 4
        null -> Int.MAX_VALUE
    }

    private fun rankAudioTag(tag: StreamPrefAudioTag?): Int = when (tag) {
        StreamPrefAudioTag.ATMOS -> 0
        StreamPrefAudioTag.DTS_HD_MA -> 1
        StreamPrefAudioTag.DTS_X -> 2
        StreamPrefAudioTag.TRUEHD -> 3
        StreamPrefAudioTag.EAC3 -> 4
        StreamPrefAudioTag.AC3 -> 5
        StreamPrefAudioTag.AAC -> 6
        null -> Int.MAX_VALUE
    }

    private fun rankVisualTag(tag: StreamPrefVisualTag?): Int = when (tag) {
        StreamPrefVisualTag.DOLBY_VISION -> 0
        StreamPrefVisualTag.HDR10_PLUS -> 1
        StreamPrefVisualTag.HDR10 -> 2
        StreamPrefVisualTag.SDR -> 3
        null -> Int.MAX_VALUE
    }

    // ---- Extractor helpers ----

    private fun extractResolution(quality: String, combined: String): Int? {
        val text = listOf(quality, combined).joinToString(" ").lowercase()
        return when {
            hasToken(text, "2160p") || hasToken(text, "4k") || combined.contains("uhd") -> 2160
            hasToken(text, "1440p") || hasToken(text, "2k") -> 1440
            hasToken(text, "1080p") || hasToken(text, "fhd") -> 1080
            hasToken(text, "720p") || hasToken(text, "hd") -> 720
            hasToken(text, "480p") || hasToken(text, "sd") -> 480
            else -> null
        }
    }

    private fun extractVisualTags(hdr: String, combined: String): Set<StreamPrefVisualTag> {
        val text = listOf(hdr, combined).joinToString(" ").lowercase()
        val tags = mutableSetOf<StreamPrefVisualTag>()
        if (containsWord(text, "dolby vision") || containsWord(text, "dv") || containsWord(text, "dovi")) {
            tags.add(StreamPrefVisualTag.DOLBY_VISION)
        }
        if (containsWord(text, "hdr10+") || containsWord(text, "hdr10plus")) {
            tags.add(StreamPrefVisualTag.HDR10_PLUS)
        }
        if (containsWord(text, "hdr10")) {
            tags.add(StreamPrefVisualTag.HDR10)
        }
        if (hasToken(text, "sdr")) {
            tags.add(StreamPrefVisualTag.SDR)
        }
        return tags
    }

    private fun extractAudioTags(audio: String, combined: String): Set<StreamPrefAudioTag> {
        val text = listOf(audio, combined).joinToString(" ").lowercase()
        val tags = mutableSetOf<StreamPrefAudioTag>()
        if (hasToken(text, "atmos")) tags.add(StreamPrefAudioTag.ATMOS)
        if (containsWord(text, "dts-hd ma") || containsWord(text, "dtshd ma")) tags.add(StreamPrefAudioTag.DTS_HD_MA)
        if (containsWord(text, "dts:x") || containsWord(text, "dtsx")) tags.add(StreamPrefAudioTag.DTS_X)
        if (containsWord(text, "truehd") || containsWord(text, "true hd")) tags.add(StreamPrefAudioTag.TRUEHD)
        if (hasToken(text, "eac3") || hasToken(text, "e-ac3") || hasToken(text, "dd+") || hasToken(text, "ddp")) tags.add(StreamPrefAudioTag.EAC3)
        if (hasToken(text, "ac3") || hasToken(text, "dolby digital")) tags.add(StreamPrefAudioTag.AC3)
        if (hasToken(text, "aac")) tags.add(StreamPrefAudioTag.AAC)
        return tags
    }

    private fun extractAudioChannels(audio: String, combined: String): Set<StreamPrefAudioChannel> {
        val text = listOf(audio, combined).joinToString(" ").lowercase()
        val channels = mutableSetOf<StreamPrefAudioChannel>()
        if (text.contains("7.1")) channels.add(StreamPrefAudioChannel.CH_7_1)
        if (text.contains("5.1")) channels.add(StreamPrefAudioChannel.CH_5_1)
        if (text.contains("2.0")) channels.add(StreamPrefAudioChannel.STEREO)
        return channels
    }

    private fun extractCodecs(codec: String, combined: String): Set<StreamPrefCodec> {
        val text = listOf(codec, combined).joinToString(" ").lowercase()
        val codecs = mutableSetOf<StreamPrefCodec>()
        if (hasToken(text, "hevc") || hasToken(text, "h265") || hasToken(text, "x265")) codecs.add(StreamPrefCodec.HEVC)
        if (hasToken(text, "avc") || hasToken(text, "h264") || hasToken(text, "x264")) codecs.add(StreamPrefCodec.H264)
        if (hasToken(text, "av1")) codecs.add(StreamPrefCodec.AV1)
        if (hasToken(text, "vp9")) codecs.add(StreamPrefCodec.VP9)
        return codecs
    }

    private fun extractEncodes(quality: String, combined: String): Set<StreamPrefEncode> {
        val text = listOf(quality, combined).joinToString(" ").lowercase()
        val encodes = mutableSetOf<StreamPrefEncode>()
        if (hasToken(text, "remux")) encodes.add(StreamPrefEncode.REMUX)
        if (containsWord(text, "blu-ray") || containsWord(text, "bluray") || hasToken(text, "bdrip") || hasToken(text, "brrip")) encodes.add(StreamPrefEncode.BLURAY)
        if (containsWord(text, "web-dl") || containsWord(text, "webdl")) encodes.add(StreamPrefEncode.WEB_DL)
        if (containsWord(text, "webrip") || containsWord(text, "web-rip")) encodes.add(StreamPrefEncode.WEBRIP)
        if (hasToken(text, "hdtv")) encodes.add(StreamPrefEncode.HDTV)
        return encodes
    }

    private fun extractLanguage(language: String): String? {
        return language.trim().takeIf { it.isNotBlank() }?.lowercase()
    }

    private fun hasToken(text: String, token: String): Boolean {
        val lowerText = text.lowercase()
        val lowerToken = token.lowercase()
        return lowerText.contains(lowerToken)
    }

    private fun containsWord(text: String, word: String): Boolean {
        return text.lowercase().contains(word.lowercase())
    }
}

package com.omnio.tv.domain.model

import kotlinx.serialization.Serializable

@Serializable
enum class StreamPrefMinQuality(val minResolution: Int) {
    NONE(0),
    P480(480),
    P720(720),
    P1080(1080),
    P4K(2160)
}

@Serializable
enum class StreamPrefVisualTag(val label: String) {
    HDR10("HDR10"),
    HDR10_PLUS("HDR10+"),
    DOLBY_VISION("Dolby Vision"),
    SDR("SDR")
}

@Serializable
enum class StreamPrefAudioTag(val label: String) {
    ATMOS("Atmos"),
    DTS_HD_MA("DTS-HD MA"),
    DTS_X("DTS:X"),
    TRUEHD("TrueHD"),
    EAC3("E-AC3"),
    AC3("AC3"),
    AAC("AAC")
}

@Serializable
enum class StreamPrefAudioChannel(val label: String) {
    STEREO("2.0"),
    CH_5_1("5.1"),
    CH_7_1("7.1"),
    CH_9_1("9.1")
}

@Serializable
enum class StreamPrefCodec(val label: String) {
    H264("H.264"),
    HEVC("HEVC"),
    AV1("AV1"),
    VP9("VP9")
}

@Serializable
enum class StreamPrefEncode(val label: String) {
    REMUX("Remux"),
    BLURAY("BluRay"),
    WEB_DL("WEB-DL"),
    WEBRIP("WEBRip"),
    HDTV("HDTV")
}

@Serializable
enum class StreamPrefSortKey(val label: String) {
    RESOLUTION("Resolution"),
    QUALITY("Quality"),
    SIZE("Size"),
    CACHED("Cached"),
    SOURCE_CONFIDENCE("Source confidence"),
    AUDIO("Audio"),
    VISUAL_TAG("Visual tag"),
    ENCODE("Encode")
}

@Serializable
enum class StreamPrefSortDirection {
    ASC,
    DESC
}

@Serializable
data class StreamPrefSortCriterion(
    val key: StreamPrefSortKey = StreamPrefSortKey.RESOLUTION,
    val direction: StreamPrefSortDirection = StreamPrefSortDirection.DESC
) {
    companion object {
        val defaultOrder = listOf(
            StreamPrefSortCriterion(StreamPrefSortKey.RESOLUTION, StreamPrefSortDirection.DESC),
            StreamPrefSortCriterion(StreamPrefSortKey.AUDIO, StreamPrefSortDirection.DESC),
            StreamPrefSortCriterion(StreamPrefSortKey.VISUAL_TAG, StreamPrefSortDirection.DESC),
            StreamPrefSortCriterion(StreamPrefSortKey.ENCODE, StreamPrefSortDirection.DESC),
            StreamPrefSortCriterion(StreamPrefSortKey.SIZE, StreamPrefSortDirection.DESC),
            StreamPrefSortCriterion(StreamPrefSortKey.SOURCE_CONFIDENCE, StreamPrefSortDirection.DESC)
        )
    }
}

@Serializable
data class StreamPreferences(
    val enabled: Boolean = false,
    val minResolution: StreamPrefMinQuality = StreamPrefMinQuality.NONE,
    val requiredVisualTags: Set<StreamPrefVisualTag> = emptySet(),
    val excludedVisualTags: Set<StreamPrefVisualTag> = emptySet(),
    val requiredAudioTags: Set<StreamPrefAudioTag> = emptySet(),
    val excludedAudioTags: Set<StreamPrefAudioTag> = emptySet(),
    val requiredCodecs: Set<StreamPrefCodec> = emptySet(),
    val excludedCodecs: Set<StreamPrefCodec> = emptySet(),
    val requiredEncodes: Set<StreamPrefEncode> = emptySet(),
    val excludedEncodes: Set<StreamPrefEncode> = emptySet(),
    val requiredLanguages: Set<String> = emptySet(),
    val excludedLanguages: Set<String> = emptySet(),
    val minSizeBytes: Long? = null,
    val maxSizeBytes: Long? = null,
    val requireCached: Boolean = false,
    val sortCriteria: List<StreamPrefSortCriterion> = StreamPrefSortCriterion.defaultOrder,
    val preloadCount: Int = 0
) {
    companion object {
        val DEFAULT = StreamPreferences()
    }
}

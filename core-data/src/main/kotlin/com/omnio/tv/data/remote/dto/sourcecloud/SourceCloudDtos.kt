package com.omnio.tv.data.remote.dto.sourcecloud

import com.omnio.tv.domain.model.AddonStreams
import com.omnio.tv.domain.model.ProxyHeaders
import com.omnio.tv.domain.model.SourceCloudSearchRequest
import com.omnio.tv.domain.model.SourceCloudService
import com.omnio.tv.domain.model.SourceCloudServiceStatus
import com.omnio.tv.domain.model.SourceCloudStatus
import com.omnio.tv.domain.model.SourceCloudStreamMetadata
import com.omnio.tv.domain.model.Stream
import com.omnio.tv.domain.model.StreamBehaviorHints
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

const val SOURCE_CLOUD_GROUP_NAME = "Omnio Source Cloud"

@JsonClass(generateAdapter = true)
data class SourceCloudSearchRequestDto(
    @param:Json(name = "type") val type: String,
    @param:Json(name = "videoId") val videoId: String,
    @param:Json(name = "tmdbId") val tmdbId: String? = null,
    @param:Json(name = "season") val season: Int? = null,
    @param:Json(name = "episode") val episode: Int? = null
)

@JsonClass(generateAdapter = true)
data class SourceCloudSearchResponseDto(
    @param:Json(name = "streams") val streams: List<SourceCloudStreamDto>? = null
)

@JsonClass(generateAdapter = true)
data class SourceCloudStatusResponseDto(
    @param:Json(name = "services") val services: List<SourceCloudServiceStatusDto>? = null
)

@JsonClass(generateAdapter = true)
data class SourceCloudServiceStatusDto(
    @param:Json(name = "service") val service: String? = null,
    @param:Json(name = "connected") val connected: Boolean = false,
    @param:Json(name = "label") val label: String? = null,
    @param:Json(name = "message") val message: String? = null
)

@JsonClass(generateAdapter = true)
data class SourceCloudStreamDto(
    @param:Json(name = "name") val name: String? = null,
    @param:Json(name = "title") val title: String? = null,
    @param:Json(name = "description") val description: String? = null,
    @param:Json(name = "url") val url: String? = null,
    @param:Json(name = "ytId") val ytId: String? = null,
    @param:Json(name = "infoHash") val infoHash: String? = null,
    @param:Json(name = "fileIdx") val fileIdx: Int? = null,
    @param:Json(name = "externalUrl") val externalUrl: String? = null,
    @param:Json(name = "behaviorHints") val behaviorHints: SourceCloudBehaviorHintsDto? = null,
    @param:Json(name = "metadata") val metadata: SourceCloudStreamMetadataDto? = null
)

@JsonClass(generateAdapter = true)
data class SourceCloudBehaviorHintsDto(
    @param:Json(name = "notWebReady") val notWebReady: Boolean? = null,
    @param:Json(name = "bingeGroup") val bingeGroup: String? = null,
    @param:Json(name = "countryWhitelist") val countryWhitelist: List<String>? = null,
    @param:Json(name = "requestHeaders") val requestHeaders: Map<String, String>? = null,
    @param:Json(name = "responseHeaders") val responseHeaders: Map<String, String>? = null,
    @param:Json(name = "videoHash") val videoHash: String? = null,
    @param:Json(name = "videoSize") val videoSize: Long? = null,
    @param:Json(name = "filename") val filename: String? = null
)

@JsonClass(generateAdapter = true)
data class SourceCloudStreamMetadataDto(
    @param:Json(name = "quality") val quality: String? = null,
    @param:Json(name = "sizeBytes") val sizeBytes: Long? = null,
    @param:Json(name = "codec") val codec: String? = null,
    @param:Json(name = "audio") val audio: String? = null,
    @param:Json(name = "hdr") val hdr: String? = null,
    @param:Json(name = "language") val language: String? = null,
    @param:Json(name = "cached") val cached: Boolean? = null,
    @param:Json(name = "sourceConfidence") val sourceConfidence: Double? = null,
    @param:Json(name = "sourceService") val sourceService: String? = null
)

fun SourceCloudSearchRequest.toDto(): SourceCloudSearchRequestDto = SourceCloudSearchRequestDto(
    type = type,
    videoId = videoId,
    tmdbId = tmdbId,
    season = season,
    episode = episode
)

fun SourceCloudSearchResponseDto.toDomain(): AddonStreams? {
    val domainStreams = streams.orEmpty().map { it.toDomain() }
    if (domainStreams.isEmpty()) return null

    return AddonStreams(
        addonName = SOURCE_CLOUD_GROUP_NAME,
        addonLogo = null,
        streams = domainStreams
    )
}

fun SourceCloudStatusResponseDto.toDomain(enabled: Boolean, baseUrlConfigured: Boolean): SourceCloudStatus =
    SourceCloudStatus(
        enabled = enabled,
        baseUrlConfigured = baseUrlConfigured,
        services = services.orEmpty().mapNotNull { it.toDomain() }
    )

fun SourceCloudServiceStatusDto.toDomain(): SourceCloudServiceStatus? {
    val sourceCloudService = service?.let { SourceCloudService.fromKey(it) } ?: return null
    return SourceCloudServiceStatus(
        service = sourceCloudService,
        connected = connected,
        label = label ?: sourceCloudService.displayName,
        message = message
    )
}

fun SourceCloudStreamDto.toDomain(): Stream = Stream(
    name = name,
    title = title,
    description = description,
    url = url,
    ytId = ytId,
    infoHash = infoHash,
    fileIdx = fileIdx,
    externalUrl = externalUrl,
    behaviorHints = behaviorHints?.toDomain(),
    addonName = SOURCE_CLOUD_GROUP_NAME,
    addonLogo = null,
    sourceProvider = "source_cloud",
    sourceCloudMetadata = metadata?.toDomain()
)

fun SourceCloudBehaviorHintsDto.toDomain(): StreamBehaviorHints = StreamBehaviorHints(
    notWebReady = notWebReady,
    bingeGroup = bingeGroup,
    countryWhitelist = countryWhitelist,
    proxyHeaders = ProxyHeaders(
        request = sanitizeHeaderMap(requestHeaders),
        response = sanitizeHeaderMap(responseHeaders)
    ).takeIf { it.request != null || it.response != null },
    videoHash = videoHash,
    videoSize = videoSize,
    filename = filename
)

fun SourceCloudStreamMetadataDto.toDomain(): SourceCloudStreamMetadata = SourceCloudStreamMetadata(
    quality = quality,
    sizeBytes = sizeBytes,
    codec = codec,
    audio = audio,
    hdr = hdr,
    language = language,
    cached = cached,
    sourceConfidence = sourceConfidence,
    sourceService = sourceService?.let { SourceCloudService.fromKey(it) }
)

private fun sanitizeHeaderMap(headers: Map<String, String>?): Map<String, String>? {
    if (headers == null) return null
    if (headers.isEmpty()) return null

    val sanitized = LinkedHashMap<String, String>(headers.size)
    headers.forEach { (rawKey, rawValue) ->
        val key = rawKey.trim()
        val value = rawValue.trim()
        if (key.isEmpty() || value.isEmpty()) return@forEach
        if (key.equals("Range", ignoreCase = true)) return@forEach
        sanitized[key] = value
    }
    return sanitized.takeIf { it.isNotEmpty() }
}

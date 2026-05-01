package com.omnio.tv.data.remote.dto.aiostreams

import com.omnio.tv.domain.model.AioStreamsConfigInnerDto

/**
 * Minimal first-time config based on upstream DefaultUserData. Keep this sparse
 * but valid so the upstream schema and web UI can initialize cleanly.
 */
object AioStreamsDefaultConfig {

    private val serviceIds = listOf(
        "realdebrid",
        "debridlink",
        "premiumize",
        "alldebrid",
        "torbox",
        "easydebrid",
        "debrider",
        "putio",
        "pikpak",
        "offcloud",
        "seedr",
        "easynews",
        "nzbdav",
        "altmount",
        "stremio_nntp",
        "stremthru_newz",
    )

    private val preferredQualities = listOf(
        "BluRay REMUX",
        "BluRay",
        "WEB-DL",
        "WEBRip",
        "HDRip",
        "HC HD-Rip",
        "DVDRip",
        "HDTV",
        "CAM",
        "TS",
        "TC",
        "SCR",
        "Unknown",
    )

    private val preferredResolutions = listOf(
        "2160p",
        "1440p",
        "1080p",
        "720p",
        "576p",
        "480p",
        "360p",
        "240p",
        "144p",
        "Unknown",
    )

    private val defaultSortCriteria = listOf(
        sortCriterion("cached"),
        sortCriterion("library"),
        sortCriterion("resolution"),
        sortCriterion("quality"),
        sortCriterion("streamExpressionScore"),
        sortCriterion("regexPatterns"),
        sortCriterion("streamType"),
        sortCriterion("visualTag"),
        sortCriterion("audioTag"),
        sortCriterion("audioChannel"),
        sortCriterion("encode"),
        sortCriterion("language"),
        sortCriterion("subtitle"),
        sortCriterion("size"),
    )

    fun build(): AioStreamsConfigInnerDto = AioStreamsConfigInnerDto(
        config = linkedMapOf(
            "services" to serviceIds.map { serviceId ->
                mapOf(
                    "id" to serviceId,
                    "enabled" to false,
                    "credentials" to emptyMap<String, Any?>(),
                )
            },
            "presets" to emptyList<Any?>(),
            "formatter" to mapOf("id" to "gdrive"),
            "preferredQualities" to preferredQualities,
            "preferredResolutions" to preferredResolutions,
            "excludedQualities" to listOf("CAM", "SCR", "TS", "TC"),
            "excludedVisualTags" to listOf("3D"),
            "sortCriteria" to mapOf("global" to defaultSortCriteria),
            "posterService" to "rpdb",
            "deduplicator" to mapOf(
                "enabled" to true,
                "keys" to listOf("filename", "infoHash"),
                "multiGroupBehaviour" to "aggressive",
                "cached" to "single_result",
                "uncached" to "per_service",
                "p2p" to "single_result",
                "http" to "disabled",
                "live" to "disabled",
                "youtube" to "disabled",
                "external" to "disabled",
                "smartDetectAttributes" to listOf(
                    "size",
                    "resolution",
                    "quality",
                    "visualTags",
                    "audioTags",
                    "audioChannels",
                    "languages",
                    "encode",
                    "edition",
                    "network",
                    "remastered",
                ),
                "smartDetectRounding" to 10,
                "libraryBehaviour" to "ignore",
            ),
            "autoPlay" to mapOf(
                "enabled" to true,
                "method" to "matchingFile",
                "attributes" to listOf("resolution", "quality", "releaseGroup"),
            ),
            "cacheAndPlay" to mapOf(
                "enabled" to false,
                "streamTypes" to listOf("usenet"),
            ),
            "statistics" to mapOf(
                "enabled" to false,
                "position" to "bottom",
                "statsToShow" to listOf("addon", "filter", "timing"),
                "showFilterStatsOnNoStreams" to true,
            ),
            "digitalReleaseFilter" to mapOf(
                "enabled" to false,
                "tolerance" to 0,
                "requestTypes" to emptyList<String>(),
                "addons" to emptyList<String>(),
                "showInfoOnFilter" to true,
            ),
            "ageRangeTypes" to listOf("usenet"),
            "seasonEpisodeMatching" to mapOf(
                "addons" to emptyList<String>(),
                "requestTypes" to emptyList<String>(),
            ),
            "yearMatching" to mapOf(
                "addons" to emptyList<String>(),
                "requestTypes" to emptyList<String>(),
            ),
            "titleMatching" to mapOf(
                "addons" to emptyList<String>(),
                "requestTypes" to emptyList<String>(),
            ),
            "precacheNextEpisode" to false,
            "precacheSingleStream" to true,
            "precacheSelector" to "count(cached(streams)) == 0 ? uncached(streams) : []",
            "enableSeadex" to true,
            "regexOverrides" to emptyList<Any?>(),
            "checkOwned" to true,
        )
    )

    private fun sortCriterion(key: String) = mapOf(
        "key" to key,
        "direction" to "desc",
    )
}

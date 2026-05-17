package com.omnio.tv.core.player

import com.omnio.tv.data.local.StreamAutoPlayMode
import com.omnio.tv.data.local.StreamAutoPlaySource
import com.omnio.tv.domain.model.AddonStreams
import com.omnio.tv.domain.model.Stream

object StreamAutoPlaySelector {
    private val providerPriority = mapOf(
        "source_cloud" to 0,
        "emby" to 1,
        "jellyfin" to 2,
        "plex" to 3
    )

    fun orderAddonStreams(
        streams: List<AddonStreams>,
        installedOrder: List<String>
    ): List<AddonStreams> {
        if (streams.isEmpty()) return streams

        val providerEntries = streams.filter { group ->
            group.streams.any { it.providerPriority() != null }
        }
        val orderedProviders = providerEntries.sortedWith(
            compareBy<AddonStreams> { group ->
                group.streams
                    .mapNotNull { stream ->
                        stream.providerPriority()
                    }
                    .minOrNull()
                    ?: Int.MAX_VALUE
            }.thenBy { it.addonName }
        )

        val nonProviderEntries = streams.filterNot { group ->
            group.streams.any { it.providerPriority() != null }
        }
        val (addonEntries, pluginEntries) = nonProviderEntries.partition { it.addonName in installedOrder }
        val orderedAddons = addonEntries.sortedBy { installedOrder.indexOf(it.addonName) }
        return orderedProviders + orderedAddons + pluginEntries
    }

    private fun resolvePlayableUrl(stream: Stream): String? {
        val url = stream.getStreamUrl() ?: return null

        return url
    }

    private fun Stream.providerPriority(): Int? {
        val provider = sourceProvider?.lowercase() ?: return null
        return providerPriority[provider]
    }



    fun selectAutoPlayStream(
        streams: List<Stream>,
        mode: StreamAutoPlayMode,
        regexPattern: String,
        source: StreamAutoPlaySource,
        installedAddonNames: Set<String>,
        selectedAddons: Set<String>,
        selectedPlugins: Set<String>,
        preferredBingeGroup: String? = null,
        preferBingeGroupInSelection: Boolean = false
    ): Stream? {
        if (streams.isEmpty()) return null

        val hasSelectedFilters = selectedAddons.isNotEmpty() || selectedPlugins.isNotEmpty()
        val sourceScopedStreams = when (source) {
            StreamAutoPlaySource.ALL_SOURCES -> streams.filter { stream ->
                stream.sourceProvider.isNullOrBlank() || !hasSelectedFilters ||
                    stream.addonName in selectedAddons || stream.addonName in selectedPlugins
            }
            StreamAutoPlaySource.INSTALLED_ADDONS_ONLY -> streams.filter { stream ->
                stream.addonName in installedAddonNames
            }
            StreamAutoPlaySource.ENABLED_PLUGINS_ONLY -> streams.filter { stream ->
                if (!stream.sourceProvider.isNullOrBlank()) {
                    stream.addonName in selectedPlugins
                } else {
                    stream.addonName !in installedAddonNames
                }
            }
        }
        val candidateStreams = sourceScopedStreams.filter { stream ->
            val isAddonStream = stream.addonName in installedAddonNames
            if (!stream.sourceProvider.isNullOrBlank()) {
                !hasSelectedFilters || stream.addonName in selectedAddons || stream.addonName in selectedPlugins
            } else if (isAddonStream) {
                selectedAddons.isEmpty() || stream.addonName in selectedAddons
            } else {
                selectedPlugins.isEmpty() || stream.addonName in selectedPlugins
            }
        }
        if (candidateStreams.isEmpty()) return null
        if (mode == StreamAutoPlayMode.MANUAL) return null

        val targetBingeGroup = preferredBingeGroup?.trim().orEmpty()
        if (preferBingeGroupInSelection && targetBingeGroup.isNotEmpty()) {
            val bingeGroupMatch = candidateStreams.firstOrNull { stream ->
                stream.behaviorHints?.bingeGroup == targetBingeGroup && stream.getStreamUrl() != null
            }
            if (bingeGroupMatch != null) return bingeGroupMatch
        }

        return when (mode) {
            StreamAutoPlayMode.MANUAL -> null
            StreamAutoPlayMode.FIRST_STREAM -> candidateStreams.firstOrNull { it.getStreamUrl() != null }
            StreamAutoPlayMode.REGEX_MATCH -> {
                val pattern = regexPattern.trim()
 
                // Try to compile the user regex
                val userRegex = runCatching { Regex(pattern, RegexOption.IGNORE_CASE) }.getOrNull()
                if (userRegex == null) return null

                // Auto-extract exclusion patterns from negative lookaheads
                val exclusionMatches = Regex("\\(\\?![^)]*?\\(([^)]+)\\)").findAll(pattern)

                val exclusionWords = exclusionMatches
                    .flatMap { match -> match.groupValues[1].split("|") }
                    .map { it.trim() }
                    .filter { it.isNotBlank() }
                    .toList()

                val excludeRegex = if (exclusionWords.isNotEmpty()) {
                    Regex("\\b(${exclusionWords.joinToString("|")})\\b", RegexOption.IGNORE_CASE)
                } else null

                // 1. Build list of ALL regex‑matching streams
                val matchingStreams = candidateStreams.filter { stream ->
                    val url = stream.getStreamUrl() ?: return@filter false

                    val searchableText = buildString {
                        append(stream.addonName).append(' ')
                        append(stream.name.orEmpty()).append(' ')
                        append(stream.title.orEmpty()).append(' ')
                        append(stream.description.orEmpty()).append(' ')
                        append(url)
                    }

                    // Must match include pattern
                    if (!userRegex.containsMatchIn(searchableText)) return@filter false

                    // Must NOT match exclusion pattern
                    if (excludeRegex != null && excludeRegex.containsMatchIn(searchableText)) {
                        return@filter false
                    }

                    true
                }

                if (matchingStreams.isEmpty()) return null
                matchingStreams.firstOrNull { resolvePlayableUrl(it) != null }
            }

        }
    }
}

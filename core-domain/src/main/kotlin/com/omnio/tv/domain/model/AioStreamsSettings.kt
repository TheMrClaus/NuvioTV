package com.omnio.tv.domain.model

/**
 * Cached bridge-table row for the current user. Upstream (the self-hosted
 * Viren070/AIOStreams instance) is the source of truth for the config payload;
 * here we only keep what the app needs to render the settings hub and install
 * the addon URL.
 */
data class AioStreamsSettings(
    val enabled: Boolean = false,
    val aioUuid: String = "",
    val manifestUrl: String = "",
    val lastSyncedAt: Long = 0L,
)

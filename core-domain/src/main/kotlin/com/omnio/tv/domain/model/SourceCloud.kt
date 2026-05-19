package com.omnio.tv.domain.model

enum class SourceCloudService(val key: String, val displayName: String) {
    REAL_DEBRID("real_debrid", "Real-Debrid"),
    TORBOX("torbox", "Torbox");

    companion object {
        fun fromKey(key: String): SourceCloudService? = entries.firstOrNull { it.key == key }
    }
}

data class SourceCloudSettings(
    val enabled: Boolean = true,
    val connectedServices: Set<SourceCloudService> = emptySet()
) {
    val hasConnectedService: Boolean
        get() = connectedServices.isNotEmpty()
}

data class SourceCloudStatus(
    val enabled: Boolean,
    val baseUrlConfigured: Boolean,
    val config: SourceCloudConfigState = SourceCloudConfigState(),
    val services: List<SourceCloudServiceStatus>
) {
    val hasConnectedService: Boolean
        get() = services.any { it.connected }
}

enum class SourceCloudConfigStatus(val key: String) {
    UNKNOWN("unknown"),
    NOT_PROVISIONED("not_provisioned"),
    READY("ready"),
    PROVISIONING_FAILED("provisioning_failed"),
    UNAVAILABLE("unavailable"),
    INVALID("invalid");

    companion object {
        fun fromKey(key: String?): SourceCloudConfigStatus =
            entries.firstOrNull { it.key == key } ?: UNKNOWN
    }
}

data class SourceCloudConfigState(
    val status: SourceCloudConfigStatus = SourceCloudConfigStatus.UNKNOWN,
    val label: String? = null,
    val message: String? = null,
    val advancedConfigAvailable: Boolean = false,
    val canReset: Boolean = false
)

data class SourceCloudAdvancedConfigSession(
    val url: String,
    val expiresAtEpochMillis: Long? = null,
    val message: String? = null,
    val configurePassword: String? = null,
    val directConfigureUrl: String? = null
)

data class SourceCloudServiceStatus(
    val service: SourceCloudService,
    val connected: Boolean,
    val label: String = service.displayName,
    val message: String? = null
)

data class SourceCloudSearchRequest(
    val type: String,
    val videoId: String,
    val tmdbId: String?,
    val season: Int?,
    val episode: Int?
)

data class SourceCloudStreamMetadata(
    val quality: String? = null,
    val sizeBytes: Long? = null,
    val codec: String? = null,
    val audio: String? = null,
    val hdr: String? = null,
    val language: String? = null,
    val cached: Boolean? = null,
    val sourceConfidence: Double? = null,
    val sourceService: SourceCloudService? = null
)

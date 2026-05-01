package com.omnio.tv.domain.model

/**
 * Opaque config payload for AIOStreams.
 *
 * AIOStreams persists debrid services, sort options, formatter settings, etc.
 * For Phase 1 we treat the entire inner shape as an opaque map so the client
 * can round-trip the upstream payload without owning every field's schema.
 * Specific fields can be surfaced in the UI in a later phase.
 */
data class AioStreamsConfigInnerDto(
    val config: Map<String, Any?> = emptyMap(),
)

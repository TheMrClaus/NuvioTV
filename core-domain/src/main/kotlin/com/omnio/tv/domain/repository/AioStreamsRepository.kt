package com.omnio.tv.domain.repository

import com.omnio.tv.domain.model.AgeRatingTier
import com.omnio.tv.domain.model.AioStreamsConfigInnerDto
import com.omnio.tv.domain.model.AioStreamsSettings
import kotlinx.coroutines.flow.Flow

interface AioStreamsRepository {
    val settings: Flow<AioStreamsSettings>

    fun cachedConfig(): AioStreamsConfigInnerDto?

    suspend fun refresh(): Result<AioStreamsConfigInnerDto?>

    suspend fun createConfig(config: AioStreamsConfigInnerDto): Result<CreateConfigResult>

    suspend fun updateConfig(
        uuid: String,
        config: AioStreamsConfigInnerDto,
    ): Result<AioStreamsConfigInnerDto>

    suspend fun deleteConfig(uuid: String): Result<Unit>

    suspend fun setEnabled(enabled: Boolean, manifestUrl: String): Result<Unit>

    suspend fun getConfigPassword(): String?

    /**
     * Provisions a fresh AIOStreams config for [targetProfileId] derived from
     * the Main profile's existing config. For Phase 1 the config is copied
     * verbatim regardless of [kidsMaxAgeRating] — AIOStreams does not expose
     * catalog filters the way AIOMetadata does, so Kids stream filtering is
     * deferred until the inner config is modelled.
     */
    suspend fun provisionFromMain(
        targetProfileId: Int,
        kidsMaxAgeRating: AgeRatingTier? = null,
    ): Result<CreateConfigResult>

    data class CreateConfigResult(val uuid: String, val manifestUrl: String)
}

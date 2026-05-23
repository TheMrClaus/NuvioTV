package com.omnio.tv.domain.repository

import com.omnio.tv.domain.model.AioConfigInnerDto
import com.omnio.tv.domain.model.AgeRatingTier
import com.omnio.tv.domain.model.AioMetadataSettings
import kotlinx.coroutines.flow.Flow

interface AioMetadataRepository {
    val settings: Flow<AioMetadataSettings>

    fun cachedConfig(): AioConfigInnerDto?

    suspend fun refresh(): Result<AioConfigInnerDto?>

    suspend fun createConfig(config: AioConfigInnerDto): Result<CreateConfigResult>

    suspend fun updateConfig(
        uuid: String,
        config: AioConfigInnerDto,
    ): Result<AioConfigInnerDto>

    suspend fun setEnabled(enabled: Boolean, manifestUrl: String): Result<Unit>

    suspend fun getConfigPassword(): String?

    /**
     * Provisions a fresh AIOMetadata config for [targetProfileId] from one of
     * the bundled templates and saves it upstream. Persists the bridge row in
     * `aio_metadata_links`, seeds the local DataStore, and swaps Main's
     * manifest URL out of the profile's addon list (if present) for the new
     * per-profile manifest.
     *
     * Template selection:
     *  - [isKids] = true → kids template (R.raw.aiometadata_kids_config). Main's
     *    API keys are always copied in, regardless of [copyKeysFromMain].
     *  - [isKids] = false → default template (R.raw.aiometadata_default_config).
     *    Main's API keys are copied only when [copyKeysFromMain] is true.
     *
     * The profile's `aioSharing` mode is recorded by the caller; this function
     * handles only the initial provisioning step. Future Main updates are
     * propagated through [updateConfig]'s fan-out per each profile's stored
     * sharing mode.
     */
    suspend fun provisionForNewProfile(
        targetProfileId: Int,
        isKids: Boolean,
        copyKeysFromMain: Boolean,
        kidsMaxAgeRating: AgeRatingTier? = null,
    ): Result<CreateConfigResult>

    /**
     * Re-applies the Kids template to the existing upstream AIOMetadata config
     * for [profileId] while preserving its UUID, password, manifest URL, and
     * place in the profile's addon list. Also writes `ageRating` to the chosen
     * tier label so the upstream's built-in age filter agrees with the
     * template-baked catalog filters. Intended for in-place rating changes on
     * an already-provisioned Kids profile.
     *
     * No-op for the primary profile (id=1). For non-Kids profiles, prefer
     * [updateConfig] with a plain `ageRating` change — that doesn't touch
     * catalogs.
     */
    suspend fun reapplyKidsOverlay(
        profileId: Int,
        maxAgeRating: AgeRatingTier?,
    ): Result<AioConfigInnerDto>

    data class CreateConfigResult(val uuid: String, val manifestUrl: String)
}

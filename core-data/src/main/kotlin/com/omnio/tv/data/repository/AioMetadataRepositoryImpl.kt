package com.omnio.tv.data.repository

import android.content.Context
import android.util.Log
import com.omnio.tv.data.BuildConfig
import com.omnio.tv.domain.auth.AuthManager
import com.omnio.tv.domain.profile.ProfileManager
import com.omnio.tv.data.local.AioMetadataSettingsDataStore
import com.omnio.tv.data.remote.api.AioMetadataApi
import com.omnio.tv.data.local.AddonPreferences
import com.omnio.tv.domain.model.AioConfigInnerDto
import com.omnio.tv.data.remote.dto.aiometadata.AioConfigLoadRequestDto
import com.omnio.tv.data.remote.dto.aiometadata.AioConfigSaveRequestDto
import com.omnio.tv.data.remote.dto.aiometadata.AioConfigUpdateRequestDto
import com.omnio.tv.data.remote.dto.aiometadata.AioMetadataDefaultConfig
import com.omnio.tv.domain.model.AgeRatingTier
import com.omnio.tv.domain.model.AioMetadataSettings
import com.omnio.tv.domain.model.AioSharingMode
import com.omnio.tv.domain.repository.AddonRepository
import com.omnio.tv.domain.repository.AioMetadataRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.jan.supabase.postgrest.Postgrest
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.security.SecureRandom
import java.util.Base64
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "AioMetadataRepository"
private const val TABLE = "aio_metadata_links"

/**
 * Coordinates the three sources of truth for AIOMetadata:
 *  - upstream (cedya77/aiometadata): API keys + catalog config
 *  - Supabase aio_metadata_links: user_id → aio_uuid + config_password + manifest_url + enabled
 *  - local DataStore: fast-path cache so the settings screen renders without a round-trip
 *
 * Upstream gates every save/update/load on a per-user password we mint on first
 * save (see getOrCreatePassword). Supabase is authoritative across devices;
 * DataStore is just a warm cache.
 */
@Singleton
class AioMetadataRepositoryImpl @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val api: AioMetadataApi,
    private val postgrest: Postgrest,
    private val dataStore: AioMetadataSettingsDataStore,
    private val authManager: AuthManager,
    private val profileManager: ProfileManager,
    private val addonRepository: AddonRepository,
    private val addonPreferences: AddonPreferences,
) : AioMetadataRepository {

    override val settings: Flow<AioMetadataSettings> = dataStore.settings

    @Volatile
    private var currentConfig: AioConfigInnerDto? = null

    override fun cachedConfig(): AioConfigInnerDto? = currentConfig

    override suspend fun refresh(): Result<AioConfigInnerDto?> = runCatching {
        val link = fetchLink() ?: run {
            currentConfig = null
            return@runCatching null
        }

        // Mirror Supabase password into the local DataStore so updateConfig and
        // subsequent loads can run without another round-trip.
        val password = link.configPassword?.takeIf { it.isNotBlank() }
            ?: dataStore.getConfigPassword()?.takeIf { it.isNotBlank() }
        if (password == null) {
            Log.w(TAG, "link row missing config_password for uuid=${link.aioUuid}; cannot load upstream config")
            currentConfig = null
            return@runCatching null
        }
        if (link.configPassword != null) dataStore.setConfigPassword(password)

        val response = api.loadConfig(link.aioUuid, AioConfigLoadRequestDto(password = password))
        if (!response.isSuccessful) {
            error("loadConfig failed: HTTP ${response.code()}")
        }
        val body = response.body() ?: error("loadConfig empty body")
        val inner = body.config
        currentConfig = inner

        dataStore.replaceAll(
            AioMetadataSettings(
                enabled = link.enabled,
                aioUuid = link.aioUuid,
                manifestUrl = link.manifestUrl.orEmpty(),
                lastSyncedAt = System.currentTimeMillis(),
            )
        )
        inner
    }.onFailure { Log.w(TAG, "refresh failed", it) }

    override suspend fun createConfig(config: AioConfigInnerDto): Result<AioMetadataRepository.CreateConfigResult> = runCatching {
        val password = getOrCreatePassword()
        val response = api.saveConfig(
            AioConfigSaveRequestDto(config = config, password = password, addonPassword = null)
        )
        if (!response.isSuccessful) {
            error("saveConfig failed: HTTP ${response.code()}")
        }
        val body = response.body() ?: error("saveConfig empty body")
        currentConfig = config

        val manifestUrl = body.installUrl ?: buildFallbackManifestUrl(body.userUUID)
        upsertLink(
            aioUuid = body.userUUID,
            manifestUrl = manifestUrl,
            enabled = false,
            configPassword = password,
        )
        dataStore.replaceAll(
            AioMetadataSettings(
                enabled = false,
                aioUuid = body.userUUID,
                manifestUrl = manifestUrl,
                lastSyncedAt = System.currentTimeMillis(),
            )
        )
        AioMetadataRepository.CreateConfigResult(uuid = body.userUUID, manifestUrl = manifestUrl)
    }.onFailure { Log.w(TAG, "createConfig failed", it) }

    override suspend fun updateConfig(uuid: String, config: AioConfigInnerDto): Result<AioConfigInnerDto> =
        runCatching {
            val password = dataStore.getConfigPassword()?.takeIf { it.isNotBlank() }
                ?: fetchLink()?.configPassword?.takeIf { it.isNotBlank() }
                ?: error("No AIOMetadata password stored; re-create the config.")

            val response = api.updateConfig(
                uuid = uuid,
                body = AioConfigUpdateRequestDto(config = config, password = password, addonPassword = null),
            )
            if (!response.isSuccessful) {
                error("updateConfig failed: HTTP ${response.code()}")
            }
            val body = response.body() ?: error("updateConfig empty body")
            currentConfig = config

            body.installUrl?.takeIf { it.isNotBlank() }?.let { dataStore.setManifestUrl(it) }
            dataStore.markSynced(System.currentTimeMillis())

            // Refresh the bridge row so manifestUrl stays in sync across devices.
            body.installUrl?.takeIf { it.isNotBlank() }?.let { freshManifest ->
                fetchLink()?.let { link ->
                    if (link.manifestUrl != freshManifest) {
                        upsertLink(
                            aioUuid = link.aioUuid,
                            manifestUrl = freshManifest,
                            enabled = link.enabled,
                            configPassword = password,
                        )
                    }
                }
            }

            // Only Main acts as the source for fan-out. Sibling profiles in
            // FULL_MIRROR/KEYS_ONLY pick up the new keys (and, for FULL_MIRROR,
            // the rest of the config with their own kid-tweaks layered back on).
            if (activeProfileId() == 1) {
                runCatching { propagateMainConfigToSiblings(config) }
                    .onFailure { Log.w(TAG, "propagateMainConfigToSiblings failed", it) }
            }
            config
        }.onFailure { Log.w(TAG, "updateConfig failed", it) }

    private suspend fun propagateMainConfigToSiblings(mainConfig: AioConfigInnerDto) {
        val mainKeys = mainConfig.apiKeys
        profileManager.profiles.value
            .filter { it.id != 1 && it.aioSharing != AioSharingMode.INDEPENDENT }
            .forEach { sibling ->
                val link = fetchLink(profileId = sibling.id) ?: return@forEach
                val siblingPassword = link.configPassword?.takeIf { it.isNotBlank() } ?: return@forEach

                val targetConfig = when (sibling.aioSharing) {
                    AioSharingMode.FULL_MIRROR -> if (sibling.isKids) {
                        // Kids profile in FULL_MIRROR is degenerate (Kids should
                        // not adopt Main's catalogs verbatim). Rebake the kids
                        // template with Main's just-updated keys + the chosen
                        // age tier.
                        val rebuilt = AioMetadataDefaultConfig.buildKids(appContext, mainKeys)
                        rebuilt.copy(
                            settings = rebuilt.settings + mapOf(
                                "ageRating" to (sibling.maxAgeRating?.label ?: "None"),
                            )
                        )
                    } else {
                        mainConfig
                    }
                    AioSharingMode.KEYS_ONLY -> {
                        val loadResp = api.loadConfig(
                            link.aioUuid,
                            AioConfigLoadRequestDto(password = siblingPassword)
                        )
                        if (!loadResp.isSuccessful) return@forEach
                        val current = loadResp.body()?.config ?: return@forEach
                        if (current.apiKeys == mainKeys) return@forEach
                        current.copy(apiKeys = mainKeys)
                    }
                    AioSharingMode.INDEPENDENT -> return@forEach
                }

                val updateResp = api.updateConfig(
                    uuid = link.aioUuid,
                    body = AioConfigUpdateRequestDto(
                        config = targetConfig,
                        password = siblingPassword,
                        addonPassword = null
                    ),
                )
                if (!updateResp.isSuccessful) {
                    Log.w(TAG, "fan-out updateConfig failed for profile ${sibling.id}: HTTP ${updateResp.code()}")
                }
            }
    }

    override suspend fun getConfigPassword(): String? = dataStore.getConfigPassword()

    override suspend fun provisionForNewProfile(
        targetProfileId: Int,
        isKids: Boolean,
        copyKeysFromMain: Boolean,
        kidsMaxAgeRating: AgeRatingTier?,
    ): Result<AioMetadataRepository.CreateConfigResult> = runCatching {
        if (targetProfileId == 1) error("Cannot provision a per-profile AIO for the primary profile")

        // Kids profiles always inherit Main's keys; non-kids inherit only when
        // the caller asked. Loading Main's config is the only way to get them
        // — we tolerate a missing/failed Main config for non-kids since they
        // can run on the default template's RPDB-free baseline.
        val wantsMainKeys = isKids || copyKeysFromMain
        val mainLink = if (wantsMainKeys) fetchLink(profileId = 1) else null
        val mainKeys: Map<String, String> = if (wantsMainKeys && mainLink != null) {
            val mainPassword = mainLink.configPassword?.takeIf { it.isNotBlank() }
            if (mainPassword == null) {
                if (isKids) error("Main profile AIOMetadata link missing password — open Main's AIOMetadata settings once to back-fill")
                emptyMap()
            } else {
                val mainLoad = api.loadConfig(mainLink.aioUuid, AioConfigLoadRequestDto(password = mainPassword))
                if (!mainLoad.isSuccessful) {
                    if (isKids) error("loadConfig (main) failed: HTTP ${mainLoad.code()}")
                    emptyMap()
                } else {
                    mainLoad.body()?.config?.apiKeys.orEmpty()
                }
            }
        } else if (isKids) {
            error("Main profile has no AIOMetadata config to copy keys from")
        } else {
            emptyMap()
        }

        val initialConfig = if (isKids) {
            val kidsBase = AioMetadataDefaultConfig.buildKids(appContext, mainKeys)
            // The kids template encodes a default `ageRating` setting; sync it
            // to the profile-chosen tier when supplied so the upstream filter
            // agrees with the template's baked-in catalog filters.
            if (kidsMaxAgeRating != null) {
                kidsBase.copy(
                    settings = kidsBase.settings + mapOf("ageRating" to kidsMaxAgeRating.label)
                )
            } else kidsBase
        } else {
            AioMetadataDefaultConfig.build(appContext, mainKeys)
        }

        val targetPassword = generatePassword()
        val saveResponse = api.saveConfig(
            AioConfigSaveRequestDto(config = initialConfig, password = targetPassword, addonPassword = null)
        )
        if (!saveResponse.isSuccessful) {
            error("saveConfig failed: HTTP ${saveResponse.code()}")
        }
        val saveBody = saveResponse.body() ?: error("saveConfig empty body")
        val manifestUrl = saveBody.installUrl ?: buildFallbackManifestUrl(saveBody.userUUID)

        upsertLink(
            aioUuid = saveBody.userUUID,
            manifestUrl = manifestUrl,
            enabled = true,
            configPassword = targetPassword,
            profileId = targetProfileId,
        )

        dataStore.seedForProfile(
            profileId = targetProfileId,
            settings = AioMetadataSettings(
                enabled = true,
                aioUuid = saveBody.userUUID,
                manifestUrl = manifestUrl,
                lastSyncedAt = System.currentTimeMillis(),
            ),
            configPassword = targetPassword,
        )

        // Swap Main's AIO manifest URL out of the target profile's addon list
        // (if it ended up there via a "copy addons from main" snapshot), then
        // add the new per-profile manifest. Without this, the Kids profile
        // would carry both manifests and Main's un-filtered catalogs would
        // shadow the kid-tuned ones.
        val mainManifestUrl = mainLink?.manifestUrl?.takeIf { it.isNotBlank() }
        if (mainManifestUrl != null) {
            addonPreferences.removeAddonFromProfile(targetProfileId, mainManifestUrl)
        }
        if (manifestUrl.isNotBlank()) {
            addonPreferences.addAddonToProfile(targetProfileId, manifestUrl)
        }

        AioMetadataRepository.CreateConfigResult(uuid = saveBody.userUUID, manifestUrl = manifestUrl)
    }.onFailure { Log.w(TAG, "provisionForNewProfile failed", it) }

    override suspend fun reapplyKidsOverlay(
        profileId: Int,
        maxAgeRating: AgeRatingTier?,
    ): Result<AioConfigInnerDto> = runCatching {
        if (profileId == 1) error("Cannot apply Kids overlay to the primary profile")

        val link = fetchLink(profileId = profileId)
            ?: error("Profile $profileId has no AIOMetadata config to update")
        val password = link.configPassword?.takeIf { it.isNotBlank() }
            ?: error("Profile $profileId AIOMetadata link missing password")

        // Pull Main's keys so the kids template re-bake doesn't lose them.
        val mainLink = fetchLink(profileId = 1)
        val mainKeys: Map<String, String> = if (mainLink != null) {
            val mainPassword = mainLink.configPassword?.takeIf { it.isNotBlank() }
            if (mainPassword != null) {
                val mainLoad = api.loadConfig(mainLink.aioUuid, AioConfigLoadRequestDto(password = mainPassword))
                if (mainLoad.isSuccessful) mainLoad.body()?.config?.apiKeys.orEmpty() else emptyMap()
            } else emptyMap()
        } else emptyMap()

        val rebuilt = AioMetadataDefaultConfig.buildKids(appContext, mainKeys)
        val withAgeRating = rebuilt.copy(
            settings = rebuilt.settings + mapOf(
                "ageRating" to (maxAgeRating?.label ?: "None"),
            )
        )

        val updateResp = api.updateConfig(
            uuid = link.aioUuid,
            body = AioConfigUpdateRequestDto(
                config = withAgeRating,
                password = password,
                addonPassword = null,
            ),
        )
        if (!updateResp.isSuccessful) {
            error("updateConfig failed: HTTP ${updateResp.code()}")
        }

        if (activeProfileId() == profileId) {
            currentConfig = withAgeRating
            dataStore.markSynced(System.currentTimeMillis())
        }

        withAgeRating
    }.onFailure { Log.w(TAG, "reapplyKidsOverlay failed", it) }

    override suspend fun setEnabled(enabled: Boolean, manifestUrl: String): Result<Unit> = runCatching {
        val profile = profileManager.activeProfile
        if (profile?.usesPrimaryAddons == true) {
            error("Active profile uses primary addons; switch profiles to manage AIOMetadata.")
        }

        dataStore.setEnabled(enabled)
        upsertLinkEnabled(enabled)

        if (manifestUrl.isNotBlank()) {
            if (enabled) addonRepository.addAddon(manifestUrl)
            else addonRepository.removeAddon(manifestUrl)
        }
    }.onFailure { Log.w(TAG, "setEnabled failed", it) }

    // --- Password -----------------------------------------------------------

    private suspend fun getOrCreatePassword(): String {
        fetchLink()?.configPassword?.takeIf { it.isNotBlank() }?.let { fromSupabase ->
            dataStore.setConfigPassword(fromSupabase)
            return fromSupabase
        }
        dataStore.getConfigPassword()?.takeIf { it.isNotBlank() }?.let { return it }
        val fresh = generatePassword()
        dataStore.setConfigPassword(fresh)
        return fresh
    }

    private fun generatePassword(): String {
        val bytes = ByteArray(32)
        SecureRandom().nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    // --- Supabase helpers ----------------------------------------------------

    @Serializable
    private data class LinkRow(
        @SerialName("user_id") val userId: String,
        @SerialName("profile_id") val profileId: Int = 1,
        @SerialName("aio_uuid") val aioUuid: String,
        val enabled: Boolean,
        @SerialName("manifest_url") val manifestUrl: String? = null,
        @SerialName("config_password") val configPassword: String? = null,
    )

    private fun activeProfileId(): Int = profileManager.activeProfileId.value

    private suspend fun fetchLink(profileId: Int = activeProfileId()): LinkRow? {
        val userId = authManager.getEffectiveUserId() ?: return null
        return try {
            postgrest.from(TABLE)
                .select {
                    filter {
                        eq("user_id", userId)
                        eq("profile_id", profileId)
                    }
                    limit(1)
                }
                .decodeList<LinkRow>()
                .firstOrNull()
        } catch (e: Exception) {
            Log.w(TAG, "fetchLink failed", e)
            null
        }
    }

    private suspend fun upsertLink(
        aioUuid: String,
        manifestUrl: String,
        enabled: Boolean,
        configPassword: String?,
        profileId: Int = activeProfileId(),
    ) {
        val userId = authManager.getEffectiveUserId() ?: error("Not authenticated")
        postgrest.from(TABLE).upsert(
            LinkRow(
                userId = userId,
                profileId = profileId,
                aioUuid = aioUuid,
                enabled = enabled,
                manifestUrl = manifestUrl,
                configPassword = configPassword,
            )
        )
    }

    private suspend fun upsertLinkEnabled(enabled: Boolean) {
        val userId = authManager.getEffectiveUserId() ?: error("Not authenticated")
        val existing = fetchLink() ?: error("No AIOMetadata link row to update")
        postgrest.from(TABLE).upsert(
            LinkRow(
                userId = userId,
                profileId = existing.profileId,
                aioUuid = existing.aioUuid,
                enabled = enabled,
                manifestUrl = existing.manifestUrl,
                configPassword = existing.configPassword,
            )
        )
    }

    private fun buildFallbackManifestUrl(uuid: String): String {
        val base = BuildConfig.AIOMETADATA_BASE_URL.trimEnd('/')
        if (base.isBlank()) return ""
        return "$base/api/manifest/$uuid/manifest.json"
    }
}

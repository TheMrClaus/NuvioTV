package com.omnio.tv.data.repository

import android.util.Log
import com.omnio.tv.data.BuildConfig
import com.omnio.tv.data.local.AddonPreferences
import com.omnio.tv.data.local.AioStreamsSettingsDataStore
import com.omnio.tv.data.remote.api.AioStreamsApi
import com.omnio.tv.data.remote.dto.aiostreams.AioStreamsCreateRequestDto
import com.omnio.tv.data.remote.dto.aiostreams.AioStreamsDefaultConfig
import com.omnio.tv.data.remote.dto.aiostreams.AioStreamsDeleteRequestDto
import com.omnio.tv.data.remote.dto.aiostreams.AioStreamsEnvelopeDto
import com.omnio.tv.data.remote.dto.aiostreams.AioStreamsUpdateRequestDto
import com.omnio.tv.domain.auth.AuthManager
import com.omnio.tv.domain.model.AgeRatingTier
import com.omnio.tv.domain.model.AioSharingMode
import com.omnio.tv.domain.model.AioStreamsConfigInnerDto
import com.omnio.tv.domain.model.AioStreamsSettings
import com.omnio.tv.domain.profile.ProfileManager
import com.omnio.tv.domain.repository.AddonRepository
import com.omnio.tv.domain.repository.AioStreamsRepository
import io.github.jan.supabase.postgrest.Postgrest
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.security.SecureRandom
import java.util.Base64
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "AioStreamsRepository"
private const val TABLE = "aio_streams_links"

@Singleton
class AioStreamsRepositoryImpl @Inject constructor(
    private val api: AioStreamsApi,
    private val postgrest: Postgrest,
    private val dataStore: AioStreamsSettingsDataStore,
    private val authManager: AuthManager,
    private val profileManager: ProfileManager,
    private val addonRepository: AddonRepository,
    private val addonPreferences: AddonPreferences,
) : AioStreamsRepository {

    override val settings: Flow<AioStreamsSettings> = dataStore.settings

    @Volatile
    private var currentConfig: AioStreamsConfigInnerDto? = null

    override fun cachedConfig(): AioStreamsConfigInnerDto? = currentConfig

    override suspend fun refresh(): Result<AioStreamsConfigInnerDto?> = runCatching {
        val link = fetchLink() ?: run {
            currentConfig = null
            return@runCatching null
        }

        val password = link.configPassword?.takeIf { it.isNotBlank() }
            ?: dataStore.getConfigPassword()?.takeIf { it.isNotBlank() }
        if (password == null) {
            Log.w(TAG, "link row missing config_password for uuid=${link.aioUuid}; cannot load upstream config")
            currentConfig = null
            return@runCatching null
        }
        if (link.configPassword != null) dataStore.setConfigPassword(password)

        val response = api.getUser(link.aioUuid, password, raw = true)
        val body = response.requireEnvelopeBody("getUser")
        val payload = body.data ?: error("getUser empty data")
        val inner = payload.userData?.sanitizedForStorage() ?: error("getUser missing userData")
        currentConfig = inner

        val manifestUrl = payload.encryptedPassword
            ?.takeIf { it.isNotBlank() }
            ?.let { buildManifestUrl(link.aioUuid, it) }
            ?: link.manifestUrl.orEmpty()

        if (payload.encryptedPassword != null && manifestUrl.isNotBlank() && manifestUrl != link.manifestUrl) {
            upsertLink(
                aioUuid = link.aioUuid,
                manifestUrl = manifestUrl,
                enabled = link.enabled,
                configPassword = password,
            )
        }

        dataStore.replaceAll(
            AioStreamsSettings(
                enabled = link.enabled,
                aioUuid = link.aioUuid,
                manifestUrl = manifestUrl,
                lastSyncedAt = System.currentTimeMillis(),
            )
        )
        inner
    }.onFailure { Log.w(TAG, "refresh failed", it) }

    override suspend fun createConfig(config: AioStreamsConfigInnerDto): Result<AioStreamsRepository.CreateConfigResult> = runCatching {
        val password = getOrCreatePassword()
        val sentConfig = config.sanitizedForCreate()
        val response = api.createUser(
            AioStreamsCreateRequestDto(
                config = sentConfig,
                password = password,
            )
        )
        val body = response.requireEnvelopeBody("createUser")
        val payload = body.data ?: error("createUser empty data")
        currentConfig = sentConfig

        val manifestUrl = payload.encryptedPassword
            ?.takeIf { it.isNotBlank() }
            ?.let { buildManifestUrl(payload.uuid, it) }
            ?: buildFallbackManifestUrl(payload.uuid)
        upsertLink(
            aioUuid = payload.uuid,
            manifestUrl = manifestUrl,
            enabled = false,
            configPassword = password,
        )
        dataStore.replaceAll(
            AioStreamsSettings(
                enabled = false,
                aioUuid = payload.uuid,
                manifestUrl = manifestUrl,
                lastSyncedAt = System.currentTimeMillis(),
            )
        )
        AioStreamsRepository.CreateConfigResult(uuid = payload.uuid, manifestUrl = manifestUrl)
    }.onFailure { Log.w(TAG, "createConfig failed", it) }

    override suspend fun updateConfig(
        uuid: String,
        config: AioStreamsConfigInnerDto,
    ): Result<AioStreamsConfigInnerDto> = runCatching {
        val password = dataStore.getConfigPassword()?.takeIf { it.isNotBlank() }
            ?: fetchLink()?.configPassword?.takeIf { it.isNotBlank() }
            ?: error("No AIOStreams password stored; re-create the config.")

        val sanitized = config.sanitizedForStorage()
        val response = api.updateUser(
            AioStreamsUpdateRequestDto(
                uuid = uuid,
                password = password,
                config = sanitized,
            )
        )
        val body = response.requireEnvelopeBody("updateUser")
        val payload = body.data ?: error("updateUser empty data")
        val updated = payload.userData?.sanitizedForStorage() ?: sanitized
        currentConfig = updated
        dataStore.markSynced(System.currentTimeMillis())

        if (activeProfileId() == 1) {
            runCatching { propagateMainConfigToSiblings(updated) }
                .onFailure { Log.w(TAG, "propagateMainConfigToSiblings failed", it) }
        }
        updated
    }.onFailure { Log.w(TAG, "updateConfig failed", it) }

    override suspend fun deleteConfig(uuid: String): Result<Unit> = runCatching {
        val link = fetchLink() ?: return@runCatching Unit
        val password = dataStore.getConfigPassword()?.takeIf { it.isNotBlank() }
            ?: link.configPassword?.takeIf { it.isNotBlank() }
            ?: error("No AIOStreams password stored; cannot delete config.")
        val manifestUrl = link.manifestUrl.orEmpty()

        val response = api.deleteUser(
            AioStreamsDeleteRequestDto(
                uuid = uuid,
                password = password,
            )
        )
        response.requireEnvelopeBody("deleteUser")

        deleteLink(link.profileId)
        currentConfig = null
        dataStore.clear()

        if (manifestUrl.isNotBlank()) {
            val profile = profileManager.activeProfile
            if (profile?.usesPrimaryAddons == true) {
                addonPreferences.removeAddonFromProfile(link.profileId, manifestUrl)
            } else {
                addonRepository.removeAddon(manifestUrl)
            }
        }
    }.onFailure { Log.w(TAG, "deleteConfig failed", it) }

    override suspend fun setEnabled(enabled: Boolean, manifestUrl: String): Result<Unit> = runCatching {
        val profile = profileManager.activeProfile
        if (profile?.usesPrimaryAddons == true) {
            error("Active profile uses primary addons; switch profiles to manage AIOStreams.")
        }

        dataStore.setEnabled(enabled)
        upsertLinkEnabled(enabled)

        if (manifestUrl.isNotBlank()) {
            if (enabled) addonRepository.addAddon(manifestUrl)
            else addonRepository.removeAddon(manifestUrl)
        }
    }.onFailure { Log.w(TAG, "setEnabled failed", it) }

    override suspend fun getConfigPassword(): String? = dataStore.getConfigPassword()

    override suspend fun provisionFromMain(
        targetProfileId: Int,
        kidsMaxAgeRating: AgeRatingTier?,
    ): Result<AioStreamsRepository.CreateConfigResult> = runCatching {
        if (targetProfileId == 1) error("Cannot provision a per-profile AIOStreams config for the primary profile")

        val mainLink = fetchLink(profileId = 1)
            ?: error("Main profile has no AIOStreams config to copy from")
        val mainPassword = mainLink.configPassword?.takeIf { it.isNotBlank() }
            ?: error("Main profile AIOStreams link missing password — open Main's AIOStreams settings once to back-fill")

        val mainLoad = api.getUser(mainLink.aioUuid, mainPassword, raw = true)
        val mainBody = mainLoad.requireEnvelopeBody("getUser(main)")
        val mainConfig = mainBody.data?.userData?.sanitizedForStorage()
            ?: error("getUser(main) missing userData")

        // TODO(phase 2): apply Kids-specific stream filtering once AIOStreams'
        // filter shape is modeled. For now Kids gets a verbatim copy of Main.
        val initialConfig = when (kidsMaxAgeRating) {
            null -> mainConfig
            else -> mainConfig
        }

        val targetPassword = generatePassword()
        val saveResponse = api.createUser(
            AioStreamsCreateRequestDto(
                config = initialConfig.sanitizedForCreate(),
                password = targetPassword,
            )
        )
        val saveBody = saveResponse.requireEnvelopeBody("createUser")
        val savePayload = saveBody.data ?: error("createUser empty data")
        val manifestUrl = savePayload.encryptedPassword
            ?.takeIf { it.isNotBlank() }
            ?.let { buildManifestUrl(savePayload.uuid, it) }
            ?: buildFallbackManifestUrl(savePayload.uuid)

        upsertLink(
            aioUuid = savePayload.uuid,
            manifestUrl = manifestUrl,
            enabled = true,
            configPassword = targetPassword,
            profileId = targetProfileId,
        )

        dataStore.seedForProfile(
            profileId = targetProfileId,
            settings = AioStreamsSettings(
                enabled = true,
                aioUuid = savePayload.uuid,
                manifestUrl = manifestUrl,
                lastSyncedAt = System.currentTimeMillis(),
            ),
            configPassword = targetPassword,
        )

        val mainManifestUrl = mainLink.manifestUrl?.takeIf { it.isNotBlank() }
        if (mainManifestUrl != null) {
            addonPreferences.removeAddonFromProfile(targetProfileId, mainManifestUrl)
        }
        if (manifestUrl.isNotBlank()) {
            addonPreferences.addAddonToProfile(targetProfileId, manifestUrl)
        }

        AioStreamsRepository.CreateConfigResult(uuid = savePayload.uuid, manifestUrl = manifestUrl)
    }.onFailure { Log.w(TAG, "provisionFromMain failed", it) }

    private suspend fun propagateMainConfigToSiblings(mainConfig: AioStreamsConfigInnerDto) {
        profileManager.profiles.value
            .filter { it.id != 1 && it.aioSharing != AioSharingMode.INDEPENDENT }
            .forEach { sibling ->
                val link = fetchLink(profileId = sibling.id) ?: return@forEach
                val siblingPassword = link.configPassword?.takeIf { it.isNotBlank() } ?: return@forEach

                // Phase 1 keeps AIOStreams opaque. Until we model service creds /
                // filters separately, FULL_MIRROR and KEYS_ONLY both fan out the
                // full config to keep sibling profiles working.
                val targetConfig = when (sibling.aioSharing) {
                    AioSharingMode.FULL_MIRROR,
                    AioSharingMode.KEYS_ONLY -> mainConfig.sanitizedForCreate()
                    AioSharingMode.INDEPENDENT -> return@forEach
                }

                val updateResponse = api.updateUser(
                    AioStreamsUpdateRequestDto(
                        uuid = link.aioUuid,
                        password = siblingPassword,
                        config = targetConfig,
                    )
                )
                if (!updateResponse.isSuccessful) {
                    Log.w(TAG, "fan-out updateUser failed for profile ${sibling.id}: HTTP ${updateResponse.code()}")
                }
            }
    }

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
        val existing = fetchLink() ?: error("No AIOStreams link row to update")
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

    private suspend fun deleteLink(profileId: Int) {
        val userId = authManager.getEffectiveUserId() ?: error("Not authenticated")
        val existing = fetchLink(profileId) ?: return
        postgrest.from(TABLE).delete {
            filter {
                eq("user_id", userId)
                eq("profile_id", existing.profileId)
                eq("aio_uuid", existing.aioUuid)
            }
        }
    }

    private fun buildFallbackManifestUrl(uuid: String): String {
        // Current upstream manifest URLs require the server-returned encrypted
        // password segment. If that token is unavailable, keep the URL blank
        // rather than minting the stale unauthenticated path.
        return ""
    }

    private fun buildManifestUrl(uuid: String, encryptedPassword: String): String {
        val base = BuildConfig.AIOSTREAMS_BASE_URL.trimEnd('/')
        if (base.isBlank()) return ""
        return "$base/stremio/$uuid/$encryptedPassword/manifest.json"
    }

    private fun <T> retrofit2.Response<AioStreamsEnvelopeDto<T>>.requireEnvelopeBody(operation: String): AioStreamsEnvelopeDto<T> {
        if (!isSuccessful) {
            error("$operation failed: HTTP ${code()}")
        }
        val envelope = body() ?: error("$operation empty body")
        if (!envelope.success) {
            error(envelope.error?.message ?: envelope.detail ?: "$operation failed")
        }
        return envelope
    }

    private fun AioStreamsConfigInnerDto.sanitizedForStorage(): AioStreamsConfigInnerDto = copy(
        config = config.toMutableMap().apply {
            remove("uuid")
            remove("encryptedPassword")
            remove("ip")
            remove("trusted")
        }
    )

    private fun AioStreamsConfigInnerDto.sanitizedForCreate(): AioStreamsConfigInnerDto =
        sanitizedForStorage().takeIf { it.config.isNotEmpty() } ?: AioStreamsDefaultConfig.build()
}

package com.omnio.tv.data.repository

import android.util.Log
import com.omnio.tv.core.network.safeApiCall
import com.omnio.tv.data.BuildConfig
import com.omnio.tv.data.local.SourceCloudSettingsDataStore
import com.omnio.tv.data.remote.api.SourceCloudApi
import com.omnio.tv.data.remote.dto.sourcecloud.SourceCloudConnectRequestDto
import com.omnio.tv.data.remote.dto.sourcecloud.SourceCloudDisconnectRequestDto
import com.omnio.tv.data.remote.dto.sourcecloud.SourceCloudProfileScopedRequestDto
import com.omnio.tv.data.remote.dto.sourcecloud.SourceCloudProvisionProfileRequestDto
import com.omnio.tv.data.remote.dto.sourcecloud.toDomain
import com.omnio.tv.data.remote.dto.sourcecloud.toDto
import com.omnio.tv.domain.model.AddonStreams
import com.omnio.tv.domain.model.SourceCloudAdvancedConfigSession
import com.omnio.tv.domain.model.SourceCloudConfigState
import com.omnio.tv.domain.model.SourceCloudSearchRequest
import com.omnio.tv.domain.model.SourceCloudService
import com.omnio.tv.domain.model.SourceCloudServiceStatus
import com.omnio.tv.domain.model.SourceCloudSettings
import com.omnio.tv.domain.model.SourceCloudStatus
import com.omnio.tv.domain.profile.ProfileManager
import com.omnio.tv.domain.repository.SourceCloudRepository
import com.omnio.tv.domain.result.NetworkResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "SourceCloudRepository"

@Singleton
class SourceCloudRepositoryImpl private constructor(
    private val api: SourceCloudApi,
    private val dataStore: SourceCloudSettingsDataStore,
    private val profileManager: ProfileManager,
    private val baseUrlConfigured: Boolean
) : SourceCloudRepository {
    @Inject
    constructor(
        api: SourceCloudApi,
        dataStore: SourceCloudSettingsDataStore,
        profileManager: ProfileManager
    ) : this(
        api = api,
        dataStore = dataStore,
        profileManager = profileManager,
        baseUrlConfigured = BuildConfig.SOURCE_CLOUD_BASE_URL.isNotBlank()
    )

    internal constructor(
        api: SourceCloudApi,
        dataStore: SourceCloudSettingsDataStore,
        profileManager: ProfileManager,
        baseUrlConfiguredForTests: Boolean,
        testOnly: Unit = Unit
    ) : this(api, dataStore, profileManager, baseUrlConfiguredForTests)

    private fun currentProfileId(): Int = profileManager.activeProfileId.value

    override val settings: Flow<SourceCloudSettings> = dataStore.settings

    override suspend fun status(): SourceCloudStatus {
        val local = settings.first()
        if (!baseUrlConfigured) {
            return local.toOfflineStatus(baseUrlConfigured = false)
        }

        return when (val result = safeApiCall { api.status(currentProfileId()) }) {
            is NetworkResult.Success -> {
                val domain = result.data.toDomain(
                    enabled = local.enabled,
                    baseUrlConfigured = true
                )
                syncConnectedServicesFromStatus(domain)
                domain
            }
            is NetworkResult.Error -> {
                Log.w(TAG, "status failed: ${result.message}")
                local.toOfflineStatus(baseUrlConfigured = true)
            }
            NetworkResult.Loading -> local.toOfflineStatus(baseUrlConfigured = true)
        }
    }

    private suspend fun syncConnectedServicesFromStatus(status: SourceCloudStatus) {
        val connected = status.services
            .filter { it.connected }
            .map { it.service }
            .toSet()
        dataStore.setConnectedServices(connected)
    }

    override suspend fun setEnabled(enabled: Boolean) {
        dataStore.setEnabled(enabled)
    }

    override suspend fun setServiceConnected(service: SourceCloudService, connected: Boolean) {
        dataStore.setServiceConnected(service, connected)
    }

    override suspend fun search(request: SourceCloudSearchRequest): NetworkResult<AddonStreams?> {
        val local = settings.first()
        // Don't gate on `hasConnectedService` (local cache): users who configured services
        // through the phone/QR or API-key flow may not have written the local set, and the
        // backend may also resolve sources via hosted AIOStreams configurations even when no
        // debrid service is bound to this profile yet. Let the server decide and return empty
        // when there is nothing to serve.
        if (!baseUrlConfigured || !local.enabled) {
            return NetworkResult.Success(null)
        }

        return when (val result = safeApiCall { api.search(request.toDto(currentProfileId())) }) {
            is NetworkResult.Success -> NetworkResult.Success(result.data.toDomain())
            is NetworkResult.Error -> {
                Log.w(TAG, "search failed: ${result.message}")
                NetworkResult.Success(null)
            }
            NetworkResult.Loading -> NetworkResult.Loading
        }
    }

    override suspend fun requestAdvancedConfigSession(): NetworkResult<SourceCloudAdvancedConfigSession?> {
        if (!baseUrlConfigured) return NetworkResult.Success(null)

        val body = SourceCloudProfileScopedRequestDto(profileId = currentProfileId())
        return when (val result = safeApiCall { api.createAdvancedConfigSession(body) }) {
            is NetworkResult.Success -> NetworkResult.Success(result.data.toDomain())
            is NetworkResult.Error -> {
                Log.w(TAG, "advanced config session failed: ${result.message}")
                NetworkResult.Success(null)
            }
            NetworkResult.Loading -> NetworkResult.Loading
        }
    }

    override suspend fun disconnectService(service: SourceCloudService): NetworkResult<SourceCloudStatus> {
        if (!baseUrlConfigured) return NetworkResult.Error("Source Cloud backend is not configured.")

        val local = settings.first()
        val body = SourceCloudDisconnectRequestDto(
            profileId = currentProfileId(),
            service = service.key
        )

        return when (val result = safeApiCall { api.disconnectService(body) }) {
            is NetworkResult.Success -> {
                val domain = result.data.toDomain(enabled = local.enabled, baseUrlConfigured = true)
                syncConnectedServicesFromStatus(domain)
                NetworkResult.Success(domain)
            }
            is NetworkResult.Error -> {
                Log.w(TAG, "disconnect failed: ${result.message}")
                result
            }
            NetworkResult.Loading -> NetworkResult.Loading
        }
    }

    override suspend fun connectService(service: SourceCloudService, apiKey: String): NetworkResult<SourceCloudStatus> {
        if (!baseUrlConfigured) return NetworkResult.Error("Source Cloud backend is not configured.")

        val trimmedKey = apiKey.trim()
        if (trimmedKey.isBlank()) {
            return NetworkResult.Error("API key cannot be empty")
        }

        val local = settings.first()
        val body = SourceCloudConnectRequestDto(
            profileId = currentProfileId(),
            service = service.key,
            apiKey = trimmedKey
        )

        return when (val result = safeApiCall { api.connectService(body) }) {
            is NetworkResult.Success -> {
                val domain = result.data.toDomain(enabled = local.enabled, baseUrlConfigured = true)
                syncConnectedServicesFromStatus(domain)
                NetworkResult.Success(domain)
            }
            is NetworkResult.Error -> {
                Log.w(TAG, "connect failed: ${result.message}")
                result
            }
            NetworkResult.Loading -> NetworkResult.Loading
        }
    }

    override suspend fun provisionProfile(
        profileId: Int,
        isKids: Boolean,
        copyKeysFromMain: Boolean,
    ): NetworkResult<SourceCloudStatus> {
        if (!baseUrlConfigured) return NetworkResult.Error("Source Cloud backend is not configured.")

        val local = settings.first()
        val body = SourceCloudProvisionProfileRequestDto(
            profileId = profileId,
            kids = isKids,
            copyKeysFromMain = copyKeysFromMain,
        )

        return when (val result = safeApiCall { api.provisionProfile(body) }) {
            is NetworkResult.Success -> {
                val configDomain = result.data.config?.toDomain() ?: com.omnio.tv.domain.model.SourceCloudConfigState()
                val domain = SourceCloudStatus(
                    enabled = local.enabled,
                    baseUrlConfigured = true,
                    config = configDomain,
                    services = emptyList(),
                )
                NetworkResult.Success(domain)
            }
            is NetworkResult.Error -> {
                Log.w(TAG, "provisionProfile failed: ${result.message}")
                result
            }
            NetworkResult.Loading -> NetworkResult.Loading
        }
    }

    override suspend fun resetConfig(): SourceCloudStatus {
        val local = settings.first()
        if (!baseUrlConfigured) return local.toOfflineStatus(baseUrlConfigured = false)

        val body = SourceCloudProfileScopedRequestDto(profileId = currentProfileId())
        return when (val result = safeApiCall { api.resetConfig(body) }) {
            is NetworkResult.Success -> {
                val domain = result.data.toDomain(
                    enabled = local.enabled,
                    baseUrlConfigured = true
                )
                syncConnectedServicesFromStatus(domain)
                domain
            }
            is NetworkResult.Error -> {
                Log.w(TAG, "config reset failed: ${result.message}")
                local.toOfflineStatus(baseUrlConfigured = true)
            }
            NetworkResult.Loading -> local.toOfflineStatus(baseUrlConfigured = true)
        }
    }

    private fun SourceCloudSettings.toOfflineStatus(baseUrlConfigured: Boolean): SourceCloudStatus = SourceCloudStatus(
        enabled = enabled,
        baseUrlConfigured = baseUrlConfigured,
        config = SourceCloudConfigState(),
        services = SourceCloudService.entries.map { service ->
            SourceCloudServiceStatus(
                service = service,
                connected = service in connectedServices,
                label = service.displayName,
                message = null
            )
        }
    )
}

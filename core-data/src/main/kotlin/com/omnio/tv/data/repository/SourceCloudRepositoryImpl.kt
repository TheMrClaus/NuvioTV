package com.omnio.tv.data.repository

import android.util.Log
import com.omnio.tv.core.network.safeApiCall
import com.omnio.tv.data.BuildConfig
import com.omnio.tv.data.local.SourceCloudSettingsDataStore
import com.omnio.tv.data.remote.api.SourceCloudApi
import com.omnio.tv.data.remote.dto.sourcecloud.toDomain
import com.omnio.tv.data.remote.dto.sourcecloud.toDto
import com.omnio.tv.domain.model.AddonStreams
import com.omnio.tv.domain.model.SourceCloudSearchRequest
import com.omnio.tv.domain.model.SourceCloudService
import com.omnio.tv.domain.model.SourceCloudServiceStatus
import com.omnio.tv.domain.model.SourceCloudSettings
import com.omnio.tv.domain.model.SourceCloudStatus
import com.omnio.tv.domain.repository.SourceCloudRepository
import com.omnio.tv.domain.result.NetworkResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "SourceCloudRepository"

@Singleton
class SourceCloudRepositoryImpl @Inject constructor(
    private val api: SourceCloudApi,
    private val dataStore: SourceCloudSettingsDataStore
) : SourceCloudRepository {
    override val settings: Flow<SourceCloudSettings> = dataStore.settings

    private val baseUrlConfigured: Boolean
        get() = BuildConfig.SOURCE_CLOUD_BASE_URL.isNotBlank()

    override suspend fun status(): SourceCloudStatus {
        val local = settings.first()
        if (!baseUrlConfigured) {
            return local.toOfflineStatus(baseUrlConfigured = false)
        }

        return when (val result = safeApiCall { api.status() }) {
            is NetworkResult.Success -> result.data.toDomain(
                enabled = local.enabled,
                baseUrlConfigured = true
            )
            is NetworkResult.Error -> {
                Log.w(TAG, "status failed: ${result.message}")
                local.toOfflineStatus(baseUrlConfigured = true)
            }
            NetworkResult.Loading -> local.toOfflineStatus(baseUrlConfigured = true)
        }
    }

    override suspend fun setEnabled(enabled: Boolean) {
        dataStore.setEnabled(enabled)
    }

    override suspend fun setServiceConnected(service: SourceCloudService, connected: Boolean) {
        dataStore.setServiceConnected(service, connected)
    }

    override suspend fun search(request: SourceCloudSearchRequest): NetworkResult<AddonStreams?> {
        val local = settings.first()
        if (!baseUrlConfigured || !local.enabled || !local.hasConnectedService) {
            return NetworkResult.Success(null)
        }

        return when (val result = safeApiCall { api.search(request.toDto()) }) {
            is NetworkResult.Success -> NetworkResult.Success(result.data.toDomain())
            is NetworkResult.Error -> {
                Log.w(TAG, "search failed: ${result.message}")
                NetworkResult.Success(null)
            }
            NetworkResult.Loading -> NetworkResult.Loading
        }
    }

    private fun SourceCloudSettings.toOfflineStatus(baseUrlConfigured: Boolean): SourceCloudStatus = SourceCloudStatus(
        enabled = enabled,
        baseUrlConfigured = baseUrlConfigured,
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

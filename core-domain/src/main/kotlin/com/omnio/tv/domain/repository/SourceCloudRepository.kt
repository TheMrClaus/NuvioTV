package com.omnio.tv.domain.repository

import com.omnio.tv.domain.model.AddonStreams
import com.omnio.tv.domain.model.SourceCloudAdvancedConfigSession
import com.omnio.tv.domain.model.SourceCloudSearchRequest
import com.omnio.tv.domain.model.SourceCloudService
import com.omnio.tv.domain.model.SourceCloudSettings
import com.omnio.tv.domain.model.SourceCloudStatus
import com.omnio.tv.domain.result.NetworkResult
import kotlinx.coroutines.flow.Flow

interface SourceCloudRepository {
    val settings: Flow<SourceCloudSettings>

    suspend fun status(): SourceCloudStatus
    suspend fun setEnabled(enabled: Boolean)
    suspend fun setServiceConnected(service: SourceCloudService, connected: Boolean)
    suspend fun search(request: SourceCloudSearchRequest): NetworkResult<AddonStreams?>
    suspend fun requestAdvancedConfigSession(): NetworkResult<SourceCloudAdvancedConfigSession?>
    suspend fun disconnectService(service: SourceCloudService): NetworkResult<SourceCloudStatus>
    suspend fun connectService(service: SourceCloudService, apiKey: String): NetworkResult<SourceCloudStatus>
    suspend fun resetConfig(): SourceCloudStatus
}

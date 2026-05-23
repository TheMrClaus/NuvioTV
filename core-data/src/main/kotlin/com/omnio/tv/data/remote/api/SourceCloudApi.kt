package com.omnio.tv.data.remote.api

import com.omnio.tv.data.remote.dto.sourcecloud.SourceCloudProfileScopedRequestDto
import com.omnio.tv.data.remote.dto.sourcecloud.SourceCloudProvisionProfileRequestDto
import com.omnio.tv.data.remote.dto.sourcecloud.SourceCloudProvisionProfileResponseDto
import com.omnio.tv.data.remote.dto.sourcecloud.SourceCloudSearchRequestDto
import com.omnio.tv.data.remote.dto.sourcecloud.SourceCloudSearchResponseDto
import com.omnio.tv.data.remote.dto.sourcecloud.SourceCloudAdvancedConfigSessionResponseDto
import com.omnio.tv.data.remote.dto.sourcecloud.SourceCloudDisconnectRequestDto
import com.omnio.tv.data.remote.dto.sourcecloud.SourceCloudConnectRequestDto
import com.omnio.tv.data.remote.dto.sourcecloud.SourceCloudStatusResponseDto
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

interface SourceCloudApi {
    @GET("source-cloud-status")
    suspend fun status(@Query("profileId") profileId: Int): Response<SourceCloudStatusResponseDto>

    @POST("source-cloud-search")
    suspend fun search(@Body request: SourceCloudSearchRequestDto): Response<SourceCloudSearchResponseDto>

    @POST("source-cloud-advanced-session")
    suspend fun createAdvancedConfigSession(
        @Body request: SourceCloudProfileScopedRequestDto
    ): Response<SourceCloudAdvancedConfigSessionResponseDto>

    @POST("source-cloud-reset")
    suspend fun resetConfig(
        @Body request: SourceCloudProfileScopedRequestDto
    ): Response<SourceCloudStatusResponseDto>

    @POST("source-cloud-disconnect-service")
    suspend fun disconnectService(
        @Body request: SourceCloudDisconnectRequestDto
    ): Response<SourceCloudStatusResponseDto>

    @POST("source-cloud-connect-service")
    suspend fun connectService(
        @Body request: SourceCloudConnectRequestDto
    ): Response<SourceCloudStatusResponseDto>

    @POST("source-cloud-provision-profile")
    suspend fun provisionProfile(
        @Body request: SourceCloudProvisionProfileRequestDto
    ): Response<SourceCloudProvisionProfileResponseDto>
}

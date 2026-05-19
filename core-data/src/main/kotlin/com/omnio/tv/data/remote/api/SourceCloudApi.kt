package com.omnio.tv.data.remote.api

import com.omnio.tv.data.remote.dto.sourcecloud.SourceCloudProfileScopedRequestDto
import com.omnio.tv.data.remote.dto.sourcecloud.SourceCloudSearchRequestDto
import com.omnio.tv.data.remote.dto.sourcecloud.SourceCloudSearchResponseDto
import com.omnio.tv.data.remote.dto.sourcecloud.SourceCloudAdvancedConfigSessionResponseDto
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
}

package com.omnio.tv.data.remote.api

import com.omnio.tv.data.remote.dto.sourcecloud.SourceCloudSearchRequestDto
import com.omnio.tv.data.remote.dto.sourcecloud.SourceCloudSearchResponseDto
import com.omnio.tv.data.remote.dto.sourcecloud.SourceCloudAdvancedConfigSessionResponseDto
import com.omnio.tv.data.remote.dto.sourcecloud.SourceCloudStatusResponseDto
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

interface SourceCloudApi {
    @GET("v1/source-cloud/status")
    suspend fun status(): Response<SourceCloudStatusResponseDto>

    @POST("v1/source-cloud/search")
    suspend fun search(@Body request: SourceCloudSearchRequestDto): Response<SourceCloudSearchResponseDto>

    @POST("v1/source-cloud/config/advanced-session")
    suspend fun createAdvancedConfigSession(): Response<SourceCloudAdvancedConfigSessionResponseDto>

    @POST("v1/source-cloud/config/reset")
    suspend fun resetConfig(): Response<SourceCloudStatusResponseDto>
}

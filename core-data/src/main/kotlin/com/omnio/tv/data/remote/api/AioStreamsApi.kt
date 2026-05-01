package com.omnio.tv.data.remote.api

import com.omnio.tv.data.remote.dto.aiostreams.AioStreamsCreateRequestDto
import com.omnio.tv.data.remote.dto.aiostreams.AioStreamsCreateResponseDto
import com.omnio.tv.data.remote.dto.aiostreams.AioStreamsDeleteRequestDto
import com.omnio.tv.data.remote.dto.aiostreams.AioStreamsDeleteResponseDto
import com.omnio.tv.data.remote.dto.aiostreams.AioStreamsEnvelopeDto
import com.omnio.tv.data.remote.dto.aiostreams.AioStreamsGetResponseDto
import com.omnio.tv.data.remote.dto.aiostreams.AioStreamsUpdateRequestDto
import com.omnio.tv.data.remote.dto.aiostreams.AioStreamsUpdateResponseDto
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.HTTP
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Query

/**
 * REST client for the self-hosted Viren070/AIOStreams instance.
 * Base URL is configured in NetworkModule from BuildConfig.AIOSTREAMS_BASE_URL.
 */
interface AioStreamsApi {

    @POST("api/v1/user")
    suspend fun createUser(
        @Body body: AioStreamsCreateRequestDto,
    ): Response<AioStreamsEnvelopeDto<AioStreamsCreateResponseDto>>

    @GET("api/v1/user")
    suspend fun getUser(
        @Query("uuid") uuid: String,
        @Query("password") password: String,
        @Query("raw") raw: Boolean = true,
    ): Response<AioStreamsEnvelopeDto<AioStreamsGetResponseDto>>

    @PUT("api/v1/user")
    suspend fun updateUser(
        @Body body: AioStreamsUpdateRequestDto,
    ): Response<AioStreamsEnvelopeDto<AioStreamsUpdateResponseDto>>

    @HTTP(method = "DELETE", path = "api/v1/user", hasBody = true)
    suspend fun deleteUser(
        @Body body: AioStreamsDeleteRequestDto,
    ): Response<AioStreamsEnvelopeDto<AioStreamsDeleteResponseDto>>
}

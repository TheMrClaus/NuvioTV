package com.omnio.tv.data.remote.dto.aiostreams

import com.omnio.tv.domain.model.AioStreamsConfigInnerDto
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * Upstream (Viren070/AIOStreams) config payload shapes.
 *
 * AIOStreams gates every create/update/get/delete on a per-user password.
 * The inner [config] is opaque — we round-trip whatever the upstream form
 * persists without modelling every field in Kotlin.
 */

@JsonClass(generateAdapter = true)
data class AioStreamsCreateRequestDto(
    @Json(name = "config") val config: AioStreamsConfigInnerDto,
    @Json(name = "password") val password: String,
)

@JsonClass(generateAdapter = true)
data class AioStreamsUpdateRequestDto(
    @Json(name = "uuid") val uuid: String,
    @Json(name = "password") val password: String,
    @Json(name = "config") val config: AioStreamsConfigInnerDto,
)

@JsonClass(generateAdapter = true)
data class AioStreamsDeleteRequestDto(
    @Json(name = "uuid") val uuid: String,
    @Json(name = "password") val password: String,
)

data class AioStreamsEnvelopeDto<T>(
    @Json(name = "success") val success: Boolean = false,
    @Json(name = "detail") val detail: String? = null,
    @Json(name = "data") val data: T? = null,
    @Json(name = "error") val error: AioStreamsErrorDto? = null,
)

@JsonClass(generateAdapter = true)
data class AioStreamsErrorDto(
    @Json(name = "code") val code: String? = null,
    @Json(name = "message") val message: String? = null,
)

@JsonClass(generateAdapter = true)
data class AioStreamsCreateResponseDto(
    @Json(name = "uuid") val uuid: String,
    @Json(name = "encryptedPassword") val encryptedPassword: String? = null,
)

@JsonClass(generateAdapter = true)
data class AioStreamsGetResponseDto(
    @Json(name = "userData") val userData: AioStreamsConfigInnerDto? = null,
    @Json(name = "encryptedPassword") val encryptedPassword: String? = null,
)

@JsonClass(generateAdapter = true)
data class AioStreamsUpdateResponseDto(
    @Json(name = "uuid") val uuid: String? = null,
    @Json(name = "userData") val userData: AioStreamsConfigInnerDto? = null,
)

@JsonClass(generateAdapter = true)
data class AioStreamsDeleteResponseDto(
    @Json(name = "noop") val noop: String? = null,
)

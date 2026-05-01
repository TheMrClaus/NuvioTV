package com.omnio.tv.data.remote.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class UpdateManifestDto(
    @Json(name = "app_module") val appModule: String? = null,
    @Json(name = "version_name") val versionName: String? = null,
    @Json(name = "release_tag") val releaseTag: String? = null,
    @Json(name = "release_title") val releaseTitle: String? = null,
    @Json(name = "release_notes") val releaseNotes: String? = null,
    @Json(name = "release_url") val releaseUrl: String? = null,
    @Json(name = "published_at_utc") val publishedAtUtc: String? = null,
    val assets: List<UpdateManifestAssetDto> = emptyList()
)

@JsonClass(generateAdapter = true)
data class UpdateManifestAssetDto(
    val name: String,
    @Json(name = "download_url") val downloadUrl: String,
    @Json(name = "size_bytes") val sizeBytes: Long? = null,
    @Json(name = "content_type") val contentType: String? = null
)
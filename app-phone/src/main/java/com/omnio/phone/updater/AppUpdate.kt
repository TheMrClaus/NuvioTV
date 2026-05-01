package com.omnio.phone.updater

data class AppUpdate(
    val versionName: String,
    val tag: String,
    val title: String,
    val notes: String,
    val releaseUrl: String?,
    val assetName: String,
    val assetUrl: String,
    val assetSizeBytes: Long?
)

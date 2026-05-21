package com.omnio.tv.domain.sync

data class RemoteAddon(
    val url: String,
    val enabled: Boolean,
    val sortOrder: Int
)

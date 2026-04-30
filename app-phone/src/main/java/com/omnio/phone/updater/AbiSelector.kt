package com.omnio.phone.updater

import android.os.Build
import com.omnio.tv.data.remote.dto.GitHubAssetDto

internal object AbiSelector {

    private const val PHONE_ASSET_PREFIX = "app-phone-"

    private val knownAbis = listOf(
        "arm64-v8a",
        "armeabi-v7a",
        "x86_64",
        "x86"
    )

    fun phoneApkAssets(assets: List<GitHubAssetDto>): List<GitHubAssetDto> =
        assets.filter { asset ->
            asset.name.endsWith(".apk", ignoreCase = true) &&
                asset.name.startsWith(PHONE_ASSET_PREFIX, ignoreCase = true)
        }

    fun chooseBestApkAsset(assets: List<GitHubAssetDto>): GitHubAssetDto? {
        val apkAssets = phoneApkAssets(assets)
        if (apkAssets.isEmpty()) return null
        if (apkAssets.size == 1) return apkAssets.first()

        val supported = Build.SUPPORTED_ABIS?.toList().orEmpty()

        for (abi in supported) {
            val candidate = apkAssets.firstOrNull { it.name.contains(abi, ignoreCase = true) }
            if (candidate != null) return candidate
        }

        val universal = apkAssets.firstOrNull {
            val n = it.name.lowercase()
            n.contains("universal") || n.contains("all")
        }
        if (universal != null) return universal

        val noAbiMention = apkAssets.firstOrNull { asset ->
            knownAbis.none { abi -> asset.name.contains(abi, ignoreCase = true) }
        }
        return noAbiMention ?: apkAssets.first()
    }
}

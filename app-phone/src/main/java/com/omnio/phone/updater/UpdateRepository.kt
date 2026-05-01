package com.omnio.phone.updater

import com.omnio.phone.BuildConfig
import com.omnio.tv.data.remote.api.GitHubReleaseApi
import com.omnio.tv.data.remote.dto.GitHubAssetDto
import com.omnio.tv.data.remote.dto.GitHubReleaseDto
import com.omnio.tv.data.remote.dto.UpdateManifestAssetDto
import com.omnio.tv.data.remote.dto.UpdateManifestDto
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UpdateRepository @Inject constructor(
    private val gitHubReleaseApi: GitHubReleaseApi
) {

    private companion object {
        const val MAX_RELEASE_PAGES = 5
        val VERSION_NAME_REGEX = Regex("""\bv?\d+\.\d+\.\d+(?:-[0-9A-Za-z.]+)?""")
    }

    suspend fun getLatestPhoneUpdate(): Result<AppUpdate> {
        return runCatching {
            fetchManifestUpdate().getOrElse {
                fetchReleaseUpdate()
            }
        }
    }

    private suspend fun fetchManifestUpdate(): Result<AppUpdate> {
        return runCatching {
            val manifestUrl = BuildConfig.UPDATE_MANIFEST_URL.trim()
            if (manifestUrl.isBlank()) {
                error("UPDATE_MANIFEST_URL is blank")
            }

            val response = gitHubReleaseApi.getUpdateManifest(manifestUrl)
            if (!response.isSuccessful) {
                error("Update manifest error: ${response.code()}")
            }

            val manifest = response.body() ?: error("Update manifest body missing")
            manifest.toAppUpdate()
        }
    }

    private suspend fun fetchReleaseUpdate(): AppUpdate {
        val owner = BuildConfig.GITHUB_OWNER
        val repo = BuildConfig.GITHUB_REPO

        for (page in 1..MAX_RELEASE_PAGES) {
            val response = gitHubReleaseApi.getReleases(owner = owner, repo = repo, page = page)
            if (!response.isSuccessful) {
                error("GitHub API error: ${response.code()}")
            }

            val releases = response.body().orEmpty()
            if (releases.isEmpty()) break

            val phoneRelease = releases.firstOrNull(::isPhoneRelease) ?: continue
            return phoneRelease.toAppUpdate()
        }

        error("No phone release found in the latest ${MAX_RELEASE_PAGES * 30} entries")
    }

    private fun isPhoneRelease(release: GitHubReleaseDto): Boolean {
        return !release.draft && !release.prerelease &&
            AbiSelector.phoneApkAssets(release.assets).isNotEmpty()
    }

    private fun UpdateManifestDto.toAppUpdate(): AppUpdate {
        val manifestTag = releaseTag?.takeIf { it.isNotBlank() }
            ?: error("Update manifest is missing release_tag")
        val manifestVersionName = versionName?.takeIf { it.isNotBlank() }
            ?: extractVersionName(releaseTag, releaseTitle)
            ?: error("Update manifest is missing version_name")
        val manifestAssets = assets.map { asset -> asset.toGitHubAsset() }
        val asset = AbiSelector.chooseBestApkAsset(manifestAssets)
            ?: error("No phone APK asset found in manifest $manifestTag")

        return AppUpdate(
            versionName = manifestVersionName,
            tag = manifestTag,
            title = releaseTitle?.takeIf { it.isNotBlank() } ?: manifestTag,
            notes = releaseNotes.orEmpty(),
            releaseUrl = releaseUrl,
            assetName = asset.name,
            assetUrl = asset.browserDownloadUrl,
            assetSizeBytes = asset.size
        )
    }

    private fun GitHubReleaseDto.toAppUpdate(): AppUpdate {
        val tag = tagName?.takeIf { it.isNotBlank() }
            ?: name?.takeIf { it.isNotBlank() }
            ?: error("Release has no tag/name")
        val releaseVersionName = extractVersionName(tagName, name)
            ?: error("Release $tag has no parseable version")
        val asset = AbiSelector.chooseBestApkAsset(assets)
            ?: error("No phone APK asset found in release $tag")

        return AppUpdate(
            versionName = releaseVersionName,
            tag = tag,
            title = name?.takeIf { it.isNotBlank() } ?: tag,
            notes = body.orEmpty(),
            releaseUrl = htmlUrl,
            assetName = asset.name,
            assetUrl = asset.browserDownloadUrl,
            assetSizeBytes = asset.size
        )
    }

    private fun UpdateManifestAssetDto.toGitHubAsset(): GitHubAssetDto {
        return GitHubAssetDto(
            name = name,
            browserDownloadUrl = downloadUrl,
            size = sizeBytes,
            contentType = contentType
        )
    }

    private fun extractVersionName(vararg candidates: String?): String? {
        return candidates.asSequence()
            .filterNotNull()
            .map(String::trim)
            .filter(String::isNotBlank)
            .mapNotNull { raw ->
                VERSION_NAME_REGEX.find(raw)?.value?.removePrefix("v")?.removePrefix("V")
            }
            .firstOrNull()
    }
}

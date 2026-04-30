package com.omnio.phone.updater

import com.omnio.phone.BuildConfig
import com.omnio.tv.data.remote.api.GitHubReleaseApi
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UpdateRepository @Inject constructor(
    private val gitHubReleaseApi: GitHubReleaseApi
) {

    suspend fun getLatestPhoneUpdate(): Result<AppUpdate> {
        return runCatching {
            val owner = BuildConfig.GITHUB_OWNER
            val repo = BuildConfig.GITHUB_REPO

            val response = gitHubReleaseApi.getReleases(owner = owner, repo = repo)
            if (!response.isSuccessful) {
                error("GitHub API error: ${response.code()}")
            }

            val releases = response.body().orEmpty()
            // Phone and TV publish to the same repo. Filter to releases that ship a
            // phone APK asset so we never offer TV builds to phone users.
            val phoneRelease = releases
                .firstOrNull { release ->
                    !release.draft && !release.prerelease &&
                        AbiSelector.phoneApkAssets(release.assets).isNotEmpty()
                }
                ?: error("No phone release found in the latest 30 entries")

            val tag = phoneRelease.tagName?.takeIf { it.isNotBlank() }
                ?: phoneRelease.name?.takeIf { it.isNotBlank() }
                ?: error("Release has no tag/name")

            val asset = AbiSelector.chooseBestApkAsset(phoneRelease.assets)
                ?: error("No phone APK asset found in release $tag")

            AppUpdate(
                tag = tag,
                title = phoneRelease.name?.takeIf { it.isNotBlank() } ?: tag,
                notes = phoneRelease.body.orEmpty(),
                releaseUrl = phoneRelease.htmlUrl,
                assetName = asset.name,
                assetUrl = asset.browserDownloadUrl,
                assetSizeBytes = asset.size
            )
        }
    }
}

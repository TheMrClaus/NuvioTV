package com.omnio.tv.updater

import com.omnio.tv.BuildConfig
import com.omnio.tv.data.remote.api.GitHubReleaseApi
import com.omnio.tv.updater.model.AppUpdate
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UpdateRepository @Inject constructor(
    private val gitHubReleaseApi: GitHubReleaseApi
) {

    suspend fun getLatestUpdate(): Result<AppUpdate> {
        return runCatching {
            val owner = BuildConfig.GITHUB_OWNER
            val repo = BuildConfig.GITHUB_REPO

            val response = gitHubReleaseApi.getReleases(owner = owner, repo = repo)
            if (!response.isSuccessful) {
                error("GitHub API error: ${response.code()}")
            }

            val releases = response.body().orEmpty()
            // TV and phone publish to the same repo. Filter to releases that ship a
            // TV APK asset so phone-only releases don't mask the latest TV update.
            val tvRelease = releases
                .firstOrNull { release ->
                    !release.draft && !release.prerelease &&
                        AbiSelector.tvApkAssets(release.assets).isNotEmpty()
                }
                ?: error("No TV release found in the latest 30 entries")

            val tag = tvRelease.tagName?.takeIf { it.isNotBlank() }
                ?: tvRelease.name?.takeIf { it.isNotBlank() }
                ?: error("Release has no tag/name")

            val asset = AbiSelector.chooseBestApkAsset(tvRelease.assets)
                ?: error("No TV APK asset found in release $tag")

            AppUpdate(
                tag = tag,
                title = tvRelease.name?.takeIf { it.isNotBlank() } ?: tag,
                notes = tvRelease.body.orEmpty(),
                releaseUrl = tvRelease.htmlUrl,
                assetName = asset.name,
                assetUrl = asset.browserDownloadUrl,
                assetSizeBytes = asset.size
            )
        }
    }
}

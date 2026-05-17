package com.omnio.tv.data.repository

import android.content.Context
import android.util.Log
import com.omnio.tv.data.remote.api.AddonApi
import com.omnio.tv.domain.model.AddonStreams
import com.omnio.tv.domain.model.SourceCloudSearchRequest
import com.omnio.tv.domain.model.SourceCloudService
import com.omnio.tv.domain.model.SourceCloudSettings
import com.omnio.tv.domain.model.SourceCloudStatus
import com.omnio.tv.domain.model.Stream
import com.omnio.tv.domain.plugin.PluginManager
import com.omnio.tv.domain.repository.AddonRepository
import com.omnio.tv.domain.repository.SourceCloudRepository
import com.omnio.tv.domain.result.NetworkResult
import com.omnio.tv.domain.tmdb.TmdbService
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class StreamRepositoryImplTest {

    @Test
    fun `emits source cloud streams when connected service is enabled`() = runTest {
        mockAndroidLog()
        val sourceCloudRepository = FakeSourceCloudRepository(
            settingsValue = SourceCloudSettings(
                enabled = true,
                connectedServices = setOf(SourceCloudService.REAL_DEBRID)
            ),
            searchResult = NetworkResult.Success(
                AddonStreams(
                    addonName = "Omnio Source Cloud",
                    addonLogo = null,
                    streams = listOf(sourceCloudStream())
                )
            )
        )
        val repository = StreamRepositoryImpl(
            context = mockk(relaxed = true),
            api = mockk(),
            addonRepository = fakeAddonRepository(),
            pluginManager = fakePluginManager(),
            tmdbService = fakeTmdbService(),
            embyMediaService = fakeEmbyMediaService(),
            sourceCloudRepository = sourceCloudRepository
        )

        val emissions = repository.getStreamsFromAllAddons(
            type = "movie",
            videoId = "tt1234567",
            season = null,
            episode = null
        ).toList()

        val success = emissions.filterIsInstance<NetworkResult.Success<List<AddonStreams>>>().last()
        assertEquals(listOf("Omnio Source Cloud"), success.data.map { it.addonName })
        assertEquals("https://stream.example/movie.mkv", success.data.single().streams.single().url)
        assertEquals(
            SourceCloudSearchRequest(
                type = "movie",
                videoId = "tt1234567",
                tmdbId = "12345",
                season = null,
                episode = null
            ),
            sourceCloudRepository.lastSearchRequest
        )
    }

    private fun sourceCloudStream(): Stream = Stream(
        name = "Cached 4K",
        title = "Cached 4K",
        description = null,
        url = "https://stream.example/movie.mkv",
        ytId = null,
        infoHash = null,
        fileIdx = null,
        externalUrl = null,
        behaviorHints = null,
        addonName = "Omnio Source Cloud",
        addonLogo = null,
        sourceProvider = "source_cloud"
    )

    private fun fakeAddonRepository(): AddonRepository = object : AddonRepository {
        override fun getInstalledAddons() = flowOf(emptyList<com.omnio.tv.domain.model.Addon>())
        override suspend fun fetchAddon(baseUrl: String) = error("fetchAddon should not be called")
        override suspend fun addAddon(url: String) = Unit
        override suspend fun removeAddon(url: String) = Unit
        override suspend fun setAddonOrder(urls: List<String>) = Unit
    }

    private fun fakePluginManager(): PluginManager = mockk(relaxed = true) {
        every { pluginsEnabled } returns flowOf(false)
        every { executeScrapersStreaming(any(), any(), any(), any()) } returns emptyFlow()
    }

    private fun fakeTmdbService(): TmdbService = mockk {
        every { apiKey() } returns ""
        coEveryEnsureTmdbId()
    }

    private fun TmdbService.coEveryEnsureTmdbId() {
        io.mockk.coEvery { ensureTmdbId("tt1234567", "movie") } returns "12345"
    }

    private fun fakeEmbyMediaService(): EmbyMediaService = mockk {
        every { isConfigured() } returns false
        io.mockk.coEvery { findEmbyStream(any(), any(), any(), any()) } returns null
    }

    private fun mockAndroidLog() {
        mockkStatic(Log::class)
        every { Log.d(any<String>(), any<String>()) } returns 0
        every { Log.w(any<String>(), any<String>()) } returns 0
        every { Log.e(any<String>(), any<String>()) } returns 0
        every { Log.e(any<String>(), any<String>(), any<Throwable>()) } returns 0
    }

    private class FakeSourceCloudRepository(
        settingsValue: SourceCloudSettings,
        private val searchResult: NetworkResult<AddonStreams?>
    ) : SourceCloudRepository {
        override val settings: Flow<SourceCloudSettings> = flowOf(settingsValue)
        var lastSearchRequest: SourceCloudSearchRequest? = null

        override suspend fun status(): SourceCloudStatus = error("status should not be called")
        override suspend fun setEnabled(enabled: Boolean) = Unit
        override suspend fun setServiceConnected(service: SourceCloudService, connected: Boolean) = Unit

        override suspend fun search(request: SourceCloudSearchRequest): NetworkResult<AddonStreams?> {
            lastSearchRequest = request
            return searchResult
        }
    }
}

package com.nuvio.tv.data.repository

import android.util.Log
import com.nuvio.tv.data.local.EmbyAuthDataStore
import com.nuvio.tv.data.local.EmbyAuthState
import com.nuvio.tv.data.remote.api.EmbyApi
import com.nuvio.tv.data.remote.dto.emby.EmbyItemDto
import com.nuvio.tv.data.remote.dto.emby.EmbyItemsResponseDto
import com.nuvio.tv.data.remote.dto.emby.EmbyMediaSourceDto
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import retrofit2.Response

class EmbyMediaServiceTest {

    private val embyApi = mockk<EmbyApi>()
    private val authDataStore = mockk<EmbyAuthDataStore>()

    @Test
    fun `findEmbyStream resolves requested episode and supports item URL fallback lookup`() = runTest {
        mockkStatic(Log::class)
        every { Log.d(any<String>(), any<String>()) } returns 0
        every { Log.w(any<String>(), any<String>()) } returns 0
        every { Log.e(any<String>(), any<String>(), any<Throwable>()) } returns 0

        every { authDataStore.state } returns flowOf(
            EmbyAuthState(
                serverUrl = "https://emby.example",
                apiKey = "secret",
                userId = "user-1",
                deviceId = "device-1"
            )
        )

        coEvery {
            embyApi.getUserItems(
                userId = "user-1",
                includeItemTypes = "Series",
                providerIds = "imdb.tt1234567",
                fields = any(),
                recursive = any(),
                limit = any()
            )
        } returns Response.success(
            EmbyItemsResponseDto(
                items = listOf(
                    EmbyItemDto(
                        id = "series-1",
                        name = "Example Show",
                        type = "Series"
                    )
                )
            )
        )

        coEvery {
            embyApi.getEpisodes(
                seriesId = "series-1",
                season = 1,
                includeItemTypes = any(),
                fields = any()
            )
        } returns Response.success(
            EmbyItemsResponseDto(
                items = listOf(
                    EmbyItemDto(
                        id = "episode-1",
                        name = "Wrong Episode",
                        indexNumber = 1,
                        mediaSources = listOf(EmbyMediaSourceDto(id = "media-1"))
                    ),
                    EmbyItemDto(
                        id = "episode-2",
                        name = "Right Episode",
                        indexNumber = 2,
                        mediaSources = listOf(EmbyMediaSourceDto(id = "media-2")),
                        runTimeTicks = 420_000_000L
                    )
                )
            )
        )

        val service = EmbyMediaService(embyApi, authDataStore)

        val resolved = service.findEmbyStream(
            contentId = "tt1234567",
            contentType = "series",
            season = 1,
            episode = 2
        )

        assertEquals(
            "https://emby.example/Videos/episode-2/stream?api_key=secret&static=true",
            resolved?.first
        )
        assertEquals("Emby: Example Show S1E2 - Right Episode", resolved?.second)

        val fallbackMetadata = service.findMetadataByItemUrlPattern(
            "https://emby.example/Videos/episode-2/stream?api_key=rotated&static=true"
        )

        assertNotNull(fallbackMetadata)
        assertEquals("episode-2", fallbackMetadata?.embyItemId)
        assertEquals("media-2", fallbackMetadata?.mediaSourceId)
        assertEquals(420_000_000L, fallbackMetadata?.runTimeTicks)
    }

    @Test
    fun `findEmbyStream ignores cross-season entries when resolving requested episode`() = runTest {
        mockkStatic(Log::class)
        every { Log.d(any<String>(), any<String>()) } returns 0
        every { Log.w(any<String>(), any<String>()) } returns 0
        every { Log.e(any<String>(), any<String>(), any<Throwable>()) } returns 0

        every { authDataStore.state } returns flowOf(
            EmbyAuthState(
                serverUrl = "https://emby.example",
                apiKey = "secret",
                userId = "user-1",
                deviceId = "device-1"
            )
        )

        coEvery {
            embyApi.getUserItems(
                userId = "user-1",
                includeItemTypes = "Series",
                providerIds = "imdb.tt1234567",
                fields = any(),
                recursive = any(),
                limit = any()
            )
        } returns Response.success(
            EmbyItemsResponseDto(
                items = listOf(
                    EmbyItemDto(
                        id = "series-1",
                        name = "Example Show",
                        type = "Series"
                    )
                )
            )
        )

        coEvery {
            embyApi.getEpisodes(
                seriesId = "series-1",
                season = 2,
                includeItemTypes = any(),
                fields = any()
            )
        } returns Response.success(
            EmbyItemsResponseDto(
                items = listOf(
                    EmbyItemDto(
                        id = "season-1-episode-2",
                        name = "Wrong Season Episode",
                        type = "Episode",
                        parentIndexNumber = 1,
                        indexNumber = 2,
                        mediaSources = listOf(EmbyMediaSourceDto(id = "media-wrong"))
                    ),
                    EmbyItemDto(
                        id = "season-2-episode-2",
                        name = "Right Episode",
                        type = "Episode",
                        parentIndexNumber = 2,
                        indexNumber = 2,
                        mediaSources = listOf(EmbyMediaSourceDto(id = "media-right"))
                    )
                )
            )
        )

        val service = EmbyMediaService(embyApi, authDataStore)

        val resolved = service.findEmbyStream(
            contentId = "tt1234567",
            contentType = "series",
            season = 2,
            episode = 2
        )

        assertEquals(
            "https://emby.example/Videos/season-2-episode-2/stream?api_key=secret&static=true",
            resolved?.first
        )
        assertEquals("Emby: Example Show S2E2 - Right Episode", resolved?.second)
    }

    @Test
    fun `findEmbyStream returns null when season episodes lack usable episode numbers`() = runTest {
        mockkStatic(Log::class)
        every { Log.d(any<String>(), any<String>()) } returns 0
        every { Log.w(any<String>(), any<String>()) } returns 0
        every { Log.e(any<String>(), any<String>(), any<Throwable>()) } returns 0

        every { authDataStore.state } returns flowOf(
            EmbyAuthState(
                serverUrl = "https://emby.example",
                apiKey = "secret",
                userId = "user-1",
                deviceId = "device-1"
            )
        )

        coEvery {
            embyApi.getUserItems(
                userId = "user-1",
                includeItemTypes = "Series",
                providerIds = "imdb.tt1234567",
                fields = any(),
                recursive = any(),
                limit = any()
            )
        } returns Response.success(
            EmbyItemsResponseDto(
                items = listOf(
                    EmbyItemDto(
                        id = "series-1",
                        name = "Example Show",
                        type = "Series"
                    )
                )
            )
        )

        coEvery {
            embyApi.getEpisodes(
                seriesId = "series-1",
                season = 1,
                includeItemTypes = any(),
                fields = any()
            )
        } returns Response.success(
            EmbyItemsResponseDto(
                items = listOf(
                    EmbyItemDto(
                        id = "episode-1",
                        name = "Episode One",
                        type = "Episode",
                        parentIndexNumber = 1,
                        indexNumber = null,
                        mediaSources = listOf(EmbyMediaSourceDto(id = "media-1"))
                    ),
                    EmbyItemDto(
                        id = "episode-2",
                        name = "Episode Two",
                        type = "Episode",
                        parentIndexNumber = 1,
                        indexNumber = null,
                        mediaSources = listOf(EmbyMediaSourceDto(id = "media-2"))
                    )
                )
            )
        )

        val service = EmbyMediaService(embyApi, authDataStore)

        val resolved = service.findEmbyStream(
            contentId = "tt1234567",
            contentType = "series",
            season = 1,
            episode = 2
        )

        assertEquals(null, resolved)
    }
}

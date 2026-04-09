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
import io.mockk.unmockkStatic
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Response

@OptIn(ExperimentalCoroutinesApi::class)
class EmbyMediaServiceTest {

    private lateinit var embyApi: EmbyApi
    private lateinit var embyAuthDataStore: EmbyAuthDataStore
    private lateinit var service: EmbyMediaService

    private val connectedState = EmbyAuthState(
        serverUrl = "http://emby.example.com",
        apiKey = "test-api-key",
        userId = "user-123"
    )

    @Before
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.d(any(), any<String>()) } returns 0
        every { Log.w(any(), any<String>()) } returns 0
        every { Log.e(any(), any<String>()) } returns 0
        every { Log.e(any(), any<String>(), any<Throwable>()) } returns 0

        embyApi = mockk()
        embyAuthDataStore = mockk()
        service = EmbyMediaService(embyApi, embyAuthDataStore)
    }

    @After
    fun tearDown() {
        unmockkStatic(Log::class)
    }

    // ── connectivity guard ────────────────────────────────────────────────

    @Test
    fun `findEmbyStream returns null when not connected`() = runTest {
        every { embyAuthDataStore.state } returns flowOf(EmbyAuthState())

        val result = service.findEmbyStream("tt1234567", "movie", null, null)

        assertNull(result)
    }

    @Test
    fun `findEmbyStream returns null for blank contentId`() = runTest {
        every { embyAuthDataStore.state } returns flowOf(connectedState)

        assertNull(service.findEmbyStream(null, "movie", null, null))
        assertNull(service.findEmbyStream("", "movie", null, null))
        assertNull(service.findEmbyStream("   ", "movie", null, null))
    }

    // ── IMDB / TMDB ID parsing ────────────────────────────────────────────

    @Test
    fun `IMDB id produces imdb provider filter`() = runTest {
        every { embyAuthDataStore.state } returns flowOf(connectedState)
        coEvery {
            embyApi.getUserItems(
                userId = "user-123",
                includeItemTypes = "Movie",
                providerIds = "imdb.tt9876543"
            )
        } returns Response.success(EmbyItemsResponseDto(items = emptyList()))

        val result = service.findEmbyStream("tt9876543", "movie", null, null)

        assertNull(result)
    }

    @Test
    fun `TMDB id produces tmdb provider filter`() = runTest {
        every { embyAuthDataStore.state } returns flowOf(connectedState)
        coEvery {
            embyApi.getUserItems(
                userId = "user-123",
                includeItemTypes = "Movie",
                providerIds = "tmdb.12345"
            )
        } returns Response.success(EmbyItemsResponseDto(items = emptyList()))

        val result = service.findEmbyStream("tmdb:12345", "movie", null, null)

        assertNull(result)
    }

    @Test
    fun `unrecognized contentId with no provider ids returns null`() = runTest {
        every { embyAuthDataStore.state } returns flowOf(connectedState)

        val result = service.findEmbyStream("unknown-id-format", "movie", null, null)

        assertNull(result)
    }

    // ── movie lookup ──────────────────────────────────────────────────────

    @Test
    fun `movie lookup success returns stream url and display name`() = runTest {
        every { embyAuthDataStore.state } returns flowOf(connectedState)
        val movieItem = EmbyItemDto(id = "emby-movie-id", name = "Test Movie")
        coEvery {
            embyApi.getUserItems(
                userId = "user-123",
                includeItemTypes = "Movie",
                providerIds = "imdb.tt1234567"
            )
        } returns Response.success(EmbyItemsResponseDto(items = listOf(movieItem)))

        val result = service.findEmbyStream("tt1234567", "movie", null, null)

        assertNotNull(result)
        assertEquals(
            "http://emby.example.com/Videos/emby-movie-id/stream?api_key=test-api-key&static=true",
            result?.first
        )
        assertTrue(result?.second?.contains("Test Movie") == true)
    }

    @Test
    fun `movie lookup uses first media source id when available`() = runTest {
        every { embyAuthDataStore.state } returns flowOf(connectedState)
        val movieItem = EmbyItemDto(
            id = "emby-movie-id",
            name = "Test Movie",
            runTimeTicks = 60_000_000L,
            mediaSources = listOf(EmbyMediaSourceDto(id = "media-src-42"))
        )
        coEvery {
            embyApi.getUserItems(any(), any(), any())
        } returns Response.success(EmbyItemsResponseDto(items = listOf(movieItem)))

        val result = service.findEmbyStream("tt1234567", "movie", null, null)

        assertNotNull(result)
        val metadata = service.getMetadataForStream(result!!.first)
        assertEquals("media-src-42", metadata?.mediaSourceId)
        assertEquals(60_000_000L, metadata?.runTimeTicks)
    }

    @Test
    fun `movie lookup not found returns null`() = runTest {
        every { embyAuthDataStore.state } returns flowOf(connectedState)
        coEvery {
            embyApi.getUserItems(any(), any(), any())
        } returns Response.success(EmbyItemsResponseDto(items = emptyList()))

        val result = service.findEmbyStream("tt1234567", "movie", null, null)

        assertNull(result)
    }

    @Test
    fun `movie lookup HTTP error returns null`() = runTest {
        every { embyAuthDataStore.state } returns flowOf(connectedState)
        coEvery {
            embyApi.getUserItems(any(), any(), any())
        } returns Response.error(500, "{}".toResponseBody("application/json".toMediaType()))

        val result = service.findEmbyStream("tt1234567", "movie", null, null)

        assertNull(result)
    }

    // ── episode lookup ────────────────────────────────────────────────────

    @Test
    fun `episode lookup success returns stream url with episode display name`() = runTest {
        every { embyAuthDataStore.state } returns flowOf(connectedState)
        val seriesItem = EmbyItemDto(id = "series-id", name = "Test Show")
        val episodeItem = EmbyItemDto(id = "episode-id", name = "Pilot")
        coEvery {
            embyApi.getUserItems(
                userId = "user-123",
                includeItemTypes = "Series",
                providerIds = "imdb.tt5678901"
            )
        } returns Response.success(EmbyItemsResponseDto(items = listOf(seriesItem)))
        coEvery {
            embyApi.getEpisodes(seriesId = "series-id", season = 1, episode = 2)
        } returns Response.success(EmbyItemsResponseDto(items = listOf(episodeItem)))

        val result = service.findEmbyStream("tt5678901", "series", 1, 2)

        assertNotNull(result)
        assertEquals(
            "http://emby.example.com/Videos/episode-id/stream?api_key=test-api-key&static=true",
            result?.first
        )
        assertTrue(result?.second?.contains("Test Show") == true)
        assertTrue(result?.second?.contains("S1E2") == true)
        assertTrue(result?.second?.contains("Pilot") == true)
    }

    @Test
    fun `episode lookup tv type resolves as series`() = runTest {
        every { embyAuthDataStore.state } returns flowOf(connectedState)
        val seriesItem = EmbyItemDto(id = "series-tv-id", name = "TV Show")
        val episodeItem = EmbyItemDto(id = "ep-tv-id", name = "Episode 1")
        coEvery {
            embyApi.getUserItems(
                userId = "user-123",
                includeItemTypes = "Series",
                providerIds = "tmdb.99"
            )
        } returns Response.success(EmbyItemsResponseDto(items = listOf(seriesItem)))
        coEvery {
            embyApi.getEpisodes(seriesId = "series-tv-id", season = 2, episode = 3)
        } returns Response.success(EmbyItemsResponseDto(items = listOf(episodeItem)))

        val result = service.findEmbyStream("tmdb:99", "tv", 2, 3)

        assertNotNull(result)
        assertTrue(result?.second?.contains("S2E3") == true)
    }

    @Test
    fun `episode lookup series not found returns null`() = runTest {
        every { embyAuthDataStore.state } returns flowOf(connectedState)
        coEvery {
            embyApi.getUserItems(any(), any(), any())
        } returns Response.success(EmbyItemsResponseDto(items = emptyList()))

        val result = service.findEmbyStream("tt5678901", "series", 1, 2)

        assertNull(result)
    }

    @Test
    fun `episode lookup episode not found returns null`() = runTest {
        every { embyAuthDataStore.state } returns flowOf(connectedState)
        val seriesItem = EmbyItemDto(id = "series-id", name = "Test Show")
        coEvery {
            embyApi.getUserItems(any(), any(), any())
        } returns Response.success(EmbyItemsResponseDto(items = listOf(seriesItem)))
        coEvery {
            embyApi.getEpisodes(any(), any(), any())
        } returns Response.success(EmbyItemsResponseDto(items = emptyList()))

        val result = service.findEmbyStream("tt5678901", "series", 3, 7)

        assertNull(result)
    }

    @Test
    fun `episode lookup HTTP error on episode endpoint returns null`() = runTest {
        every { embyAuthDataStore.state } returns flowOf(connectedState)
        val seriesItem = EmbyItemDto(id = "series-id", name = "Test Show")
        coEvery {
            embyApi.getUserItems(any(), any(), any())
        } returns Response.success(EmbyItemsResponseDto(items = listOf(seriesItem)))
        coEvery {
            embyApi.getEpisodes(any(), any(), any())
        } returns Response.error(404, "{}".toResponseBody("application/json".toMediaType()))

        val result = service.findEmbyStream("tt5678901", "series", 1, 1)

        assertNull(result)
    }

    // ── metadata map ──────────────────────────────────────────────────────

    @Test
    fun `metadata is stored and retrievable by exact stream url`() = runTest {
        every { embyAuthDataStore.state } returns flowOf(connectedState)
        val item = EmbyItemDto(id = "item-abc", name = "Movie", runTimeTicks = 5_000_000L)
        coEvery { embyApi.getUserItems(any(), any(), any()) } returns
            Response.success(EmbyItemsResponseDto(items = listOf(item)))

        val result = service.findEmbyStream("tt1111111", "movie", null, null)

        val streamUrl = result!!.first
        val metadata = service.getMetadataForStream(streamUrl)
        assertNotNull(metadata)
        assertEquals("item-abc", metadata?.embyItemId)
        assertEquals(5_000_000L, metadata?.runTimeTicks)
    }

    @Test
    fun `metadata map size increments with each resolved stream`() = runTest {
        every { embyAuthDataStore.state } returns flowOf(connectedState)
        coEvery { embyApi.getUserItems(any(), any(), any()) } returnsMany listOf(
            Response.success(EmbyItemsResponseDto(items = listOf(EmbyItemDto(id = "item-1", name = "First")))),
            Response.success(EmbyItemsResponseDto(items = listOf(EmbyItemDto(id = "item-2", name = "Second"))))
        )

        service.findEmbyStream("tt1111111", "movie", null, null)
        assertEquals(1, service.metadataMapSize())

        service.findEmbyStream("tt2222222", "movie", null, null)
        assertEquals(2, service.metadataMapSize())
    }

    @Test
    fun `clearMetadata empties the map`() = runTest {
        every { embyAuthDataStore.state } returns flowOf(connectedState)
        val item = EmbyItemDto(id = "item-xyz", name = "Movie")
        coEvery { embyApi.getUserItems(any(), any(), any()) } returns
            Response.success(EmbyItemsResponseDto(items = listOf(item)))
        service.findEmbyStream("tt3333333", "movie", null, null)
        assertTrue(service.metadataMapSize() > 0)

        service.clearMetadata()

        assertEquals(0, service.metadataMapSize())
    }

    @Test
    fun `findMetadataByItemUrlPattern finds entry by item id in url`() = runTest {
        every { embyAuthDataStore.state } returns flowOf(connectedState)
        val item = EmbyItemDto(id = "pattern-item-id", name = "Movie")
        coEvery { embyApi.getUserItems(any(), any(), any()) } returns
            Response.success(EmbyItemsResponseDto(items = listOf(item)))
        service.findEmbyStream("tt4444444", "movie", null, null)

        val found = service.findMetadataByItemUrlPattern(
            "http://emby.example.com/Videos/pattern-item-id/stream?api_key=key&static=true"
        )

        assertNotNull(found)
        assertEquals("pattern-item-id", found?.embyItemId)
    }

    @Test
    fun `findMetadataByItemUrlPattern returns null when map is empty`() {
        val result = service.findMetadataByItemUrlPattern(
            "http://emby.example.com/Videos/some-id/stream"
        )
        assertNull(result)
    }

    @Test
    fun `getMetadataForStream returns null for unknown url`() {
        assertNull(service.getMetadataForStream("http://unknown.url/stream"))
    }
}

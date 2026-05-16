package com.omnio.tv.data.repository

import android.os.SystemClock
import android.util.Log
import com.omnio.tv.data.local.TraktSettingsDataStore
import com.omnio.tv.data.remote.api.TraktApi
import com.omnio.tv.data.remote.dto.trakt.TraktEpisodeDto
import com.omnio.tv.data.remote.dto.trakt.TraktHistoryAddNotFoundDto
import com.omnio.tv.data.remote.dto.trakt.TraktHistoryAddRequestDto
import com.omnio.tv.data.remote.dto.trakt.TraktHistoryAddResponseDto
import com.omnio.tv.data.remote.dto.trakt.TraktHistoryRemoveCountDto
import com.omnio.tv.data.remote.dto.trakt.TraktHistoryRemoveNotFoundDto
import com.omnio.tv.data.remote.dto.trakt.TraktHistoryRemoveRequestDto
import com.omnio.tv.data.remote.dto.trakt.TraktHistoryRemoveResponseDto
import com.omnio.tv.data.remote.dto.trakt.TraktIdsDto
import com.omnio.tv.domain.model.WatchProgress
import com.omnio.tv.domain.repository.MetaRepository
import com.omnio.tv.domain.tmdb.TmdbService
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test
import retrofit2.Response

class TraktProgressServiceTest {

    private val traktApi = mockk<TraktApi>()
    private val traktAuthService = mockk<TraktAuthService>()
    private val metaRepository = mockk<MetaRepository>()
    private val tmdbService = mockk<TmdbService>()
    private val traktSettingsDataStore = mockk<TraktSettingsDataStore>()
    private val traktEpisodeMappingService = mockk<TraktEpisodeMappingService>()

    @Before
    fun setUp() {
        mockkStatic(SystemClock::class)
        mockkStatic(Log::class)
        every { SystemClock.elapsedRealtime() } returns 1L
        every { Log.d(any(), any()) } returns 0
        every { Log.w(any(), any<String>()) } returns 0
        every { Log.w(any(), any<String>(), any()) } returns 0
        every { Log.e(any(), any<String>(), any()) } returns 0
    }

    @After
    fun tearDown() {
        unmockkStatic(SystemClock::class)
        unmockkStatic(Log::class)
    }

    @Test
    fun `batch add treats 2xx with added episodes as success`() = runTest {
        val service = service()
        coEvery { traktApi.addHistory(any(), any()) } returns Response.success(
            TraktHistoryAddResponseDto(added = TraktHistoryRemoveCountDto(episodes = 2))
        )
        coEvery { traktEpisodeMappingService.resolveEpisodeMapping(any(), any(), any(), any(), any()) } returns null

        service.markSeasonWatchedBatch(progressList())

        coVerify(exactly = 1) { traktApi.addHistory("Bearer token", any()) }
    }

    @Test
    fun `batch add treats 2xx with only not found episodes as failure`() = runTest {
        val service = service()
        coEvery { traktApi.addHistory(any(), any()) } returns Response.success(
            TraktHistoryAddResponseDto(
                added = TraktHistoryRemoveCountDto(episodes = 0),
                notFound = TraktHistoryAddNotFoundDto(episodes = listOf(notFoundEpisode()))
            )
        )
        coEvery { traktEpisodeMappingService.resolveEpisodeMapping(any(), any(), any(), any(), any()) } returns null

        assertFailsWithIllegalStateException {
            service.markSeasonWatchedBatch(progressList())
        }
    }

    @Test
    fun `batch remove treats non successful response as failure`() = runTest {
        val service = service()
        coEvery { traktApi.removeHistory(any(), any()) } returns Response.error(
            500,
            okhttp3.ResponseBody.create(null, "server error")
        )

        assertFailsWithIllegalStateException {
            service.removeSeasonFromHistoryBatch("trakt:10", listOf(1 to 1))
        }
    }

    @Test
    fun `batch remove treats 2xx with zero deleted episodes and not found episodes as failure`() = runTest {
        val service = service()
        coEvery { traktApi.removeHistory(any(), any()) } returns Response.success(
            TraktHistoryRemoveResponseDto(
                deleted = TraktHistoryRemoveCountDto(episodes = 0),
                notFound = TraktHistoryRemoveNotFoundDto(episodes = listOf(notFoundEpisode()))
            )
        )

        assertFailsWithIllegalStateException {
            service.removeSeasonFromHistoryBatch("trakt:10", listOf(1 to 1))
        }
    }

    @Test
    fun `batch add retries with remapped episode numbers when Trakt cannot find original episodes`() = runTest {
        val service = service()
        val requests = mutableListOf<TraktHistoryAddRequestDto>()
        coEvery { traktApi.addHistory(any(), capture(requests)) } returnsMany listOf(
            Response.success(
                TraktHistoryAddResponseDto(
                    added = TraktHistoryRemoveCountDto(episodes = 0),
                    notFound = TraktHistoryAddNotFoundDto(episodes = listOf(notFoundEpisode()))
                )
            ),
            Response.success(TraktHistoryAddResponseDto(added = TraktHistoryRemoveCountDto(episodes = 2)))
        )
        coEvery {
            traktEpisodeMappingService.resolveEpisodeMapping(any(), any(), any(), 1, 1)
        } returns EpisodeMappingEntry(season = 2, episode = 5, videoId = "trakt:10:2:5")
        coEvery {
            traktEpisodeMappingService.resolveEpisodeMapping(any(), any(), any(), 1, 2)
        } returns EpisodeMappingEntry(season = 2, episode = 6, videoId = "trakt:10:2:6")

        service.markSeasonWatchedBatch(progressList())

        assertEquals(2, requests.size)
        assertEquals(listOf(5, 6), requests[1].episodeNumbersForSeason(2))
    }

    @Test
    fun `batch remove retries with remapped episode numbers when Trakt cannot find original episodes`() = runTest {
        val service = service()
        val requests = mutableListOf<TraktHistoryRemoveRequestDto>()
        coEvery { traktApi.removeHistory(any(), capture(requests)) } returnsMany listOf(
            Response.success(
                TraktHistoryRemoveResponseDto(
                    deleted = TraktHistoryRemoveCountDto(episodes = 0),
                    notFound = TraktHistoryRemoveNotFoundDto(episodes = listOf(notFoundEpisode()))
                )
            ),
            Response.success(TraktHistoryRemoveResponseDto(deleted = TraktHistoryRemoveCountDto(episodes = 2)))
        )
        coEvery {
            traktEpisodeMappingService.resolveEpisodeMapping(any(), any(), any(), 1, 1)
        } returns EpisodeMappingEntry(season = 2, episode = 5, videoId = "trakt:10:2:5")
        coEvery {
            traktEpisodeMappingService.resolveEpisodeMapping(any(), any(), any(), 1, 2)
        } returns EpisodeMappingEntry(season = 2, episode = 6, videoId = "trakt:10:2:6")

        service.removeSeasonFromHistoryBatch("trakt:10", listOf(1 to 1, 1 to 2))

        assertEquals(2, requests.size)
        assertEquals(listOf(5, 6), requests[1].episodeNumbersForSeason(2))
    }

    @Test
    fun `single history add accepts TMDB only content ids`() = runTest {
        val service = service()
        val requests = mutableListOf<TraktHistoryAddRequestDto>()
        coEvery { traktApi.addHistory(any(), capture(requests)) } returns Response.success(
            TraktHistoryAddResponseDto(added = TraktHistoryRemoveCountDto(movies = 1))
        )

        service.markAsWatched(
            progress = movieProgress(contentId = "tmdb:123"),
            title = "Movie",
            year = 2024
        )

        assertEquals(123, requests.single().movies?.single()?.ids?.tmdb)
    }

    private fun service(): TraktProgressService {
        every { traktSettingsDataStore.continueWatchingDaysCap } returns MutableSharedFlow()
        coEvery { traktAuthService.executeAuthorizedWriteRequest<TraktHistoryAddResponseDto>(any()) } coAnswers {
            firstArg<suspend (String) -> Response<TraktHistoryAddResponseDto>>().invoke("Bearer token")
        }
        coEvery { traktAuthService.executeAuthorizedWriteRequest<TraktHistoryRemoveResponseDto>(any()) } coAnswers {
            firstArg<suspend (String) -> Response<TraktHistoryRemoveResponseDto>>().invoke("Bearer token")
        }

        return TraktProgressService(
            traktApi = traktApi,
            traktAuthService = traktAuthService,
            metaRepository = metaRepository,
            tmdbService = tmdbService,
            traktSettingsDataStore = traktSettingsDataStore,
            traktEpisodeMappingService = traktEpisodeMappingService
        )
    }

    private fun progressList(): List<WatchProgress> {
        return listOf(
            progress(season = 1, episode = 1, videoId = "trakt:10:1:1"),
            progress(season = 1, episode = 2, videoId = "trakt:10:1:2")
        )
    }

    private fun progress(season: Int, episode: Int, videoId: String): WatchProgress {
        return WatchProgress(
            contentId = "trakt:10",
            contentType = "series",
            name = "Show",
            poster = null,
            backdrop = null,
            logo = null,
            videoId = videoId,
            season = season,
            episode = episode,
            episodeTitle = null,
            position = 1L,
            duration = 1L,
            lastWatched = 1L
        )
    }

    private fun movieProgress(contentId: String): WatchProgress {
        return WatchProgress(
            contentId = contentId,
            contentType = "movie",
            name = "Movie",
            poster = null,
            backdrop = null,
            logo = null,
            videoId = contentId,
            season = null,
            episode = null,
            episodeTitle = null,
            position = 1L,
            duration = 1L,
            lastWatched = 1L
        )
    }

    private fun notFoundEpisode(): TraktEpisodeDto {
        return TraktEpisodeDto(
            season = 1,
            number = 1,
            ids = TraktIdsDto(trakt = 1001)
        )
    }

    private fun TraktHistoryAddRequestDto.episodeNumbersForSeason(season: Int): List<Int?> {
        return shows.orEmpty()
            .flatMap { it.seasons.orEmpty() }
            .first { it.number == season }
            .episodes.orEmpty()
            .map { it.number }
    }

    private fun TraktHistoryRemoveRequestDto.episodeNumbersForSeason(season: Int): List<Int> {
        return shows.orEmpty()
            .flatMap { it.seasons.orEmpty() }
            .first { it.number == season }
            .episodes.orEmpty()
            .map { it.number }
    }

    private suspend fun assertFailsWithIllegalStateException(block: suspend () -> Unit) {
        try {
            block()
            fail("Expected IllegalStateException")
        } catch (_: IllegalStateException) {
        }
    }
}

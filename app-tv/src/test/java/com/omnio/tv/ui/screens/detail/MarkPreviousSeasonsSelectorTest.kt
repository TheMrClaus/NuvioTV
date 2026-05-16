package com.omnio.tv.ui.screens.detail

import com.omnio.tv.domain.model.ContentType
import com.omnio.tv.domain.model.Meta
import com.omnio.tv.domain.model.PosterShape
import com.omnio.tv.domain.model.Video
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkPreviousSeasonsSelectorTest {

    private fun video(season: Int?, episode: Int?, id: String = "s${season}e${episode}") =
        Video(
            id = id,
            title = id,
            released = null,
            thumbnail = null,
            streams = emptyList(),
            season = season,
            episode = episode,
            overview = null,
            runtime = null,
            available = null
        )

    private fun meta(videos: List<Video>) = Meta(
        id = "tt0",
        type = ContentType.SERIES,
        name = "Test Show",
        poster = null,
        posterShape = PosterShape.POSTER,
        background = null,
        logo = null,
        description = null,
        releaseInfo = null,
        imdbRating = null,
        genres = emptyList(),
        runtime = null,
        director = emptyList(),
        cast = emptyList(),
        videos = videos,
        country = null,
        awards = null,
        language = null,
        links = emptyList()
    )

    private val alwaysUnwatched: (Int, Int) -> Boolean = { _, _ -> false }

    @Test
    fun `returns episodes from seasons 1 to N-1 only`() {
        val m = meta(listOf(
            video(1, 1), video(1, 2),
            video(2, 1), video(2, 2),
            video(3, 1), video(3, 2),
            video(4, 1)
        ))

        val result = selectPreviousSeasonsEpisodes(m, currentSeason = 3, isWatched = alwaysUnwatched)

        assertEquals(setOf(1 to 1, 1 to 2, 2 to 1, 2 to 2), result.map { it.season!! to it.episode!! }.toSet())
    }

    @Test
    fun `excludes specials season 0`() {
        val m = meta(listOf(
            video(0, 1), video(0, 2),
            video(1, 1),
            video(2, 1),
            video(3, 1)
        ))

        val result = selectPreviousSeasonsEpisodes(m, currentSeason = 3, isWatched = alwaysUnwatched)

        assertTrue("must not include season 0", result.none { it.season == 0 })
        assertEquals(setOf(1 to 1, 2 to 1), result.map { it.season!! to it.episode!! }.toSet())
    }

    @Test
    fun `season 1 returns empty list`() {
        val m = meta(listOf(video(1, 1), video(1, 2), video(2, 1)))

        val result = selectPreviousSeasonsEpisodes(m, currentSeason = 1, isWatched = alwaysUnwatched)

        assertTrue(result.isEmpty())
    }

    @Test
    fun `season 0 (specials) returns empty list`() {
        val m = meta(listOf(video(0, 1), video(1, 1), video(2, 1)))

        val result = selectPreviousSeasonsEpisodes(m, currentSeason = 0, isWatched = alwaysUnwatched)

        assertTrue(result.isEmpty())
    }

    @Test
    fun `filters out already watched episodes`() {
        val m = meta(listOf(video(1, 1), video(1, 2), video(2, 1), video(2, 2)))
        val watched: (Int, Int) -> Boolean = { s, e -> s == 1 && e == 1 }

        val result = selectPreviousSeasonsEpisodes(m, currentSeason = 3, isWatched = watched)

        assertEquals(setOf(1 to 2, 2 to 1, 2 to 2), result.map { it.season!! to it.episode!! }.toSet())
    }

    @Test
    fun `handles season gaps`() {
        val m = meta(listOf(video(1, 1), video(3, 1), video(3, 2), video(4, 1)))

        val result = selectPreviousSeasonsEpisodes(m, currentSeason = 4, isWatched = alwaysUnwatched)

        assertEquals(setOf(1 to 1, 3 to 1, 3 to 2), result.map { it.season!! to it.episode!! }.toSet())
    }

    @Test
    fun `skips videos with null season or null episode`() {
        val m = meta(listOf(
            video(null, 1, id = "no-season"),
            video(1, null, id = "no-episode"),
            video(1, 1)
        ))

        val result = selectPreviousSeasonsEpisodes(m, currentSeason = 2, isWatched = alwaysUnwatched)

        assertEquals(setOf(1 to 1), result.map { it.season!! to it.episode!! }.toSet())
    }
}

package com.omnio.tv.ui.screens.home

import com.omnio.tv.domain.model.ContentType
import com.omnio.tv.domain.model.MetaPreview
import com.omnio.tv.domain.model.PosterShape
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ModernHomeRowsTest {

    @Test
    fun `top ten rank starts at one for trending rows`() {
        val row = heroCarouselRow(title = "Trending Now")

        assertEquals(1, row.topTenRankOrNull(0))
        assertEquals(10, row.topTenRankOrNull(9))
    }

    @Test
    fun `top ten rank is null outside trending top ten`() {
        val row = heroCarouselRow(title = "Popular Movies")

        assertNull(row.topTenRankOrNull(0))
        assertNull(heroCarouselRow(title = "Trending Now").topTenRankOrNull(10))
    }

    @Test
    fun `source count subtitle is only shown for multiple catalog sources`() {
        val row = heroCarouselRow(
            title = "From Your Library",
            items = listOf(
                catalogItem(key = "1", addonBaseUrl = "https://a.example"),
                catalogItem(key = "2", addonBaseUrl = "https://b.example"),
                catalogItem(key = "3", addonBaseUrl = "https://b.example")
            )
        )

        assertEquals(2, row.distinctCatalogSourceCount())
        assertEquals("across 2 sources", row.sourceCountSubtitle())
    }

    @Test
    fun `source count subtitle is hidden for single source or continue watching`() {
        val singleSourceRow = heroCarouselRow(
            title = "From Your Library",
            items = listOf(
                catalogItem(key = "1", addonBaseUrl = "https://a.example"),
                catalogItem(key = "2", addonBaseUrl = "https://a.example")
            )
        )

        val continueWatchingRow = heroCarouselRow(
            title = "Continue Watching",
            items = listOf(continueWatchingItem(key = "cw_1"))
        )

        assertNull(singleSourceRow.sourceCountSubtitle())
        assertNull(continueWatchingRow.sourceCountSubtitle())
    }

    private fun heroCarouselRow(
        title: String,
        items: List<ModernCarouselItem> = emptyList()
    ) = HeroCarouselRow(
        key = title.lowercase().replace(' ', '_'),
        title = title,
        globalRowIndex = 0,
        items = items
    )

    private fun catalogItem(key: String, addonBaseUrl: String): ModernCarouselItem {
        val meta = MetaPreview(
            id = key,
            type = ContentType.MOVIE,
            name = "Title $key",
            poster = null,
            posterShape = PosterShape.POSTER,
            background = null,
            logo = null,
            description = null,
            releaseInfo = "2024",
            imdbRating = null,
            genres = emptyList()
        )
        return ModernCarouselItem(
            key = key,
            title = meta.name,
            subtitle = meta.releaseInfo,
            imageUrl = null,
            heroPreview = HeroPreview(
                title = meta.name,
                logo = null,
                description = null,
                contentTypeText = "Movie",
                yearText = meta.releaseInfo,
                imdbText = null,
                genres = emptyList(),
                poster = null,
                backdrop = null,
                imageUrl = null
            ),
            payload = ModernPayload.Catalog(
                focusKey = key,
                itemId = key,
                itemType = meta.apiType,
                addonBaseUrl = addonBaseUrl,
                trailerTitle = meta.name,
                trailerReleaseInfo = meta.releaseInfo,
                trailerApiType = meta.apiType
            ),
            metaPreview = meta
        )
    }

    private fun continueWatchingItem(key: String) = ModernCarouselItem(
        key = key,
        title = "Episode $key",
        subtitle = "S1E1",
        imageUrl = null,
        heroPreview = HeroPreview(
            title = "Episode $key",
            logo = null,
            description = null,
            contentTypeText = "Series",
            yearText = "2024",
            imdbText = null,
            genres = emptyList(),
            poster = null,
            backdrop = null,
            imageUrl = null
        ),
        payload = ModernPayload.ContinueWatching(
            ContinueWatchingItem.NextUp(
                info = NextUpInfo(
                    contentId = key,
                    contentType = "series",
                    name = "Episode $key",
                    poster = null,
                    backdrop = null,
                    logo = null,
                    videoId = key,
                    season = 1,
                    episode = 1,
                    episodeTitle = null,
                    thumbnail = null,
                    lastWatched = 0L,
                    sortTimestamp = 0L
                )
            )
        )
    )
}

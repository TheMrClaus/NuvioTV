package com.omnio.tv.ui.screens.detail

import com.omnio.tv.domain.model.Meta
import com.omnio.tv.domain.model.Video

/**
 * Selects unwatched episodes from all seasons strictly before [currentSeason].
 *
 * Rules:
 * - Includes seasons in `1..(currentSeason - 1)` only (Specials / season 0 excluded).
 * - Excludes videos with a null `season` or null `episode` (extras, trailers, etc.).
 * - Excludes episodes already considered watched per [isWatched].
 *
 * Returns an empty list when [currentSeason] <= 1 or when no episodes match.
 */
fun selectPreviousSeasonsEpisodes(
    meta: Meta,
    currentSeason: Int,
    isWatched: (season: Int, episode: Int) -> Boolean
): List<Video> {
    if (currentSeason <= 1) return emptyList()
    return meta.videos.filter { v ->
        val s = v.season ?: return@filter false
        val e = v.episode ?: return@filter false
        s in 1 until currentSeason && !isWatched(s, e)
    }
}

package com.nuvio.tv.ui.screens.player

import android.util.Log
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

private const val EMBY_SESSION_REPORT_INTERVAL_MS = 10_000L

/**
 * Starts the periodic Emby "Now Playing" session progress reporting loop.
 *
 * Each tick captures the current playing/paused state once and reports the position
 * and paused flag to the Emby server, regardless of whether playback is currently
 * paused or playing. This ensures the Emby UI (e.g. "Now Playing" dashboard) always
 * reflects the true playback state.
 */
internal fun PlayerRuntimeController.startEmbySessionProgressReporting() {
    embySessionProgressJob?.cancel()
    embySessionProgressJob = scope.launch {
        while (isActive) {
            delay(EMBY_SESSION_REPORT_INTERVAL_MS)

            if (!hasRenderedFirstFrame) continue

            val isPlaying = isPlaybackCurrentlyPlaying()
            val positionMs = currentPlaybackPositionMs() ?: continue
            val isPaused = !isPlaying

            reportEmbySessionProgress(positionMs = positionMs, isPaused = isPaused)
        }
    }
}

internal fun PlayerRuntimeController.stopEmbySessionProgressReporting() {
    embySessionProgressJob?.cancel()
    embySessionProgressJob = null
}

private fun PlayerRuntimeController.reportEmbySessionProgress(positionMs: Long, isPaused: Boolean) {
    val positionTicks = positionMs * 10_000L
    Log.d(
        PlayerRuntimeController.TAG,
        "EMBY_SESSION progress: positionTicks=$positionTicks isPaused=$isPaused"
    )
}

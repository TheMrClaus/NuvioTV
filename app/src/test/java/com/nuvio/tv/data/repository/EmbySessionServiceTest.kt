package com.nuvio.tv.data.repository

import android.util.Log
import com.nuvio.tv.data.local.EmbyAuthDataStore
import com.nuvio.tv.data.local.EmbyAuthState
import com.nuvio.tv.data.remote.api.EmbyApi
import com.nuvio.tv.data.remote.dto.emby.EmbyPlaybackProgressDto
import com.nuvio.tv.data.remote.dto.emby.EmbyPlaybackStartDto
import com.nuvio.tv.data.remote.dto.emby.EmbyPlaybackStopDto
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Response

class EmbySessionServiceTest {

    private val embyApi = mockk<EmbyApi>()
    private val authDataStore = mockk<EmbyAuthDataStore>()

    @Test
    fun `reportStart progress and stop reuse the same PlaySessionId and paused state`() = runTest {
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

        val startBodies = mutableListOf<EmbyPlaybackStartDto>()
        val progressBodies = mutableListOf<EmbyPlaybackProgressDto>()
        val stopBodies = mutableListOf<EmbyPlaybackStopDto>()

        coEvery { embyApi.reportPlaybackStart(capture(startBodies)) } returns Response.success(Unit)
        coEvery { embyApi.reportPlaybackProgress(capture(progressBodies)) } returns Response.success(Unit)
        coEvery { embyApi.reportPlaybackStopped(capture(stopBodies)) } returns Response.success(Unit)

        val service = EmbySessionService(embyApi, authDataStore)

        service.reportStart(itemId = "item-1", mediaSourceId = "media-1", positionMs = 1234)

        val lastProgressReportField = EmbySessionService::class.java.getDeclaredField("lastProgressReportMs")
        lastProgressReportField.isAccessible = true
        lastProgressReportField.setLong(service, 0L)

        service.reportProgress(positionMs = 5678, isPaused = true)
        service.reportStop(positionMs = 9999)

        assertEquals(1, startBodies.size)
        assertEquals(1, progressBodies.size)
        assertEquals(1, stopBodies.size)

        val startBody = startBodies.single()
        val progressBody = progressBodies.single()
        val stopBody = stopBodies.single()

        assertEquals("item-1", startBody.itemId)
        assertEquals("media-1", startBody.mediaSourceId)
        assertEquals(12_340_000L, startBody.positionTicks)

        assertEquals(startBody.playSessionId, progressBody.playSessionId)
        assertEquals(startBody.playSessionId, stopBody.playSessionId)
        assertEquals("item-1", progressBody.itemId)
        assertEquals("media-1", progressBody.mediaSourceId)
        assertEquals(56_780_000L, progressBody.positionTicks)
        assertTrue(progressBody.isPaused)
        assertEquals(99_990_000L, stopBody.positionTicks)
    }
}

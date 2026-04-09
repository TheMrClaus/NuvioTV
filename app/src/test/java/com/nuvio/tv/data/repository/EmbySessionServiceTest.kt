package com.nuvio.tv.data.repository

import android.util.Log
import com.nuvio.tv.data.local.EmbyAuthDataStore
import com.nuvio.tv.data.local.EmbyAuthState
import com.nuvio.tv.data.remote.api.EmbyApi
import com.nuvio.tv.data.remote.dto.emby.EmbyPlaybackProgressDto
import com.nuvio.tv.data.remote.dto.emby.EmbyPlaybackStartDto
import com.nuvio.tv.data.remote.dto.emby.EmbyPlaybackStopDto
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkStatic
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Response

@OptIn(ExperimentalCoroutinesApi::class)
class EmbySessionServiceTest {

    private lateinit var embyApi: EmbyApi
    private lateinit var embyAuthDataStore: EmbyAuthDataStore
    private lateinit var service: EmbySessionService

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
        service = EmbySessionService(embyApi, embyAuthDataStore)
    }

    @After
    fun tearDown() {
        unmockkStatic(Log::class)
    }

    // ── connectivity guard ────────────────────────────────────────────────

    @Test
    fun `reportStart skipped when not connected`() = runTest {
        every { embyAuthDataStore.state } returns flowOf(EmbyAuthState())

        service.reportStart("item-1", "src-1")

        coVerify(exactly = 0) { embyApi.reportPlaybackStart(any()) }
    }

    @Test
    fun `reportProgress skipped when not connected`() = runTest {
        every { embyAuthDataStore.state } returns flowOf(EmbyAuthState())

        service.reportProgress(5_000)

        coVerify(exactly = 0) { embyApi.reportPlaybackProgress(any()) }
    }

    @Test
    fun `reportStop skipped when not connected`() = runTest {
        every { embyAuthDataStore.state } returns flowOf(EmbyAuthState())

        service.reportStop(3_000)

        coVerify(exactly = 0) { embyApi.reportPlaybackStopped(any()) }
    }

    // ── reportStart ───────────────────────────────────────────────────────

    @Test
    fun `reportStart sends API call when connected`() = runTest {
        every { embyAuthDataStore.state } returns flowOf(connectedState)
        val slot = slot<EmbyPlaybackStartDto>()
        coEvery { embyApi.reportPlaybackStart(capture(slot)) } returns Response.success(Unit)

        service.reportStart("item-1", "src-1", positionMs = 2_000)

        coVerify(exactly = 1) { embyApi.reportPlaybackStart(any()) }
        assertEquals("item-1", slot.captured.itemId)
        assertEquals("src-1", slot.captured.mediaSourceId)
        assertEquals(20_000_000L, slot.captured.positionTicks)
        assertNotNull(slot.captured.playSessionId)
        assertTrue(slot.captured.playSessionId.isNotBlank())
    }

    @Test
    fun `reportStart deduplication prevents second call for same itemId`() = runTest {
        every { embyAuthDataStore.state } returns flowOf(connectedState)
        coEvery { embyApi.reportPlaybackStart(any()) } returns Response.success(Unit)

        service.reportStart("item-1", "src-1")
        service.reportStart("item-1", "src-1")

        coVerify(exactly = 1) { embyApi.reportPlaybackStart(any()) }
    }

    @Test
    fun `reportStart sends second call when itemId changes`() = runTest {
        every { embyAuthDataStore.state } returns flowOf(connectedState)
        coEvery { embyApi.reportPlaybackStart(any()) } returns Response.success(Unit)

        service.reportStart("item-1", "src-1")
        service.reportStart("item-2", "src-2")

        coVerify(exactly = 2) { embyApi.reportPlaybackStart(any()) }
    }

    @Test
    fun `reportStart does not update state on API failure`() = runTest {
        every { embyAuthDataStore.state } returns flowOf(connectedState)
        coEvery { embyApi.reportPlaybackStart(any()) } returns
            Response.error(500, "{}".toResponseBody("application/json".toMediaType()))

        service.reportStart("item-1", "src-1")
        // Call again — should retry because previous attempt failed
        service.reportStart("item-1", "src-1")

        coVerify(exactly = 2) { embyApi.reportPlaybackStart(any()) }
    }

    // ── reportProgress ────────────────────────────────────────────────────

    @Test
    fun `reportProgress is skipped when reportStart was never called`() = runTest {
        every { embyAuthDataStore.state } returns flowOf(connectedState)

        service.reportProgress(5_000)

        coVerify(exactly = 0) { embyApi.reportPlaybackProgress(any()) }
    }

    @Test
    fun `reportProgress sends isPaused=true correctly`() = runTest {
        every { embyAuthDataStore.state } returns flowOf(connectedState)
        coEvery { embyApi.reportPlaybackStart(any()) } returns Response.success(Unit)
        val slot = slot<EmbyPlaybackProgressDto>()
        coEvery { embyApi.reportPlaybackProgress(capture(slot)) } returns Response.success(Unit)

        service.reportStart("item-1", "src-1")
        service.lastProgressReportMs = 0L  // bypass throttle window

        service.reportProgress(10_000, isPaused = true)

        coVerify(exactly = 1) { embyApi.reportPlaybackProgress(any()) }
        assertTrue(slot.captured.isPaused)
        assertEquals("item-1", slot.captured.itemId)
        assertEquals(100_000_000L, slot.captured.positionTicks)
    }

    @Test
    fun `reportProgress sends isPaused=false correctly`() = runTest {
        every { embyAuthDataStore.state } returns flowOf(connectedState)
        coEvery { embyApi.reportPlaybackStart(any()) } returns Response.success(Unit)
        val slot = slot<EmbyPlaybackProgressDto>()
        coEvery { embyApi.reportPlaybackProgress(capture(slot)) } returns Response.success(Unit)

        service.reportStart("item-1", "src-1")
        service.lastProgressReportMs = 0L

        service.reportProgress(30_000, isPaused = false)

        assertFalse(slot.captured.isPaused)
        assertEquals(300_000_000L, slot.captured.positionTicks)
    }

    @Test
    fun `reportProgress is throttled within the interval window`() = runTest {
        every { embyAuthDataStore.state } returns flowOf(connectedState)
        coEvery { embyApi.reportPlaybackStart(any()) } returns Response.success(Unit)
        coEvery { embyApi.reportPlaybackProgress(any()) } returns Response.success(Unit)

        service.reportStart("item-1", "src-1")
        service.lastProgressReportMs = 0L

        service.reportProgress(5_000)           // passes throttle — sent
        // lastProgressReportMs is now System.currentTimeMillis()
        service.reportProgress(6_000)           // within throttle window — skipped

        coVerify(exactly = 1) { embyApi.reportPlaybackProgress(any()) }
    }

    // ── reportStop ────────────────────────────────────────────────────────

    @Test
    fun `reportStop sends API call and resets state`() = runTest {
        every { embyAuthDataStore.state } returns flowOf(connectedState)
        coEvery { embyApi.reportPlaybackStart(any()) } returns Response.success(Unit)
        val slot = slot<EmbyPlaybackStopDto>()
        coEvery { embyApi.reportPlaybackStopped(capture(slot)) } returns Response.success(Unit)

        service.reportStart("item-1", "src-1")
        service.reportStop(positionMs = 120_000)

        coVerify(exactly = 1) { embyApi.reportPlaybackStopped(any()) }
        assertEquals("item-1", slot.captured.itemId)
        assertEquals(1_200_000_000L, slot.captured.positionTicks)
    }

    @Test
    fun `reportStop clears session so subsequent reportProgress is ignored`() = runTest {
        every { embyAuthDataStore.state } returns flowOf(connectedState)
        coEvery { embyApi.reportPlaybackStart(any()) } returns Response.success(Unit)
        coEvery { embyApi.reportPlaybackStopped(any()) } returns Response.success(Unit)

        service.reportStart("item-1", "src-1")
        service.reportStop()
        service.lastProgressReportMs = 0L
        service.reportProgress(5_000)

        coVerify(exactly = 0) { embyApi.reportPlaybackProgress(any()) }
    }

    @Test
    fun `reportStop is skipped when reportStart was never called`() = runTest {
        every { embyAuthDataStore.state } returns flowOf(connectedState)

        service.reportStop()

        coVerify(exactly = 0) { embyApi.reportPlaybackStopped(any()) }
    }

    @Test
    fun `reportStop resets state even when API call fails`() = runTest {
        every { embyAuthDataStore.state } returns flowOf(connectedState)
        coEvery { embyApi.reportPlaybackStart(any()) } returns Response.success(Unit)
        coEvery { embyApi.reportPlaybackStopped(any()) } returns
            Response.error(503, "{}".toResponseBody("application/json".toMediaType()))

        service.reportStart("item-1", "src-1")
        service.reportStop()

        // State was reset — second reportProgress should be skipped
        service.lastProgressReportMs = 0L
        service.reportProgress(1_000)
        coVerify(exactly = 0) { embyApi.reportPlaybackProgress(any()) }
    }

    // ── tick conversion ───────────────────────────────────────────────────

    @Test
    fun `msToTicks converts 1 ms to 10000 ticks`() {
        assertEquals(10_000L, service.msToTicks(1L))
    }

    @Test
    fun `msToTicks converts 0 ms to 0 ticks`() {
        assertEquals(0L, service.msToTicks(0L))
    }

    @Test
    fun `msToTicks converts one hour to correct ticks`() {
        val oneHourMs = 3_600_000L
        assertEquals(36_000_000_000L, service.msToTicks(oneHourMs))
    }

    @Test
    fun `msToTicks converts 500 ms correctly`() {
        assertEquals(5_000_000L, service.msToTicks(500L))
    }

    // ── resetSession ──────────────────────────────────────────────────────

    @Test
    fun `resetSession allows reportStart again after reset`() = runTest {
        every { embyAuthDataStore.state } returns flowOf(connectedState)
        coEvery { embyApi.reportPlaybackStart(any()) } returns Response.success(Unit)

        service.reportStart("item-1", "src-1")
        service.resetSession()
        service.reportStart("item-1", "src-1")  // same id, but session was reset

        coVerify(exactly = 2) { embyApi.reportPlaybackStart(any()) }
    }
}

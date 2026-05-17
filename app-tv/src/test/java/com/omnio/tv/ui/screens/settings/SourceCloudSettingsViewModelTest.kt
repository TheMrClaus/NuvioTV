package com.omnio.tv.ui.screens.settings

import com.omnio.tv.MainDispatcherRule
import com.omnio.tv.domain.model.AddonStreams
import com.omnio.tv.domain.model.SourceCloudAdvancedConfigSession
import com.omnio.tv.domain.model.SourceCloudConfigState
import com.omnio.tv.domain.model.SourceCloudConfigStatus
import com.omnio.tv.domain.model.SourceCloudSearchRequest
import com.omnio.tv.domain.model.SourceCloudService
import com.omnio.tv.domain.model.SourceCloudServiceStatus
import com.omnio.tv.domain.model.SourceCloudSettings
import com.omnio.tv.domain.model.SourceCloudStatus
import com.omnio.tv.domain.repository.SourceCloudRepository
import com.omnio.tv.domain.result.NetworkResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class SourceCloudSettingsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `init loads source cloud status`() = runTest {
        val repository = FakeSourceCloudRepository(
            status = SourceCloudStatus(
                enabled = true,
                baseUrlConfigured = false,
                services = listOf(SourceCloudServiceStatus(SourceCloudService.REAL_DEBRID, connected = false))
            )
        )

        val viewModel = SourceCloudSettingsViewModel(repository)
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isLoading)
        assertEquals(repository.statusValue, viewModel.uiState.value.status)
        assertNull(viewModel.uiState.value.errorMessage)
        assertEquals(listOf("status"), repository.events)
    }

    @Test
    fun `set enabled mutates repository then refreshes status`() = runTest {
        val repository = FakeSourceCloudRepository(
            status = SourceCloudStatus(enabled = false, baseUrlConfigured = true, services = emptyList())
        )
        val viewModel = SourceCloudSettingsViewModel(repository)
        advanceUntilIdle()

        repository.statusValue = SourceCloudStatus(enabled = true, baseUrlConfigured = true, services = emptyList())
        viewModel.setEnabled(true)
        advanceUntilIdle()

        assertEquals(listOf("status", "setEnabled:true", "status"), repository.events)
        assertEquals(listOf(true), repository.enabledMutations)
        assertTrue(viewModel.uiState.value.status?.enabled == true)
        assertEquals(2, repository.statusCalls)
    }

    @Test
    fun `set service connected mutates repository then refreshes status`() = runTest {
        val repository = FakeSourceCloudRepository(
            status = SourceCloudStatus(enabled = true, baseUrlConfigured = true, services = emptyList())
        )
        val viewModel = SourceCloudSettingsViewModel(repository)
        advanceUntilIdle()

        repository.statusValue = SourceCloudStatus(
            enabled = true,
            baseUrlConfigured = true,
            services = listOf(SourceCloudServiceStatus(SourceCloudService.TORBOX, connected = true))
        )
        viewModel.setServiceConnected(SourceCloudService.TORBOX, connected = true)
        advanceUntilIdle()

        assertEquals(listOf("status", "setServiceConnected:torbox:true", "status"), repository.events)
        assertEquals(listOf(SourceCloudService.TORBOX to true), repository.serviceMutations)
        assertEquals(repository.statusValue, viewModel.uiState.value.status)
        assertEquals(2, repository.statusCalls)
    }

    @Test
    fun `refresh failure exposes generic error and keeps loading false`() = runTest {
        val repository = FakeSourceCloudRepository(
            status = SourceCloudStatus(enabled = true, baseUrlConfigured = true, services = emptyList()),
            statusError = IllegalStateException("backend token leaked")
        )

        val viewModel = SourceCloudSettingsViewModel(repository)
        advanceUntilIdle()

        assertEquals(listOf("status"), repository.events)
        assertFalse(viewModel.uiState.value.isLoading)
        assertNull(viewModel.uiState.value.status)
        assertEquals("Source Cloud is unavailable right now.", viewModel.uiState.value.errorMessage)
    }

    @Test
    fun `mutation failure does not refresh status and exposes generic error`() = runTest {
        val initialStatus = SourceCloudStatus(enabled = false, baseUrlConfigured = true, services = emptyList())
        val repository = FakeSourceCloudRepository(
            status = initialStatus,
            setEnabledError = IllegalStateException("write failed")
        )
        val viewModel = SourceCloudSettingsViewModel(repository)
        advanceUntilIdle()

        viewModel.setEnabled(true)
        advanceUntilIdle()

        assertEquals(listOf("status", "setEnabled:true"), repository.events)
        assertEquals(initialStatus, viewModel.uiState.value.status)
        assertFalse(viewModel.uiState.value.isLoading)
        assertEquals("Source Cloud settings could not be updated.", viewModel.uiState.value.errorMessage)
    }

    @Test
    fun `request advanced config session stores short lived url state`() = runTest {
        val repository = FakeSourceCloudRepository(
            status = SourceCloudStatus(
                enabled = true,
                baseUrlConfigured = true,
                config = SourceCloudConfigState(
                    status = SourceCloudConfigStatus.READY,
                    advancedConfigAvailable = true
                ),
                services = listOf(SourceCloudServiceStatus(SourceCloudService.REAL_DEBRID, connected = true))
            ),
            advancedSession = NetworkResult.Success(
                SourceCloudAdvancedConfigSession(
                    url = "https://source.omnio.tv/advanced/session/abc",
                    expiresAtEpochMillis = 1_770_000_000_000L,
                    message = "Scan this QR code from your phone."
                )
            )
        )
        val viewModel = SourceCloudSettingsViewModel(repository)
        advanceUntilIdle()

        viewModel.requestAdvancedConfigSession()
        advanceUntilIdle()

        assertEquals(listOf("status", "requestAdvancedConfigSession"), repository.events)
        assertEquals("https://source.omnio.tv/advanced/session/abc", viewModel.uiState.value.advancedConfigSession?.url)
        assertEquals("Scan this QR code from your phone.", viewModel.uiState.value.advancedConfigSession?.message)
        assertNull(viewModel.uiState.value.errorMessage)
    }

    @Test
    fun `advanced config failure uses generic retryable error without exposing backend details`() = runTest {
        val repository = FakeSourceCloudRepository(
            status = SourceCloudStatus(enabled = true, baseUrlConfigured = true, services = emptyList()),
            advancedSession = NetworkResult.Success(null)
        )
        val viewModel = SourceCloudSettingsViewModel(repository)
        advanceUntilIdle()

        viewModel.requestAdvancedConfigSession()
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.advancedConfigSession)
        assertEquals("Advanced Source Config is unavailable right now.", viewModel.uiState.value.errorMessage)
    }

    @Test
    fun `reset config refreshes status and clears advanced session`() = runTest {
        val resetStatus = SourceCloudStatus(
            enabled = true,
            baseUrlConfigured = true,
            config = SourceCloudConfigState(status = SourceCloudConfigStatus.NOT_PROVISIONED, canReset = false),
            services = emptyList()
        )
        val repository = FakeSourceCloudRepository(
            status = SourceCloudStatus(enabled = true, baseUrlConfigured = true, services = emptyList()),
            resetStatus = resetStatus,
            advancedSession = NetworkResult.Success(SourceCloudAdvancedConfigSession("https://source.omnio.tv/advanced/session/abc"))
        )
        val viewModel = SourceCloudSettingsViewModel(repository)
        advanceUntilIdle()
        viewModel.requestAdvancedConfigSession()
        advanceUntilIdle()

        viewModel.resetConfig()
        advanceUntilIdle()

        assertEquals(listOf("status", "requestAdvancedConfigSession", "resetConfig"), repository.events)
        assertEquals(resetStatus, viewModel.uiState.value.status)
        assertNull(viewModel.uiState.value.advancedConfigSession)
    }

    private class FakeSourceCloudRepository(
        status: SourceCloudStatus,
        private val statusError: Throwable? = null,
        private val setEnabledError: Throwable? = null,
        private val setServiceConnectedError: Throwable? = null,
        private val advancedSession: NetworkResult<SourceCloudAdvancedConfigSession?> = NetworkResult.Success(null),
        private val resetStatus: SourceCloudStatus? = null
    ) : SourceCloudRepository {
        override val settings: Flow<SourceCloudSettings> = MutableStateFlow(SourceCloudSettings())
        var statusValue = status
        var statusCalls = 0
        val events = mutableListOf<String>()
        val enabledMutations = mutableListOf<Boolean>()
        val serviceMutations = mutableListOf<Pair<SourceCloudService, Boolean>>()

        override suspend fun status(): SourceCloudStatus {
            events += "status"
            statusCalls += 1
            statusError?.let { throw it }
            return statusValue
        }

        override suspend fun setEnabled(enabled: Boolean) {
            events += "setEnabled:$enabled"
            setEnabledError?.let { throw it }
            enabledMutations += enabled
        }

        override suspend fun setServiceConnected(service: SourceCloudService, connected: Boolean) {
            events += "setServiceConnected:${service.key}:$connected"
            setServiceConnectedError?.let { throw it }
            serviceMutations += service to connected
        }

        override suspend fun search(request: SourceCloudSearchRequest): NetworkResult<AddonStreams?> =
            error("search should not be called")

        override suspend fun requestAdvancedConfigSession(): NetworkResult<SourceCloudAdvancedConfigSession?> {
            events += "requestAdvancedConfigSession"
            return advancedSession
        }

        override suspend fun resetConfig(): SourceCloudStatus {
            events += "resetConfig"
            return resetStatus ?: statusValue
        }
    }
}

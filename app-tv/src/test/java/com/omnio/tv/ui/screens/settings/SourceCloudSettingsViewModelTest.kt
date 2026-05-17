package com.omnio.tv.ui.screens.settings

import com.omnio.tv.MainDispatcherRule
import com.omnio.tv.domain.model.AddonStreams
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

    private class FakeSourceCloudRepository(
        status: SourceCloudStatus,
        private val statusError: Throwable? = null,
        private val setEnabledError: Throwable? = null,
        private val setServiceConnectedError: Throwable? = null
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
    }
}

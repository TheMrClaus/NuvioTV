package com.omnio.tv.data.repository

import android.util.Log
import com.omnio.tv.data.local.SourceCloudSettingsDataStore
import com.omnio.tv.data.remote.api.SourceCloudApi
import com.omnio.tv.data.remote.dto.sourcecloud.SourceCloudAdvancedConfigSessionResponseDto
import com.omnio.tv.data.remote.dto.sourcecloud.SourceCloudProfileScopedRequestDto
import com.omnio.tv.domain.model.SourceCloudAdvancedConfigSession
import com.omnio.tv.domain.model.SourceCloudConfigStatus
import com.omnio.tv.domain.model.SourceCloudService
import com.omnio.tv.domain.model.SourceCloudSettings
import com.omnio.tv.domain.profile.ProfileManager
import com.omnio.tv.domain.result.NetworkResult
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import retrofit2.Response

class SourceCloudRepositoryImplTest {
    @Test
    fun `advanced config session request maps successful backend response`() = runTest {
        val api = mockk<SourceCloudApi>()
        coEvery { api.createAdvancedConfigSession(any()) } returns Response.success(
            SourceCloudAdvancedConfigSessionResponseDto(
                url = "https://source.omnio.tv/advanced/session/abc",
                expiresAtEpochMillis = 1_770_000_000_000L,
                message = "Scan to open advanced source config",
                configurePassword = "secret-password",
                directConfigureUrl = "https://account.omnio.tv/aios/configure"
            )
        )
        val repository = repository(api)

        val result = repository.requestAdvancedConfigSession()

        assertEquals(
            NetworkResult.Success(
                SourceCloudAdvancedConfigSession(
                    url = "https://source.omnio.tv/advanced/session/abc",
                    expiresAtEpochMillis = 1_770_000_000_000L,
                    message = "Scan to open advanced source config",
                    configurePassword = "secret-password",
                    directConfigureUrl = "https://account.omnio.tv/aios/configure"
                )
            ),
            result
        )
        coVerify(exactly = 1) {
            api.createAdvancedConfigSession(SourceCloudProfileScopedRequestDto(profileId = 1))
        }
    }

    @Test
    fun `advanced config failure is optional and returns null`() = runTest {
        mockAndroidLog()
        val api = mockk<SourceCloudApi>()
        coEvery { api.createAdvancedConfigSession(any()) } returns Response.error(503, "unavailable".toResponseBody())
        val repository = repository(api)

        val result = repository.requestAdvancedConfigSession()

        assertEquals(NetworkResult.Success(null), result)
    }

    @Test
    fun `reset source config ignores backend failure and refreshes local status shape`() = runTest {
        mockAndroidLog()
        val api = mockk<SourceCloudApi>()
        coEvery { api.resetConfig(any()) } returns Response.error(500, "failed".toResponseBody())
        val repository = repository(api)

        val status = repository.resetConfig()

        assertEquals(SourceCloudConfigStatus.UNKNOWN, status.config.status)
        assertNull(status.config.message)
        coVerify(exactly = 1) {
            api.resetConfig(SourceCloudProfileScopedRequestDto(profileId = 1))
        }
    }

    private fun repository(
        api: SourceCloudApi,
        settings: SourceCloudSettings = SourceCloudSettings(
            enabled = true,
            connectedServices = setOf(SourceCloudService.REAL_DEBRID)
        ),
        profileId: Int = 1
    ): SourceCloudRepositoryImpl {
        val dataStore = mockk<SourceCloudSettingsDataStore>(relaxed = true)
        every { dataStore.settings } returns MutableStateFlow(settings)
        val profileManager = mockk<ProfileManager>()
        every { profileManager.activeProfileId } returns MutableStateFlow(profileId)
        return SourceCloudRepositoryImpl(
            api,
            dataStore,
            profileManager,
            baseUrlConfiguredForTests = true,
            testOnly = Unit
        )
    }

    private fun mockAndroidLog() {
        mockkStatic(Log::class)
        every { Log.w(any<String>(), any<String>()) } returns 0
    }

}

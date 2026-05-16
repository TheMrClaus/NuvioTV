package com.omnio.tv.data.repository

import com.omnio.tv.data.local.TraktAuthState
import com.omnio.tv.data.remote.api.TraktApi
import com.omnio.tv.data.remote.dto.trakt.TraktIdsDto
import com.omnio.tv.data.remote.dto.trakt.TraktScrobbleResponseDto
import com.omnio.tv.domain.profile.ProfileManager
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Test
import retrofit2.Response
import java.io.IOException

class TraktScrobbleServiceTest {

    private val traktApi = mockk<TraktApi>()
    private val traktAuthService = mockk<TraktAuthService>()
    private val traktProgressService = mockk<TraktProgressService>()
    private val profileManager = mockk<ProfileManager>()

    @Test
    fun `scrobble stop retries transient IO failure and succeeds`() = runTest {
        val service = service()
        coEvery { traktApi.scrobbleStop(any(), any()) } throws IOException("timeout") andThen Response.success(
            201,
            TraktScrobbleResponseDto(action = "stop")
        )
        coEvery { traktProgressService.refreshNow() } returns Unit

        service.scrobbleStop(movieItem(), 90f)

        coVerify(exactly = 2) { traktApi.scrobbleStop("Bearer token", any()) }
        coVerify(exactly = 1) { traktProgressService.refreshNow() }
    }

    @Test
    fun `scrobble stop retries 5xx response and succeeds`() = runTest {
        val service = service()
        coEvery { traktApi.scrobbleStop(any(), any()) } returnsMany listOf(
            Response.error(500, okhttp3.ResponseBody.create(null, "server error")),
            Response.success(201, TraktScrobbleResponseDto(action = "stop"))
        )
        coEvery { traktProgressService.refreshNow() } returns Unit

        service.scrobbleStop(movieItem(), 90f)

        coVerify(exactly = 2) { traktApi.scrobbleStop("Bearer token", any()) }
        coVerify(exactly = 1) { traktProgressService.refreshNow() }
    }

    @Test
    fun `scrobble start does not retry`() = runTest {
        val service = service()
        coEvery { traktApi.scrobbleStart(any(), any()) } returns Response.error(
            500,
            okhttp3.ResponseBody.create(null, "server error")
        )

        service.scrobbleStart(movieItem(), 10f)

        coVerify(exactly = 1) { traktApi.scrobbleStart("Bearer token", any()) }
    }

    private fun service(): TraktScrobbleService {
        every { profileManager.activeProfile } returns null
        every { traktAuthService.hasRequiredCredentials() } returns true
        coEvery { traktAuthService.getCurrentAuthState() } returns TraktAuthState(
            accessToken = "access",
            refreshToken = "refresh"
        )
        coEvery { traktAuthService.executeAuthorizedWriteRequest<TraktScrobbleResponseDto>(any()) } coAnswers {
            firstArg<suspend (String) -> Response<TraktScrobbleResponseDto>>().invoke("Bearer token")
        }
        return TraktScrobbleService(
            traktApi = traktApi,
            traktAuthService = traktAuthService,
            traktProgressService = traktProgressService,
            profileManager = profileManager
        )
    }

    private fun movieItem(): TraktScrobbleItem.Movie {
        return TraktScrobbleItem.Movie(
            title = "Movie",
            year = 2024,
            ids = TraktIdsDto(trakt = 10)
        )
    }
}

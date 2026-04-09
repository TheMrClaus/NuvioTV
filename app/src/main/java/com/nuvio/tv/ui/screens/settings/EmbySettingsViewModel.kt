package com.nuvio.tv.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nuvio.tv.data.local.EmbyAuthDataStore
import com.nuvio.tv.data.local.EmbyAuthState
import com.nuvio.tv.data.remote.api.EmbyApi
import com.squareup.moshi.Moshi
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import javax.inject.Inject

data class EmbySettingsUiState(
    val serverUrl: String = "",
    val apiKey: String = "",
    val isTesting: Boolean = false,
    val testResult: String? = null,
    val isTestSuccess: Boolean = false
)

@HiltViewModel
class EmbySettingsViewModel @Inject constructor(
    private val embyAuthDataStore: EmbyAuthDataStore,
    private val okHttpClient: OkHttpClient,
    private val moshi: Moshi
) : ViewModel() {

    val authState: StateFlow<EmbyAuthState> = embyAuthDataStore.state
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), EmbyAuthState())

    private val _uiState = MutableStateFlow(EmbySettingsUiState())
    val uiState: StateFlow<EmbySettingsUiState> = _uiState.asStateFlow()

    fun updateServerUrl(url: String) {
        _uiState.value = _uiState.value.copy(serverUrl = url, testResult = null)
    }

    fun updateApiKey(key: String) {
        _uiState.value = _uiState.value.copy(apiKey = key, testResult = null)
    }

    fun testConnection() {
        val state = _uiState.value
        if (state.serverUrl.isBlank() || state.apiKey.isBlank()) {
            _uiState.value = state.copy(testResult = "Server URL and API Key are required", isTestSuccess = false)
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isTesting = true, testResult = null)

            try {
                val tempServerUrl = state.serverUrl.trimEnd('/')
                val apiKey = state.apiKey

                // Build a one-off OkHttp client with credentials injected directly into the
                // interceptor, so we never write partial or placeholder credentials to the
                // shared DataStore (which would briefly flip isConnected to true).
                val testDeviceId = java.util.UUID.randomUUID().toString()
                val testClient = okHttpClient.newBuilder()
                    .addInterceptor { chain ->
                        val originalUrl = chain.request().url.toString()
                        val url = if (originalUrl.startsWith("http://localhost/")) {
                            originalUrl.replaceFirst("http://localhost", tempServerUrl)
                        } else {
                            originalUrl
                        }
                        val authHeader = "MediaBrowser Client=\"NuvioTV\", Device=\"Android TV\", DeviceId=\"$testDeviceId\", Version=\"1.0.0\", Token=\"$apiKey\""
                        val request = chain.request().newBuilder()
                            .url(url)
                            .header("X-Emby-Authorization", authHeader)
                            .build()
                        chain.proceed(request)
                    }
                    .build()

                val testApi = Retrofit.Builder()
                    .baseUrl("http://localhost/")
                    .client(testClient)
                    .addConverterFactory(MoshiConverterFactory.create(moshi))
                    .build()
                    .create(EmbyApi::class.java)

                // Test connection by getting system info
                val systemInfoResponse = testApi.getSystemInfo()
                if (!systemInfoResponse.isSuccessful) {
                    _uiState.value = _uiState.value.copy(
                        isTesting = false,
                        testResult = "Connection failed: ${systemInfoResponse.code()} ${systemInfoResponse.message()}",
                        isTestSuccess = false
                    )
                    return@launch
                }

                val serverName = systemInfoResponse.body()?.serverName ?: "Emby Server"

                // Get users and pick first admin (matching NuvioMobile pattern)
                val usersResponse = testApi.getUsers()
                if (!usersResponse.isSuccessful) {
                    _uiState.value = _uiState.value.copy(
                        isTesting = false,
                        testResult = "Connected to $serverName but failed to fetch users",
                        isTestSuccess = false
                    )
                    return@launch
                }

                val users = usersResponse.body() ?: emptyList()
                val adminUser = users.firstOrNull { it.policy?.isAdministrator == true } ?: users.firstOrNull()
                if (adminUser == null) {
                    _uiState.value = _uiState.value.copy(
                        isTesting = false,
                        testResult = "No users found on $serverName",
                        isTestSuccess = false
                    )
                    return@launch
                }

                // Save real credentials now that we have the real userId — DataStore is only
                // written on success, so isConnected never flips true with a bogus userId.
                embyAuthDataStore.saveCredentials(
                    serverUrl = tempServerUrl,
                    apiKey = apiKey,
                    userId = adminUser.id
                )

                _uiState.value = _uiState.value.copy(
                    isTesting = false,
                    testResult = "Connected to $serverName as ${adminUser.name ?: "Unknown User"}",
                    isTestSuccess = true
                )
            } catch (e: Exception) {
                // No credentials were written to DataStore, so no cleanup is needed.
                _uiState.value = _uiState.value.copy(
                    isTesting = false,
                    testResult = "Connection error: ${e.message}",
                    isTestSuccess = false
                )
            }
        }
    }

    fun disconnect() {
        viewModelScope.launch {
            embyAuthDataStore.clearCredentials()
            _uiState.value = EmbySettingsUiState()
        }
    }
}

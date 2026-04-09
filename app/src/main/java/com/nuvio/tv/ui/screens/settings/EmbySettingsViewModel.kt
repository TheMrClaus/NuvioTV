package com.nuvio.tv.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nuvio.tv.data.local.EmbyAuthDataStore
import com.nuvio.tv.data.local.EmbyAuthState
import com.nuvio.tv.data.remote.api.EmbyApi
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class EmbyConnectionResult {
    object MissingCredentials : EmbyConnectionResult()
    data class ConnectionFailed(val code: Int, val message: String) : EmbyConnectionResult()
    data class FetchUsersFailed(val serverName: String) : EmbyConnectionResult()
    data class NoUsers(val serverName: String) : EmbyConnectionResult()
    data class Success(val serverName: String, val userName: String?) : EmbyConnectionResult()
    data class NetworkError(val message: String?) : EmbyConnectionResult()
}

data class EmbySettingsUiState(
    val serverUrl: String = "",
    val apiKey: String = "",
    val isTesting: Boolean = false,
    val connectionResult: EmbyConnectionResult? = null
) {
    val isTestSuccess: Boolean get() = connectionResult is EmbyConnectionResult.Success
}

@HiltViewModel
class EmbySettingsViewModel @Inject constructor(
    private val embyAuthDataStore: EmbyAuthDataStore,
    private val embyApi: EmbyApi
) : ViewModel() {

    val authState: StateFlow<EmbyAuthState> = embyAuthDataStore.state
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), EmbyAuthState())

    private val _uiState = MutableStateFlow(EmbySettingsUiState())
    val uiState: StateFlow<EmbySettingsUiState> = _uiState.asStateFlow()

    fun updateServerUrl(url: String) {
        _uiState.value = _uiState.value.copy(serverUrl = url, connectionResult = null)
    }

    fun updateApiKey(key: String) {
        _uiState.value = _uiState.value.copy(apiKey = key, connectionResult = null)
    }

    fun testConnection() {
        val state = _uiState.value
        if (state.serverUrl.isBlank() || state.apiKey.isBlank()) {
            _uiState.value = state.copy(connectionResult = EmbyConnectionResult.MissingCredentials)
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isTesting = true, connectionResult = null)

            try {
                // Temporarily save credentials to make the API call work (interceptor reads from DataStore)
                val tempServerUrl = state.serverUrl.trimEnd('/')
                embyAuthDataStore.saveCredentials(
                    serverUrl = tempServerUrl,
                    apiKey = state.apiKey,
                    userId = "TEMP_LOOKUP_USER" // Placeholder until real userId is obtained from server
                )

                // Test connection by getting system info
                val systemInfoResponse = embyApi.getSystemInfo()
                if (!systemInfoResponse.isSuccessful) {
                    embyAuthDataStore.clearCredentials()
                    _uiState.value = _uiState.value.copy(
                        isTesting = false,
                        connectionResult = EmbyConnectionResult.ConnectionFailed(
                            code = systemInfoResponse.code(),
                            message = systemInfoResponse.message()
                        )
                    )
                    return@launch
                }

                val serverName = systemInfoResponse.body()?.serverName ?: "Emby Server"

                // Get users and pick first admin (matching NuvioMobile pattern)
                val usersResponse = embyApi.getUsers()
                if (!usersResponse.isSuccessful) {
                    embyAuthDataStore.clearCredentials()
                    _uiState.value = _uiState.value.copy(
                        isTesting = false,
                        connectionResult = EmbyConnectionResult.FetchUsersFailed(serverName)
                    )
                    return@launch
                }

                val users = usersResponse.body() ?: emptyList()
                val adminUser = users.firstOrNull { it.policy?.isAdministrator == true } ?: users.firstOrNull()
                if (adminUser == null) {
                    embyAuthDataStore.clearCredentials()
                    _uiState.value = _uiState.value.copy(
                        isTesting = false,
                        connectionResult = EmbyConnectionResult.NoUsers(serverName)
                    )
                    return@launch
                }

                // Save real credentials with userId
                embyAuthDataStore.saveCredentials(
                    serverUrl = tempServerUrl,
                    apiKey = state.apiKey,
                    userId = adminUser.id
                )

                _uiState.value = _uiState.value.copy(
                    isTesting = false,
                    connectionResult = EmbyConnectionResult.Success(
                        serverName = serverName,
                        userName = adminUser.name
                    )
                )
            } catch (e: Exception) {
                embyAuthDataStore.clearCredentials()
                _uiState.value = _uiState.value.copy(
                    isTesting = false,
                    connectionResult = EmbyConnectionResult.NetworkError(e.message)
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

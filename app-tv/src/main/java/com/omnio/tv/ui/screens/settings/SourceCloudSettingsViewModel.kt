package com.omnio.tv.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.omnio.tv.domain.model.SourceCloudAdvancedConfigSession
import com.omnio.tv.domain.model.SourceCloudService
import com.omnio.tv.domain.model.SourceCloudStatus
import com.omnio.tv.domain.repository.SourceCloudRepository
import com.omnio.tv.domain.result.NetworkResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val SOURCE_CLOUD_UNAVAILABLE_MESSAGE = "Source Cloud is unavailable right now."
private const val SOURCE_CLOUD_UPDATE_FAILED_MESSAGE = "Source Cloud settings could not be updated."
private const val SOURCE_CLOUD_ADVANCED_UNAVAILABLE_MESSAGE = "Advanced Source Config is unavailable right now."

@HiltViewModel
class SourceCloudSettingsViewModel @Inject constructor(
    private val repository: SourceCloudRepository
) : ViewModel() {

    data class UiState(
        val isLoading: Boolean = false,
        val isAdvancedConfigLoading: Boolean = false,
        val status: SourceCloudStatus? = null,
        val advancedConfigSession: SourceCloudAdvancedConfigSession? = null,
        val errorMessage: String? = null
    )

    private val _uiState = MutableStateFlow(UiState(isLoading = true))
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            runCatching { repository.status() }
                .onSuccess { status ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            status = status,
                            errorMessage = null
                        )
                    }
                }
                .onFailure {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = SOURCE_CLOUD_UNAVAILABLE_MESSAGE
                        )
                    }
                }
        }
    }

    fun setEnabled(enabled: Boolean) {
        viewModelScope.launch {
            mutateAndRefresh { repository.setEnabled(enabled) }
        }
    }

    fun setServiceConnected(service: SourceCloudService, connected: Boolean) {
        viewModelScope.launch {
            mutateAndRefresh { repository.setServiceConnected(service, connected) }
        }
    }

    fun requestAdvancedConfigSession() {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isAdvancedConfigLoading = true,
                    advancedConfigSession = null,
                    errorMessage = null
                )
            }
            when (val result = repository.requestAdvancedConfigSession()) {
                is NetworkResult.Success -> {
                    _uiState.update {
                        it.copy(
                            isAdvancedConfigLoading = false,
                            advancedConfigSession = result.data,
                            errorMessage = if (result.data == null) SOURCE_CLOUD_ADVANCED_UNAVAILABLE_MESSAGE else null
                        )
                    }
                }
                is NetworkResult.Error -> {
                    _uiState.update {
                        it.copy(
                            isAdvancedConfigLoading = false,
                            errorMessage = SOURCE_CLOUD_ADVANCED_UNAVAILABLE_MESSAGE
                        )
                    }
                }
                NetworkResult.Loading -> Unit
            }
        }
    }

    fun resetConfig() {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isLoading = true,
                    advancedConfigSession = null,
                    errorMessage = null
                )
            }
            runCatching { repository.resetConfig() }
                .onSuccess { status ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            status = status,
                            errorMessage = null
                        )
                    }
                }
                .onFailure {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = SOURCE_CLOUD_UPDATE_FAILED_MESSAGE
                        )
                    }
                }
        }
    }

    private suspend fun mutateAndRefresh(mutate: suspend () -> Unit) {
        _uiState.update { it.copy(isLoading = true, errorMessage = null) }
        runCatching { mutate() }
            .onFailure {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = SOURCE_CLOUD_UPDATE_FAILED_MESSAGE
                    )
                }
                return
            }

        runCatching { repository.status() }
            .onSuccess { status ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        status = status,
                        errorMessage = null
                    )
                }
            }
            .onFailure {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = SOURCE_CLOUD_UNAVAILABLE_MESSAGE
                    )
                }
            }
    }
}

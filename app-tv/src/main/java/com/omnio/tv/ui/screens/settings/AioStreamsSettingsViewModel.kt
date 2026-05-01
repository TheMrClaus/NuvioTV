package com.omnio.tv.ui.screens.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.omnio.tv.BuildConfig
import com.omnio.tv.R
import com.omnio.tv.data.remote.dto.aiostreams.AioStreamsDefaultConfig
import com.omnio.tv.domain.model.AioStreamsConfigInnerDto
import com.omnio.tv.domain.profile.ProfileManager
import com.omnio.tv.domain.repository.AioStreamsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AioStreamsSettingsViewModel @Inject constructor(
    private val repository: AioStreamsRepository,
    private val profileManager: ProfileManager,
    @ApplicationContext private val appContext: Context,
) : ViewModel() {

    data class UiState(
        val isLoading: Boolean = false,
        val isMutating: Boolean = false,
        val enabled: Boolean = false,
        val uuid: String = "",
        val manifestUrl: String = "",
        val configPassword: String = "",
        val encryptedManifestPassword: String = "",
        val isPrimaryProfileBlocked: Boolean = false,
        val hasConfig: Boolean = false,
        val isMainProfile: Boolean = false,
        val canProvisionFromMain: Boolean = false,
        val canResetFromMain: Boolean = false,
        val isProvisioning: Boolean = false,
        val errorMessage: String? = null,
        val statusMessage: String? = null,
    ) {
        val manageUrl: String
            get() {
                if (uuid.isBlank() || encryptedManifestPassword.isBlank()) return ""
                val base = BuildConfig.AIOSTREAMS_BASE_URL.trimEnd('/')
                if (base.isBlank()) return ""
                return "$base/stremio/$uuid/$encryptedManifestPassword/configure"
            }

        val canEnable: Boolean
            get() = manifestUrl.isNotBlank() || hasConfig
    }

    private val _uiState = MutableStateFlow(UiState(isLoading = true))
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init {
        observeSettings()
        viewModelScope.launch { refresh() }
    }

    private fun observeSettings() {
        combine(
            repository.settings,
            profileManager.profiles,
            profileManager.activeProfileId,
        ) { settings, _, _ -> settings }
            .onEach { settings ->
                val profile = profileManager.activeProfile
                val primaryBlocked = profile?.usesPrimaryAddons == true
                val isMain = profile?.isPrimary == true
                val canProvision = !isMain && !primaryBlocked && settings.aioUuid.isBlank()
                val canReset = !isMain && !primaryBlocked && settings.aioUuid.isNotBlank()
                _uiState.update {
                    it.copy(
                        enabled = settings.enabled,
                        uuid = settings.aioUuid,
                        manifestUrl = settings.manifestUrl,
                        encryptedManifestPassword = settings.manifestUrl.extractEncryptedPassword(),
                        isPrimaryProfileBlocked = primaryBlocked,
                        hasConfig = settings.aioUuid.isNotBlank(),
                        isMainProfile = isMain,
                        canProvisionFromMain = canProvision,
                        canResetFromMain = canReset,
                    )
                }
            }
            .launchIn(viewModelScope)
    }

    fun onRefreshClick() {
        viewModelScope.launch { refresh() }
    }

    fun onToggleEnabled() {
        val current = _uiState.value
        if (current.isMutating || current.isPrimaryProfileBlocked) return

        val target = !current.enabled
        viewModelScope.launch {
            _uiState.update { it.copy(isMutating = true, errorMessage = null, statusMessage = null) }

            val manifest = if (target && current.uuid.isBlank()) {
                if (!current.isMainProfile) {
                    _uiState.update {
                        it.copy(
                            isMutating = false,
                            statusMessage = appContext.getString(R.string.aio_streams_use_provision_from_main)
                        )
                    }
                    return@launch
                }
                val created = repository.createConfig(AioStreamsDefaultConfig.build())
                val result = created.getOrNull()
                if (result == null) {
                    _uiState.update {
                        it.copy(
                            isMutating = false,
                            errorMessage = created.exceptionOrNull()?.message
                                ?: appContext.getString(R.string.aio_streams_create_failed)
                        )
                    }
                    return@launch
                }
                _uiState.update { it.copy(configPassword = repository.getConfigPassword().orEmpty()) }
                result.manifestUrl
            } else {
                current.manifestUrl
            }

            if (target && manifest.isBlank()) {
                _uiState.update {
                    it.copy(
                        isMutating = false,
                        errorMessage = appContext.getString(R.string.aio_streams_manifest_unavailable)
                    )
                }
                return@launch
            }

            val setResult = repository.setEnabled(target, manifest)
            _uiState.update {
                it.copy(
                    isMutating = false,
                    errorMessage = setResult.exceptionOrNull()?.message
                )
            }
            if (setResult.isSuccess) refresh()
        }
    }

    fun onProvisionFromMainClick() {
        val current = _uiState.value
        if (current.isProvisioning || !current.canProvisionFromMain) return
        runProvisionFromMain(reset = false)
    }

    fun onResetFromMainClick() {
        val current = _uiState.value
        if (current.isProvisioning || !current.canResetFromMain) return
        runProvisionFromMain(reset = true)
    }

    fun consumeError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    fun consumeStatus() {
        _uiState.update { it.copy(statusMessage = null) }
    }

    private fun runProvisionFromMain(reset: Boolean) {
        val profile = profileManager.activeProfile ?: return
        if (profile.isPrimary) return

        viewModelScope.launch {
            _uiState.update { it.copy(isProvisioning = true, errorMessage = null, statusMessage = null) }

            if (reset) {
                val uuid = _uiState.value.uuid
                if (uuid.isNotBlank()) {
                    repository.deleteConfig(uuid).onFailure { error ->
                        _uiState.update {
                            it.copy(
                                isProvisioning = false,
                                errorMessage = error.message
                                    ?: appContext.getString(R.string.aio_streams_provision_unknown_reason)
                            )
                        }
                        return@launch
                    }
                }
            }

            val result = repository.provisionFromMain(
                targetProfileId = profile.id,
                kidsMaxAgeRating = if (profile.isKids) profile.maxAgeRating else null,
            )
            result
                .onSuccess {
                    refresh()
                    _uiState.update {
                        it.copy(
                            isProvisioning = false,
                            statusMessage = appContext.getString(
                                if (reset) R.string.aio_streams_reset_success
                                else R.string.aio_streams_provision_success
                            )
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            isProvisioning = false,
                            errorMessage = error.message
                                ?: appContext.getString(R.string.aio_streams_provision_unknown_reason)
                        )
                    }
                }
        }
    }

    private suspend fun refresh() {
        _uiState.update { it.copy(isLoading = true, errorMessage = null) }
        val password = repository.getConfigPassword()
        repository.refresh()
            .onSuccess { config ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        configPassword = password.orEmpty(),
                        hasConfig = config != null,
                    )
                }
            }
            .onFailure { error ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = error.message ?: appContext.getString(R.string.aio_streams_refresh_failed)
                    )
                }
            }
    }

    private fun String.extractEncryptedPassword(): String {
        if (isBlank()) return ""
        val trimmed = trim().trimEnd('/')
        val marker = "/stremio/"
        val start = trimmed.indexOf(marker)
        if (start == -1) return ""
        val parts = trimmed.substring(start + marker.length).split('/')
        return parts.getOrNull(1).orEmpty()
    }
}

@file:OptIn(ExperimentalTvMaterial3Api::class)

package com.omnio.tv.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.tv.material3.ExperimentalTvMaterial3Api
import com.omnio.tv.R
import com.omnio.tv.data.local.AVAILABLE_SUBTITLE_LANGUAGES
import com.omnio.tv.data.local.StreamPreferencesDataStore
import com.omnio.tv.data.local.displayName
import com.omnio.tv.domain.model.StreamPrefAudioTag
import com.omnio.tv.domain.model.StreamPrefCodec
import com.omnio.tv.domain.model.StreamPrefEncode
import com.omnio.tv.domain.model.StreamPrefMinQuality
import com.omnio.tv.domain.model.StreamPrefSortCriterion
import com.omnio.tv.domain.model.StreamPrefSortDirection
import com.omnio.tv.domain.model.StreamPrefSortKey
import com.omnio.tv.domain.model.StreamPrefVisualTag
import com.omnio.tv.domain.model.StreamPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class StreamPreferencesViewModel @Inject constructor(
    private val dataStore: StreamPreferencesDataStore
) : ViewModel() {

    private val _uiState = MutableStateFlow(StreamPreferencesUiState())
    val uiState: StateFlow<StreamPreferencesUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            dataStore.preferences.collectLatest { prefs ->
                _uiState.update { it.fromPreferences(prefs) }
            }
        }
    }

    fun onEvent(event: StreamPreferencesEvent) {
        when (event) {
            is StreamPreferencesEvent.ToggleEnabled ->
                update { it.copy(enabled = event.enabled) }
            is StreamPreferencesEvent.SetMinResolution ->
                update { it.copy(minResolution = event.value) }
            is StreamPreferencesEvent.SetRequiredVisualTags ->
                update { it.copy(requiredVisualTags = event.tags) }
            is StreamPreferencesEvent.SetExcludedVisualTags ->
                update { it.copy(excludedVisualTags = event.tags) }
            is StreamPreferencesEvent.SetRequiredAudioTags ->
                update { it.copy(requiredAudioTags = event.tags) }
            is StreamPreferencesEvent.SetExcludedAudioTags ->
                update { it.copy(excludedAudioTags = event.tags) }
            is StreamPreferencesEvent.SetRequiredCodecs ->
                update { it.copy(requiredCodecs = event.codecs) }
            is StreamPreferencesEvent.SetExcludedCodecs ->
                update { it.copy(excludedCodecs = event.codecs) }
            is StreamPreferencesEvent.SetRequiredEncodes ->
                update { it.copy(requiredEncodes = event.encodes) }
            is StreamPreferencesEvent.SetExcludedEncodes ->
                update { it.copy(excludedEncodes = event.encodes) }
            is StreamPreferencesEvent.SetRequiredLanguages ->
                update { it.copy(requiredLanguages = event.codes) }
            is StreamPreferencesEvent.SetExcludedLanguages ->
                update { it.copy(excludedLanguages = event.codes) }
            is StreamPreferencesEvent.ToggleRequireCached ->
                update { it.copy(requireCached = event.checked) }
            is StreamPreferencesEvent.SetSortCriteria ->
                update { it.copy(sortCriteria = event.criteria) }
            is StreamPreferencesEvent.SetPreloadCount ->
                update { it.copy(preloadCount = event.count) }
        }
    }

    private fun update(transform: (StreamPreferences) -> StreamPreferences) {
        viewModelScope.launch {
            dataStore.update(transform)
        }
    }
}

data class StreamPreferencesUiState(
    val enabled: Boolean = false,
    val minResolution: StreamPrefMinQuality = StreamPrefMinQuality.NONE,
    val requiredVisualTags: Set<StreamPrefVisualTag> = emptySet(),
    val excludedVisualTags: Set<StreamPrefVisualTag> = emptySet(),
    val requiredAudioTags: Set<StreamPrefAudioTag> = emptySet(),
    val excludedAudioTags: Set<StreamPrefAudioTag> = emptySet(),
    val requiredCodecs: Set<StreamPrefCodec> = emptySet(),
    val excludedCodecs: Set<StreamPrefCodec> = emptySet(),
    val requiredEncodes: Set<StreamPrefEncode> = emptySet(),
    val excludedEncodes: Set<StreamPrefEncode> = emptySet(),
    val requiredLanguages: Set<String> = emptySet(),
    val excludedLanguages: Set<String> = emptySet(),
    val requireCached: Boolean = false,
    val sortCriteria: List<StreamPrefSortCriterion> = StreamPrefSortCriterion.defaultOrder,
    val preloadCount: Int = 0
) {
    fun fromPreferences(prefs: StreamPreferences): StreamPreferencesUiState = copy(
        enabled = prefs.enabled,
        minResolution = prefs.minResolution,
        requiredVisualTags = prefs.requiredVisualTags,
        excludedVisualTags = prefs.excludedVisualTags,
        requiredAudioTags = prefs.requiredAudioTags,
        excludedAudioTags = prefs.excludedAudioTags,
        requiredCodecs = prefs.requiredCodecs,
        excludedCodecs = prefs.excludedCodecs,
        requiredEncodes = prefs.requiredEncodes,
        excludedEncodes = prefs.excludedEncodes,
        requiredLanguages = prefs.requiredLanguages,
        excludedLanguages = prefs.excludedLanguages,
        requireCached = prefs.requireCached,
        sortCriteria = prefs.sortCriteria,
        preloadCount = prefs.preloadCount
    )
}

sealed class StreamPreferencesEvent {
    data class ToggleEnabled(val enabled: Boolean) : StreamPreferencesEvent()
    data class SetMinResolution(val value: StreamPrefMinQuality) : StreamPreferencesEvent()
    data class SetRequiredVisualTags(val tags: Set<StreamPrefVisualTag>) : StreamPreferencesEvent()
    data class SetExcludedVisualTags(val tags: Set<StreamPrefVisualTag>) : StreamPreferencesEvent()
    data class SetRequiredAudioTags(val tags: Set<StreamPrefAudioTag>) : StreamPreferencesEvent()
    data class SetExcludedAudioTags(val tags: Set<StreamPrefAudioTag>) : StreamPreferencesEvent()
    data class SetRequiredCodecs(val codecs: Set<StreamPrefCodec>) : StreamPreferencesEvent()
    data class SetExcludedCodecs(val codecs: Set<StreamPrefCodec>) : StreamPreferencesEvent()
    data class SetRequiredEncodes(val encodes: Set<StreamPrefEncode>) : StreamPreferencesEvent()
    data class SetExcludedEncodes(val encodes: Set<StreamPrefEncode>) : StreamPreferencesEvent()
    data class SetRequiredLanguages(val codes: Set<String>) : StreamPreferencesEvent()
    data class SetExcludedLanguages(val codes: Set<String>) : StreamPreferencesEvent()
    data class ToggleRequireCached(val checked: Boolean) : StreamPreferencesEvent()
    data class SetSortCriteria(val criteria: List<StreamPrefSortCriterion>) : StreamPreferencesEvent()
    data class SetPreloadCount(val count: Int) : StreamPreferencesEvent()
}

@Composable
fun StreamPreferencesSettingsContent(
    viewModel: StreamPreferencesViewModel = hiltViewModel(),
    initialFocusRequester: FocusRequester? = null
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showMinQualityDialog by remember { mutableStateOf(false) }
    var showRequiredCodecDialog by remember { mutableStateOf(false) }
    var showExcludedCodecDialog by remember { mutableStateOf(false) }
    var showRequiredVisualDialog by remember { mutableStateOf(false) }
    var showExcludedVisualDialog by remember { mutableStateOf(false) }
    var showRequiredAudioDialog by remember { mutableStateOf(false) }
    var showExcludedAudioDialog by remember { mutableStateOf(false) }
    var showRequiredEncodeDialog by remember { mutableStateOf(false) }
    var showExcludedEncodeDialog by remember { mutableStateOf(false) }
    var showRequiredLanguageDialog by remember { mutableStateOf(false) }
    var showExcludedLanguageDialog by remember { mutableStateOf(false) }
    var showPreloadDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        SettingsDetailHeader(
            title = stringResource(R.string.settings_stream_prefs),
            subtitle = stringResource(R.string.settings_stream_prefs_subtitle)
        )

        SettingsGroupCard(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            LazyColumn(
                contentPadding = PaddingValues(bottom = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item(key = "stream_prefs_enabled") {
                    SettingsToggleRow(
                        title = stringResource(R.string.stream_prefs_enable_title),
                        subtitle = stringResource(R.string.stream_prefs_enable_subtitle),
                        checked = uiState.enabled,
                        onToggle = { viewModel.onEvent(StreamPreferencesEvent.ToggleEnabled(!uiState.enabled)) },
                        modifier = if (initialFocusRequester != null) {
                            Modifier.focusRequester(initialFocusRequester)
                        } else {
                            Modifier
                        }
                    )
                }

                item(key = "stream_prefs_min_quality") {
                    val label = uiState.minResolution.let {
                        if (it == StreamPrefMinQuality.NONE) "Any" else it.name
                    }
                    SettingsActionRow(
                        title = stringResource(R.string.stream_prefs_min_quality_title),
                        subtitle = stringResource(R.string.stream_prefs_min_quality_subtitle),
                        value = label,
                        enabled = uiState.enabled,
                        onClick = { showMinQualityDialog = true }
                    )
                }

                item(key = "stream_prefs_codec_required") {
                    val label = uiState.requiredCodecs.takeIf { it.isNotEmpty() }
                        ?.joinToString(", ") { it.label } ?: "Any"
                    SettingsActionRow(
                        title = stringResource(R.string.stream_prefs_codec_required_title),
                        subtitle = stringResource(R.string.stream_prefs_codec_required_subtitle),
                        value = label,
                        enabled = uiState.enabled,
                        onClick = { showRequiredCodecDialog = true }
                    )
                }

                item(key = "stream_prefs_codec_excluded") {
                    val label = uiState.excludedCodecs.takeIf { it.isNotEmpty() }
                        ?.joinToString(", ") { it.label } ?: "None"
                    SettingsActionRow(
                        title = stringResource(R.string.stream_prefs_codec_excluded_title),
                        subtitle = stringResource(R.string.stream_prefs_codec_excluded_subtitle),
                        value = label,
                        enabled = uiState.enabled,
                        onClick = { showExcludedCodecDialog = true }
                    )
                }

                item(key = "stream_prefs_hdr_required") {
                    val label = uiState.requiredVisualTags.takeIf { it.isNotEmpty() }
                        ?.joinToString(", ") { it.label } ?: "Any"
                    SettingsActionRow(
                        title = stringResource(R.string.stream_prefs_hdr_required_title),
                        subtitle = stringResource(R.string.stream_prefs_hdr_required_subtitle),
                        value = label,
                        enabled = uiState.enabled,
                        onClick = { showRequiredVisualDialog = true }
                    )
                }

                item(key = "stream_prefs_hdr_excluded") {
                    val label = uiState.excludedVisualTags.takeIf { it.isNotEmpty() }
                        ?.joinToString(", ") { it.label } ?: "None"
                    SettingsActionRow(
                        title = stringResource(R.string.stream_prefs_hdr_excluded_title),
                        subtitle = stringResource(R.string.stream_prefs_hdr_excluded_subtitle),
                        value = label,
                        enabled = uiState.enabled,
                        onClick = { showExcludedVisualDialog = true }
                    )
                }

                item(key = "stream_prefs_audio_required") {
                    val label = uiState.requiredAudioTags.takeIf { it.isNotEmpty() }
                        ?.joinToString(", ") { it.label } ?: "Any"
                    SettingsActionRow(
                        title = stringResource(R.string.stream_prefs_audio_required_title),
                        subtitle = stringResource(R.string.stream_prefs_audio_required_subtitle),
                        value = label,
                        enabled = uiState.enabled,
                        onClick = { showRequiredAudioDialog = true }
                    )
                }

                item(key = "stream_prefs_audio_excluded") {
                    val label = uiState.excludedAudioTags.takeIf { it.isNotEmpty() }
                        ?.joinToString(", ") { it.label } ?: "None"
                    SettingsActionRow(
                        title = stringResource(R.string.stream_prefs_audio_excluded_title),
                        subtitle = stringResource(R.string.stream_prefs_audio_excluded_subtitle),
                        value = label,
                        enabled = uiState.enabled,
                        onClick = { showExcludedAudioDialog = true }
                    )
                }

                item(key = "stream_prefs_encode_required") {
                    val label = uiState.requiredEncodes.takeIf { it.isNotEmpty() }
                        ?.joinToString(", ") { it.label } ?: "Any"
                    SettingsActionRow(
                        title = stringResource(R.string.stream_prefs_encode_required_title),
                        subtitle = stringResource(R.string.stream_prefs_encode_required_subtitle),
                        value = label,
                        enabled = uiState.enabled,
                        onClick = { showRequiredEncodeDialog = true }
                    )
                }

                item(key = "stream_prefs_encode_excluded") {
                    val label = uiState.excludedEncodes.takeIf { it.isNotEmpty() }
                        ?.joinToString(", ") { it.label } ?: "None"
                    SettingsActionRow(
                        title = stringResource(R.string.stream_prefs_encode_excluded_title),
                        subtitle = stringResource(R.string.stream_prefs_encode_excluded_subtitle),
                        value = label,
                        enabled = uiState.enabled,
                        onClick = { showExcludedEncodeDialog = true }
                    )
                }

                item(key = "stream_prefs_language_required") {
                    val label = if (uiState.requiredLanguages.isEmpty()) "Any"
                    else uiState.requiredLanguages.joinToString(", ") { code ->
                        AVAILABLE_SUBTITLE_LANGUAGES.find { it.code == code }?.name ?: code
                    }
                    SettingsActionRow(
                        title = stringResource(R.string.stream_prefs_language_required_title),
                        subtitle = stringResource(R.string.stream_prefs_language_required_subtitle),
                        value = label,
                        enabled = uiState.enabled,
                        onClick = { showRequiredLanguageDialog = true }
                    )
                }

                item(key = "stream_prefs_language_excluded") {
                    val label = if (uiState.excludedLanguages.isEmpty()) "None"
                    else uiState.excludedLanguages.joinToString(", ") { code ->
                        AVAILABLE_SUBTITLE_LANGUAGES.find { it.code == code }?.name ?: code
                    }
                    SettingsActionRow(
                        title = stringResource(R.string.stream_prefs_language_excluded_title),
                        subtitle = stringResource(R.string.stream_prefs_language_excluded_subtitle),
                        value = label,
                        enabled = uiState.enabled,
                        onClick = { showExcludedLanguageDialog = true }
                    )
                }

                item(key = "stream_prefs_cached") {
                    SettingsToggleRow(
                        title = stringResource(R.string.stream_prefs_cached_title),
                        subtitle = stringResource(R.string.stream_prefs_cached_subtitle),
                        checked = uiState.requireCached,
                        enabled = uiState.enabled,
                        onToggle = { viewModel.onEvent(StreamPreferencesEvent.ToggleRequireCached(!uiState.requireCached)) }
                    )
                }

                item(key = "stream_prefs_preload") {
                    val label = when (uiState.preloadCount) {
                        0 -> "Off"
                        else -> "${uiState.preloadCount} stream(s)"
                    }
                    SettingsActionRow(
                        title = stringResource(R.string.stream_prefs_preload_title),
                        subtitle = stringResource(R.string.stream_prefs_preload_subtitle),
                        value = label,
                        enabled = uiState.enabled,
                        onClick = { showPreloadDialog = true }
                    )
                }
            }
        }
    }

    // ---- Dialogs ----

    if (showMinQualityDialog) {
        SettingsSingleChoiceDialog(
            title = stringResource(R.string.stream_prefs_min_quality_title),
            options = StreamPrefMinQuality.entries.map { q ->
                SettingsPickerOption(
                    value = q,
                    title = if (q == StreamPrefMinQuality.NONE) "Any" else q.name
                )
            },
            selected = uiState.minResolution,
            onSelected = {
                viewModel.onEvent(StreamPreferencesEvent.SetMinResolution(it))
            },
            onDismiss = { showMinQualityDialog = false }
        )
    }

    if (showRequiredCodecDialog) {
        SettingsMultiChoiceDialog(
            title = stringResource(R.string.stream_prefs_codec_required_title),
            options = StreamPrefCodec.entries.map { c ->
                SettingsPickerOption(value = c, title = c.label)
            },
            initiallySelected = uiState.requiredCodecs.toList(),
            onSave = {
                viewModel.onEvent(StreamPreferencesEvent.SetRequiredCodecs(it.toSet()))
            },
            onDismiss = { showRequiredCodecDialog = false },
            onClear = { viewModel.onEvent(StreamPreferencesEvent.SetRequiredCodecs(emptySet())) }
        )
    }

    if (showExcludedCodecDialog) {
        SettingsMultiChoiceDialog(
            title = stringResource(R.string.stream_prefs_codec_excluded_title),
            options = StreamPrefCodec.entries.map { c ->
                SettingsPickerOption(value = c, title = c.label)
            },
            initiallySelected = uiState.excludedCodecs.toList(),
            onSave = {
                viewModel.onEvent(StreamPreferencesEvent.SetExcludedCodecs(it.toSet()))
            },
            onDismiss = { showExcludedCodecDialog = false },
            onClear = { viewModel.onEvent(StreamPreferencesEvent.SetExcludedCodecs(emptySet())) }
        )
    }

    if (showRequiredVisualDialog) {
        SettingsMultiChoiceDialog(
            title = stringResource(R.string.stream_prefs_hdr_required_title),
            options = StreamPrefVisualTag.entries.map { v ->
                SettingsPickerOption(value = v, title = v.label)
            },
            initiallySelected = uiState.requiredVisualTags.toList(),
            onSave = {
                viewModel.onEvent(StreamPreferencesEvent.SetRequiredVisualTags(it.toSet()))
            },
            onDismiss = { showRequiredVisualDialog = false },
            onClear = { viewModel.onEvent(StreamPreferencesEvent.SetRequiredVisualTags(emptySet())) }
        )
    }

    if (showExcludedVisualDialog) {
        SettingsMultiChoiceDialog(
            title = stringResource(R.string.stream_prefs_hdr_excluded_title),
            options = StreamPrefVisualTag.entries.map { v ->
                SettingsPickerOption(value = v, title = v.label)
            },
            initiallySelected = uiState.excludedVisualTags.toList(),
            onSave = {
                viewModel.onEvent(StreamPreferencesEvent.SetExcludedVisualTags(it.toSet()))
            },
            onDismiss = { showExcludedVisualDialog = false },
            onClear = { viewModel.onEvent(StreamPreferencesEvent.SetExcludedVisualTags(emptySet())) }
        )
    }

    if (showRequiredAudioDialog) {
        SettingsMultiChoiceDialog(
            title = stringResource(R.string.stream_prefs_audio_required_title),
            options = StreamPrefAudioTag.entries.map { a ->
                SettingsPickerOption(value = a, title = a.label)
            },
            initiallySelected = uiState.requiredAudioTags.toList(),
            onSave = {
                viewModel.onEvent(StreamPreferencesEvent.SetRequiredAudioTags(it.toSet()))
            },
            onDismiss = { showRequiredAudioDialog = false },
            onClear = { viewModel.onEvent(StreamPreferencesEvent.SetRequiredAudioTags(emptySet())) }
        )
    }

    if (showExcludedAudioDialog) {
        SettingsMultiChoiceDialog(
            title = stringResource(R.string.stream_prefs_audio_excluded_title),
            options = StreamPrefAudioTag.entries.map { a ->
                SettingsPickerOption(value = a, title = a.label)
            },
            initiallySelected = uiState.excludedAudioTags.toList(),
            onSave = {
                viewModel.onEvent(StreamPreferencesEvent.SetExcludedAudioTags(it.toSet()))
            },
            onDismiss = { showExcludedAudioDialog = false },
            onClear = { viewModel.onEvent(StreamPreferencesEvent.SetExcludedAudioTags(emptySet())) }
        )
    }

    if (showRequiredEncodeDialog) {
        SettingsMultiChoiceDialog(
            title = stringResource(R.string.stream_prefs_encode_required_title),
            options = StreamPrefEncode.entries.map { e ->
                SettingsPickerOption(value = e, title = e.label)
            },
            initiallySelected = uiState.requiredEncodes.toList(),
            onSave = {
                viewModel.onEvent(StreamPreferencesEvent.SetRequiredEncodes(it.toSet()))
            },
            onDismiss = { showRequiredEncodeDialog = false },
            onClear = { viewModel.onEvent(StreamPreferencesEvent.SetRequiredEncodes(emptySet())) }
        )
    }

    if (showExcludedEncodeDialog) {
        SettingsMultiChoiceDialog(
            title = stringResource(R.string.stream_prefs_encode_excluded_title),
            options = StreamPrefEncode.entries.map { e ->
                SettingsPickerOption(value = e, title = e.label)
            },
            initiallySelected = uiState.excludedEncodes.toList(),
            onSave = {
                viewModel.onEvent(StreamPreferencesEvent.SetExcludedEncodes(it.toSet()))
            },
            onDismiss = { showExcludedEncodeDialog = false },
            onClear = { viewModel.onEvent(StreamPreferencesEvent.SetExcludedEncodes(emptySet())) }
        )
    }

    if (showRequiredLanguageDialog) {
        SettingsMultiChoiceDialog(
            title = stringResource(R.string.stream_prefs_language_required_title),
            options = AVAILABLE_SUBTITLE_LANGUAGES.map { lang ->
                SettingsPickerOption(value = lang.code, title = lang.name)
            },
            initiallySelected = uiState.requiredLanguages.toList(),
            onSave = {
                viewModel.onEvent(StreamPreferencesEvent.SetRequiredLanguages(it.toSet()))
            },
            onDismiss = { showRequiredLanguageDialog = false },
            onClear = { viewModel.onEvent(StreamPreferencesEvent.SetRequiredLanguages(emptySet())) }
        )
    }

    if (showExcludedLanguageDialog) {
        SettingsMultiChoiceDialog(
            title = stringResource(R.string.stream_prefs_language_excluded_title),
            options = AVAILABLE_SUBTITLE_LANGUAGES.map { lang ->
                SettingsPickerOption(value = lang.code, title = lang.name)
            },
            initiallySelected = uiState.excludedLanguages.toList(),
            onSave = {
                viewModel.onEvent(StreamPreferencesEvent.SetExcludedLanguages(it.toSet()))
            },
            onDismiss = { showExcludedLanguageDialog = false },
            onClear = { viewModel.onEvent(StreamPreferencesEvent.SetExcludedLanguages(emptySet())) }
        )
    }

    if (showPreloadDialog) {
        SettingsSingleChoiceDialog(
            title = stringResource(R.string.stream_prefs_preload_title),
            options = listOf(0, 1, 2, 3, 5).map { count ->
                SettingsPickerOption(
                    value = count,
                    title = if (count == 0) "Off" else "$count stream(s)"
                )
            },
            selected = uiState.preloadCount,
            onSelected = {
                viewModel.onEvent(StreamPreferencesEvent.SetPreloadCount(it))
            },
            onDismiss = { showPreloadDialog = false }
        )
    }
}

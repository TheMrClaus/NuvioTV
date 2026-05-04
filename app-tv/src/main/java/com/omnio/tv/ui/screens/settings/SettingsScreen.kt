@file:OptIn(ExperimentalTvMaterial3Api::class)

package com.omnio.tv.ui.screens.settings

import androidx.activity.compose.BackHandler
import androidx.annotation.RawRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil.compose.rememberAsyncImagePainter
import coil.decode.SvgDecoder
import coil.request.ImageRequest
import com.omnio.tv.BuildConfig
import com.omnio.tv.R
import com.omnio.tv.domain.profile.ProfileManager
import com.omnio.tv.ui.screens.plugin.PluginScreenContent
import com.omnio.tv.core.uishared.OmnioColors
import com.omnio.tv.ui.components.cinematic.cinematicFocus
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.delay

internal enum class SettingsCategory {
    ACCOUNT,
    PROFILES,
    APPEARANCE,
    LAYOUT,
    COLLECTIONS,
    PLUGINS,
    INTEGRATION,
    PLAYBACK,
    ADVANCED,
    TRAKT,
    ABOUT,
    DEBUG
}

private enum class IntegrationSettingsSection {
    Hub,
    Emby,
    Tmdb,
    MdbList,
    AnimeSkip,
    AioMetadata
}

internal enum class SettingsSectionDestination {
    Inline,
    External
}

internal data class SettingsSectionSpec(
    val category: SettingsCategory,
    val title: String,
    val icon: ImageVector? = null,
    @param:RawRes val rawIconRes: Int? = null,
    val subtitle: String,
    val destination: SettingsSectionDestination
)

private const val SETTINGS_DETAIL_FOCUS_DELAY_MS = 120L
private const val SETTINGS_DETAIL_ANIM_IN_DURATION_MS = 200
private const val SETTINGS_DETAIL_ANIM_OUT_DURATION_MS = 180

@EntryPoint
@InstallIn(SingletonComponent::class)
internal interface SettingsScreenEntryPoint {
    fun profileManager(): ProfileManager
}

@Composable
private fun rememberSettingsSectionSpecs() = listOf(
    SettingsSectionSpec(
        category = SettingsCategory.ACCOUNT,
        title = stringResource(R.string.settings_account),
        icon = Icons.Default.Person,
        subtitle = stringResource(R.string.settings_account_subtitle),
        destination = SettingsSectionDestination.Inline
    ),
    SettingsSectionSpec(
        category = SettingsCategory.PROFILES,
        title = stringResource(R.string.settings_profiles),
        icon = Icons.Default.People,
        subtitle = stringResource(R.string.settings_profiles_subtitle),
        destination = SettingsSectionDestination.Inline
    ),
    SettingsSectionSpec(
        category = SettingsCategory.APPEARANCE,
        title = stringResource(R.string.appearance_title),
        icon = Icons.Default.Palette,
        subtitle = stringResource(R.string.appearance_subtitle),
        destination = SettingsSectionDestination.Inline
    ),
    SettingsSectionSpec(
        category = SettingsCategory.LAYOUT,
        title = stringResource(R.string.settings_layout),
        icon = Icons.Default.GridView,
        subtitle = stringResource(R.string.settings_layout_subtitle),
        destination = SettingsSectionDestination.Inline
    ),
    SettingsSectionSpec(
        category = SettingsCategory.COLLECTIONS,
        title = stringResource(R.string.collections_card_title),
        icon = Icons.Default.GridView,
        subtitle = stringResource(R.string.collections_card_subtitle),
        destination = SettingsSectionDestination.External
    ),
    SettingsSectionSpec(
        category = SettingsCategory.PLUGINS,
        title = stringResource(R.string.settings_plugins),
        icon = Icons.Default.Build,
        subtitle = stringResource(R.string.settings_plugins_subtitle),
        destination = SettingsSectionDestination.Inline
    ),
    SettingsSectionSpec(
        category = SettingsCategory.INTEGRATION,
        title = stringResource(R.string.settings_integration),
        icon = Icons.Default.Link,
        subtitle = "",
        destination = SettingsSectionDestination.Inline
    ),
    SettingsSectionSpec(
        category = SettingsCategory.PLAYBACK,
        title = stringResource(R.string.settings_playback),
        icon = Icons.Default.Settings,
        subtitle = stringResource(R.string.settings_playback_subtitle),
        destination = SettingsSectionDestination.Inline
    ),
    SettingsSectionSpec(
        category = SettingsCategory.TRAKT,
        title = "Trakt",
        rawIconRes = R.raw.trakt_tv_glyph,
        subtitle = stringResource(R.string.settings_trakt_subtitle),
        destination = SettingsSectionDestination.External
    ),
    SettingsSectionSpec(
        category = SettingsCategory.ABOUT,
        title = stringResource(R.string.about_title),
        icon = Icons.Default.Info,
        subtitle = stringResource(R.string.settings_about_subtitle),
        destination = SettingsSectionDestination.Inline
    ),
    SettingsSectionSpec(
        category = SettingsCategory.ADVANCED,
        title = stringResource(R.string.settings_advanced),
        icon = Icons.Default.Build,
        subtitle = stringResource(R.string.settings_advanced_subtitle),
        destination = SettingsSectionDestination.Inline
    ),
    SettingsSectionSpec(
        category = SettingsCategory.DEBUG,
        title = stringResource(R.string.settings_debug),
        icon = Icons.Default.BugReport,
        subtitle = stringResource(R.string.settings_debug_subtitle),
        destination = SettingsSectionDestination.Inline
    )
)

@Composable
fun SettingsScreen(
    showBuiltInHeader: Boolean = true,
    onNavigateToTrakt: () -> Unit = {},
    onNavigateToAuthQrSignIn: () -> Unit = {},
    onNavigateToManageProfiles: () -> Unit = {},
    onNavigateToSupportersContributors: () -> Unit = {},
    onNavigateToCollections: () -> Unit = {},
    profileViewModel: ProfileSettingsViewModel = hiltViewModel()
) {
    val isPrimaryProfileActive by profileViewModel.isPrimaryProfileActive.collectAsStateWithLifecycle()
    val profiles by profileViewModel.profiles.collectAsStateWithLifecycle()
    val profileManager = rememberSettingsProfileManager()
    val activeProfileId by profileManager.activeProfileId.collectAsStateWithLifecycle()
    val activeProfileName = remember(activeProfileId, profiles) {
        profiles.firstOrNull { it.id == activeProfileId }?.name ?: "Primary"
    }

    val allSectionSpecs = rememberSettingsSectionSpecs()
    val visibleSections = remember(isPrimaryProfileActive, allSectionSpecs) {
        allSectionSpecs.filter { section ->
            when (section.category) {
                SettingsCategory.DEBUG -> BuildConfig.IS_DEBUG_BUILD
                SettingsCategory.PROFILES -> isPrimaryProfileActive
                SettingsCategory.ACCOUNT -> isPrimaryProfileActive
                else -> true
            }
        }
    }

    val isRtl = androidx.compose.ui.platform.LocalLayoutDirection.current == androidx.compose.ui.unit.LayoutDirection.Rtl
    var selectedCategory by remember(visibleSections) {
        mutableStateOf(
            visibleSections.firstOrNull()?.category ?: SettingsCategory.APPEARANCE
        )
    }
    val railFocusRequesters = remember(visibleSections) {
        visibleSections.associate { it.category to FocusRequester() }
    }
    val contentFocusRequesters = remember {
            mapOf(
                SettingsCategory.APPEARANCE to FocusRequester(),
                SettingsCategory.LAYOUT to FocusRequester(),
                SettingsCategory.INTEGRATION to FocusRequester(),
                SettingsCategory.PLAYBACK to FocusRequester(),
                SettingsCategory.ADVANCED to FocusRequester(),
                SettingsCategory.ABOUT to FocusRequester()
            )
    }
    val railContainerFocusRequester = remember { FocusRequester() }
    val integrationHubFocusRequester = remember { FocusRequester() }
    val integrationEmbyFocusRequester = remember { FocusRequester() }
    val integrationTmdbFocusRequester = remember { FocusRequester() }
    val integrationMdbListFocusRequester = remember { FocusRequester() }
    val integrationAnimeSkipFocusRequester = remember { FocusRequester() }
    val integrationAioMetadataFocusRequester = remember { FocusRequester() }
    var integrationSection by remember { mutableStateOf(IntegrationSettingsSection.Hub) }
    var pendingContentFocusCategory by remember { mutableStateOf<SettingsCategory?>(null) }
    var pendingContentFocusRequestId by remember { mutableLongStateOf(0L) }
    var allowDetailAutofocus by remember { mutableStateOf(false) }

    val focusManager = LocalFocusManager.current

    LaunchedEffect(visibleSections) {
        if (visibleSections.none { it.category == selectedCategory }) {
            selectedCategory = visibleSections.firstOrNull()?.category ?: SettingsCategory.APPEARANCE
        }
    }

    LaunchedEffect(Unit) {
        runCatching { railContainerFocusRequester.requestFocus() }
    }

    LaunchedEffect(pendingContentFocusRequestId) {
        val category = pendingContentFocusCategory ?: return@LaunchedEffect
        delay(SETTINGS_DETAIL_FOCUS_DELAY_MS)
        val requester = contentFocusRequesters[category]
        val requested = if (requester != null) {
            runCatching { requester.requestFocus() }.isSuccess
        } else {
            false
        }
        if (!requested) {
            focusManager.moveFocus(FocusDirection.Right)
        }
        pendingContentFocusCategory = null
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(
                start = 32.dp,
                end = 32.dp,
                top = if (showBuiltInHeader) 24.dp else 68.dp,
                bottom = 24.dp
            )
    ) {
        SettingsWorkspaceSurface(
            modifier = Modifier
                .fillMaxSize()
        ) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                if (showBuiltInHeader) {
                    SettingsTopHeader(
                        profileName = activeProfileName,
                        versionName = BuildConfig.VERSION_NAME,
                        buildNumber = BuildConfig.VERSION_CODE.toString(),
                        bandwidthLabel = null
                    )

                    Spacer(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 18.dp, bottom = 18.dp)
                            .height(1.dp)
                            .background(Color.White.copy(alpha = 0.06f))
                    )
                }

                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    var railHadFocus by remember { mutableStateOf(false) }

                    LazyColumn(
                        modifier = Modifier
                            .focusRequester(railContainerFocusRequester)
                            .width(280.dp)
                            .fillMaxHeight()
                            .onFocusChanged { state ->
                                val justGainedFocus = !railHadFocus && state.hasFocus
                                railHadFocus = state.hasFocus
                                if (justGainedFocus) {
                                    val requester = railFocusRequesters[selectedCategory]
                                    val requested = if (requester != null) {
                                        runCatching { requester.requestFocus() }.isSuccess
                                    } else {
                                        false
                                    }
                                    if (!requested) {
                                        focusManager.moveFocus(FocusDirection.Down)
                                    }
                                }
                            }
                            .onPreviewKeyEvent { event ->
                                val toDetailKey = if (isRtl) Key.DirectionLeft else Key.DirectionRight
                                if (event.type == KeyEventType.KeyDown && event.key == toDetailKey) {
                                    allowDetailAutofocus = true
                                    false
                                } else {
                                    false
                                }
                            },
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(
                            items = visibleSections,
                            key = { it.category }
                        ) { section ->
                            SettingsSectionListItem(
                                title = section.title,
                                icon = section.icon,
                                rawIconRes = section.rawIconRes,
                                isSelected = selectedCategory == section.category,
                                focusRequester = railFocusRequesters[section.category],
                                countLabel = settingsSectionCountLabel(
                                    category = section.category,
                                    profileCount = profiles.size
                                ),
                                onClick = {
                                    if (section.destination == SettingsSectionDestination.External) {
                                        when (section.category) {
                                            SettingsCategory.ACCOUNT -> onNavigateToAuthQrSignIn()
                                            SettingsCategory.TRAKT -> onNavigateToTrakt()
                                            SettingsCategory.COLLECTIONS -> onNavigateToCollections()
                                            else -> Unit
                                        }
                                    } else {
                                        if (section.category == SettingsCategory.INTEGRATION) {
                                            integrationSection = IntegrationSettingsSection.Hub
                                        }
                                        allowDetailAutofocus = true
                                        selectedCategory = section.category
                                        pendingContentFocusCategory = section.category
                                        pendingContentFocusRequestId += 1L
                                    }
                                }
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .onKeyEvent { event ->
                                val toRailKey = if (isRtl) Key.DirectionRight else Key.DirectionLeft
                                if (event.type == KeyEventType.KeyDown && event.key == toRailKey) {
                                    val movedLeft = focusManager.moveFocus(if (isRtl) FocusDirection.Right else FocusDirection.Left)
                                    if (!movedLeft) {
                                        allowDetailAutofocus = false
                                        val requested = railFocusRequesters[selectedCategory]?.let { requester ->
                                            runCatching { requester.requestFocus() }.isSuccess
                                        } ?: false
                                        if (!requested) {
                                            runCatching { railContainerFocusRequester.requestFocus() }
                                        }
                                    }
                                    true
                                } else {
                                    false
                                }
                            }
                            .onFocusChanged { state ->
                                if (state.hasFocus && !allowDetailAutofocus) {
                                    railFocusRequesters[selectedCategory]?.let { requester ->
                                        runCatching { requester.requestFocus() }
                                    }
                                }
                            }
                    ) {
                        when (selectedCategory) {
                        SettingsCategory.PROFILES -> ProfileSettingsContent(
                            onManageProfiles = onNavigateToManageProfiles
                        )
                        SettingsCategory.APPEARANCE -> ThemeSettingsContent(
                            initialFocusRequester = if (allowDetailAutofocus) {
                                contentFocusRequesters[SettingsCategory.APPEARANCE]
                            } else {
                                null
                            }
                        )
                        SettingsCategory.LAYOUT -> LayoutSettingsContent(
                            initialFocusRequester = if (allowDetailAutofocus) {
                                contentFocusRequesters[SettingsCategory.LAYOUT]
                            } else {
                                null
                            }
                        )
                        SettingsCategory.PLAYBACK -> PlaybackSettingsContent(
                            initialFocusRequester = if (allowDetailAutofocus) {
                                contentFocusRequesters[SettingsCategory.PLAYBACK]
                            } else {
                                null
                            }
                        )
                        SettingsCategory.ADVANCED -> NetworkSettingsContent(
                            initialFocusRequester = if (allowDetailAutofocus) {
                                contentFocusRequesters[SettingsCategory.ADVANCED]
                            } else {
                                null
                            }
                        )
                        SettingsCategory.INTEGRATION -> IntegrationSettingsContent(
                            selectedSection = integrationSection,
                            onSelectSection = { integrationSection = it },
                            initialFocusRequester = if (allowDetailAutofocus) {
                                contentFocusRequesters[SettingsCategory.INTEGRATION]
                            } else {
                                null
                            },
                            hubFocusRequester = integrationHubFocusRequester,
                            embyFocusRequester = integrationEmbyFocusRequester,
                            tmdbFocusRequester = integrationTmdbFocusRequester,
                            mdbListFocusRequester = integrationMdbListFocusRequester,
                            animeSkipFocusRequester = integrationAnimeSkipFocusRequester,
                            aioMetadataFocusRequester = integrationAioMetadataFocusRequester,
                            autoFocusEnabled = allowDetailAutofocus
                        )
                        SettingsCategory.ABOUT -> AboutSettingsContent(
                            onNavigateToSupportersContributors = onNavigateToSupportersContributors,
                            initialFocusRequester = if (allowDetailAutofocus) {
                                contentFocusRequesters[SettingsCategory.ABOUT]
                            } else {
                                null
                            }
                        )
                        SettingsCategory.PLUGINS -> PluginsSettingsContent()
                        SettingsCategory.ACCOUNT -> AccountSettingsInline(
                            onNavigateToAuthQrSignIn = onNavigateToAuthQrSignIn
                        )
                        SettingsCategory.DEBUG -> DebugSettingsContent()
                        SettingsCategory.TRAKT -> Unit
                        SettingsCategory.COLLECTIONS -> Unit
                    }
                }
            }
        }
    }
}

}


@Composable
private fun SettingsTopHeader(
    profileName: String,
    versionName: String,
    buildNumber: String,
    bandwidthLabel: String?
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(28.dp)
        ) {
            Row(
                modifier = Modifier
                    .alpha(0.92f)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color.White.copy(alpha = 0.06f))
                    .border(
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.10f)),
                        shape = RoundedCornerShape(6.dp)
                    )
                    .padding(horizontal = 18.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
                Text(
                    text = stringResource(R.string.cd_back),
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(28.dp)
                )
                Text(
                    text = stringResource(R.string.nav_settings),
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = (-0.02f).em,
                    color = Color.White
                )
            }
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            settingsHeaderMetaSegments(
                profileName = profileName,
                versionName = versionName,
                buildNumber = buildNumber,
                bandwidthLabel = bandwidthLabel
            ).forEachIndexed { index, segment ->
                if (index > 0) {
                    Text(
                        text = "·",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.45f),
                        fontFamily = FontFamily.Monospace
                    )
                }
                Text(
                    text = if (segment == bandwidthLabel) "📶 $segment" else segment,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.72f),
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun SettingsSectionListItem(
    title: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
    icon: ImageVector? = null,
    rawIconRes: Int? = null,
    countLabel: String? = null
) {
    var isFocused by remember { mutableStateOf(false) }
    val appliedModifier = if (focusRequester != null) {
        modifier.focusRequester(focusRequester)
    } else {
        modifier
    }
    val itemTextColor = if (isSelected) {
        Color.White
    } else {
        Color.White.copy(alpha = 0.72f)
    }

    Card(
        onClick = onClick,
        modifier = appliedModifier
            .fillMaxWidth()
            .cinematicFocus(focused = isFocused, cornerRadius = 6.dp, scale = false)
            .onFocusChanged { isFocused = it.isFocused },
        colors = CardDefaults.colors(
            containerColor = if (isSelected) Color(0x1FE50914) else Color.Transparent,
            focusedContainerColor = if (isSelected) Color(0x1FE50914) else Color.Transparent
        ),
        border = CardDefaults.border(border = androidx.tv.material3.Border.None, focusedBorder = androidx.tv.material3.Border.None),
        shape = CardDefaults.shape(RoundedCornerShape(6.dp)),
        scale = CardDefaults.scale(focusedScale = 1f, pressedScale = 1f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 68.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .fillMaxHeight()
                    .background(if (isSelected) OmnioColors.Secondary else Color.Transparent)
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                SettingsSectionIcon(
                    icon = icon,
                    rawIconRes = rawIconRes,
                    tint = itemTextColor
                )
                Text(
                    text = title,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = itemTextColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (countLabel != null) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color.White.copy(alpha = 0.06f))
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = countLabel,
                            style = MaterialTheme.typography.labelMedium,
                            fontFamily = FontFamily.Monospace,
                            color = Color.White.copy(alpha = 0.72f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsSectionIcon(
    icon: ImageVector?,
    @RawRes rawIconRes: Int?,
    tint: Color
) {
    when {
        rawIconRes != null -> {
            androidx.compose.foundation.Image(
                painter = rememberRawSvgPainter(rawIconRes),
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(tint)
            )
        }
        icon != null -> {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(18.dp)
            )
        }
        else -> Spacer(modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun rememberSettingsProfileManager(): ProfileManager {
    val context = LocalContext.current.applicationContext
    return remember(context) {
        EntryPointAccessors.fromApplication(context, SettingsScreenEntryPoint::class.java)
            .profileManager()
    }
}

@Composable
private fun rememberRawSvgPainter(@RawRes rawIconRes: Int): androidx.compose.ui.graphics.painter.Painter {
    val context = LocalContext.current
    val request = remember(rawIconRes, context) {
        ImageRequest.Builder(context)
            .data(rawIconRes)
            .decoderFactory(SvgDecoder.Factory())
            .crossfade(false)
            .build()
    }
    return rememberAsyncImagePainter(model = request)
}

internal fun settingsHeaderMetaSegments(
    profileName: String,
    versionName: String,
    buildNumber: String,
    bandwidthLabel: String?
): List<String> = buildList {
    add("Profile: ${profileName.ifBlank { "Primary" }}")
    add("v$versionName (build $buildNumber)")
    bandwidthLabel?.takeIf { it.isNotBlank() }?.let(::add)
}

internal fun settingsSectionCountLabel(
    category: SettingsCategory,
    profileCount: Int
): String? = when (category) {
    SettingsCategory.PROFILES -> profileCount.toString()
    else -> null
}

@Composable
private fun PluginsSettingsContent() {
    val pluginViewModel: com.omnio.tv.ui.screens.plugin.PluginViewModel = hiltViewModel()
    val pluginUiState by pluginViewModel.uiState.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        SettingsDetailHeader(
            title = stringResource(R.string.settings_plugins),
            subtitle = stringResource(R.string.settings_plugins_section_subtitle)
        )
        SettingsGroupCard(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.TopStart
            ) {
                PluginScreenContent(
                    uiState = pluginUiState,
                    viewModel = pluginViewModel,
                    showHeader = false
                )
            }
        }
    }
}

@Composable
private fun AccountSettingsInline(
    onNavigateToAuthQrSignIn: () -> Unit
) {
    val accountViewModel: com.omnio.tv.ui.screens.account.AccountViewModel = hiltViewModel()
    val accountUiState by accountViewModel.uiState.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        SettingsDetailHeader(
            title = stringResource(R.string.settings_account),
            subtitle = stringResource(R.string.settings_account_section_subtitle)
        )
        SettingsGroupCard(modifier = Modifier.fillMaxSize()) {
            com.omnio.tv.ui.screens.account.AccountSettingsContent(
                uiState = accountUiState,
                viewModel = accountViewModel,
                onNavigateToAuthQrSignIn = onNavigateToAuthQrSignIn
            )
        }
    }
}

@Composable
private fun IntegrationSettingsContent(
    selectedSection: IntegrationSettingsSection,
    onSelectSection: (IntegrationSettingsSection) -> Unit,
    initialFocusRequester: FocusRequester?,
    hubFocusRequester: FocusRequester,
    embyFocusRequester: FocusRequester,
    tmdbFocusRequester: FocusRequester,
    mdbListFocusRequester: FocusRequester,
    animeSkipFocusRequester: FocusRequester,
    aioMetadataFocusRequester: FocusRequester,
    autoFocusEnabled: Boolean
) {
    BackHandler(enabled = selectedSection != IntegrationSettingsSection.Hub) {
        onSelectSection(IntegrationSettingsSection.Hub)
    }
    val hubEntryFocusRequester = initialFocusRequester ?: hubFocusRequester

    LaunchedEffect(selectedSection, autoFocusEnabled) {
        if (!autoFocusEnabled) return@LaunchedEffect
        val requester = when (selectedSection) {
            IntegrationSettingsSection.Hub -> hubEntryFocusRequester
            IntegrationSettingsSection.Emby -> embyFocusRequester
            IntegrationSettingsSection.Tmdb -> tmdbFocusRequester
            IntegrationSettingsSection.MdbList -> mdbListFocusRequester
            IntegrationSettingsSection.AnimeSkip -> animeSkipFocusRequester
            IntegrationSettingsSection.AioMetadata -> aioMetadataFocusRequester
        }
        runCatching { requester.requestFocus() }
    }

    when (selectedSection) {
        IntegrationSettingsSection.Hub -> {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                SettingsDetailHeader(
                    title = stringResource(R.string.settings_integrations_section),
                    subtitle = stringResource(R.string.settings_integrations_section_subtitle)
                )

                SettingsGroupCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        item(key = "integration_hub_emby") {
                            SettingsActionRow(
                                title = stringResource(R.string.settings_emby_title),
                                subtitle = stringResource(R.string.settings_emby_subtitle),
                                onClick = { onSelectSection(IntegrationSettingsSection.Emby) },
                                modifier = Modifier.focusRequester(hubEntryFocusRequester)
                            )
                        }
                        item(key = "integration_hub_tmdb") {
                            SettingsActionRow(
                                title = "TMDB",
                                subtitle = stringResource(R.string.settings_tmdb_subtitle),
                                onClick = { onSelectSection(IntegrationSettingsSection.Tmdb) },
                                modifier = Modifier
                            )
                        }
                        item(key = "integration_hub_mdblist") {
                            SettingsActionRow(
                                title = "MDBList",
                                subtitle = stringResource(R.string.settings_mdblist_subtitle),
                                onClick = { onSelectSection(IntegrationSettingsSection.MdbList) }
                            )
                        }
                        item(key = "integration_hub_animeskip") {
                            SettingsActionRow(
                                title = "Anime-Skip",
                                subtitle = stringResource(R.string.settings_animeskip_subtitle),
                                onClick = { onSelectSection(IntegrationSettingsSection.AnimeSkip) }
                            )
                        }
                        item(key = "integration_hub_aio_metadata") {
                            SettingsActionRow(
                                title = stringResource(R.string.aio_metadata_title),
                                subtitle = stringResource(R.string.aio_metadata_subtitle),
                                onClick = { onSelectSection(IntegrationSettingsSection.AioMetadata) }
                            )
                        }
                    }
                }
            }
        }

        IntegrationSettingsSection.Emby -> {
            EmbySettingsContent(
                initialFocusRequester = embyFocusRequester
            )
        }

        IntegrationSettingsSection.Tmdb -> {
            TmdbSettingsContent(
                initialFocusRequester = tmdbFocusRequester
            )
        }

        IntegrationSettingsSection.MdbList -> {
            MDBListSettingsContent(
                initialFocusRequester = mdbListFocusRequester
            )
        }

        IntegrationSettingsSection.AnimeSkip -> {
            AnimeSkipSettingsContent(
                initialFocusRequester = animeSkipFocusRequester
            )
        }

        IntegrationSettingsSection.AioMetadata -> {
            AioMetadataSettingsContent(
                initialFocusRequester = aioMetadataFocusRequester
            )
        }
    }
}

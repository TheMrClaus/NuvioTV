@file:OptIn(ExperimentalTvMaterial3Api::class)

package com.omnio.tv.ui.screens.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.tv.material3.Border
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil.compose.rememberAsyncImagePainter
import coil.decode.SvgDecoder
import coil.request.ImageRequest
import com.omnio.tv.R
import com.omnio.tv.core.uishared.OmnioColors
import com.omnio.tv.ui.components.OmnioDialog

internal val SettingsContainerRadius = 28.dp
internal val SettingsPillRadius = 999.dp
internal val SettingsSecondaryCardRadius = 18.dp
internal val SettingsRailItemHeight = 56.dp
private val SettingsCinematicHeaderRed = Color(0xFFFF5A5F)
private val SettingsCinematicSurface = Color(0xFF12141A)
private val SettingsCinematicSurfaceFocused = Color(0xFF181B22)
private val SettingsCinematicPanel = Color(0xFF0F1117)

@Composable
internal fun SettingsStandaloneScaffold(
    title: String,
    subtitle: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp, vertical = 24.dp)
    ) {
        SettingsWorkspaceSurface(
            modifier = Modifier
                .fillMaxSize()
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                content()
            }
        }
    }
}

@Composable
internal fun SettingsBrandPanel(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    showBuiltInHeader: Boolean = true
) {
    val titleColor = if (showBuiltInHeader) OmnioColors.TextPrimary else Color.Transparent
    val subtitleColor = if (showBuiltInHeader) OmnioColors.TextSecondary else Color.Transparent

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(SettingsContainerRadius))
            .background(OmnioColors.BackgroundElevated)
            .border(
                width = 1.dp,
                color = OmnioColors.Border,
                shape = RoundedCornerShape(SettingsContainerRadius)
            )
            .padding(26.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.Start
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(OmnioColors.BackgroundCard),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = null,
                    tint = titleColor
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = stringResource(R.string.nav_settings),
                style = MaterialTheme.typography.titleLarge,
                color = titleColor
            )
        }

        Spacer(modifier = Modifier.height(26.dp))

        com.omnio.tv.ui.components.cinematic.OmnioWordmark(height = 56.dp)

        Spacer(modifier = Modifier.height(14.dp))

        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium,
            color = titleColor,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = subtitleColor,
            maxLines = 4,
            overflow = TextOverflow.Ellipsis
        )

        Spacer(modifier = Modifier.weight(1f))

        Text(
            text = stringResource(R.string.settings_rounded_ui),
            style = MaterialTheme.typography.labelMedium,
            letterSpacing = 1.2.sp,
            color = subtitleColor
        )
    }
}

@Composable
internal fun SettingsWorkspaceSurface(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(SettingsContainerRadius))
            .background(OmnioColors.BackgroundElevated)
            .border(
                width = 1.dp,
                color = OmnioColors.Border,
                shape = RoundedCornerShape(SettingsContainerRadius)
            )
            .padding(20.dp),
        content = content
    )
}

@Composable
internal fun SettingsRailButton(
    title: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
    onFocused: () -> Unit = {},
    icon: ImageVector? = null,
    rawIconRes: Int? = null
) {
    var isFocused by remember { mutableStateOf(false) }
    val appliedModifier = if (focusRequester != null) {
        modifier.focusRequester(focusRequester)
    } else {
        modifier
    }

    Card(
        onClick = onClick,
        modifier = appliedModifier
            .fillMaxWidth()
            .heightIn(min = SettingsRailItemHeight)
            .onFocusChanged { state ->
                val nowFocused = state.isFocused
                if (isFocused != nowFocused) {
                    isFocused = nowFocused
                    if (nowFocused) onFocused()
                }
            },
        colors = CardDefaults.colors(
            containerColor = if (isSelected) OmnioColors.BackgroundCard else OmnioColors.Background,
            focusedContainerColor = OmnioColors.BackgroundCard
        ),
        border = CardDefaults.border(
            border = if (isSelected) Border(
                border = BorderStroke(1.dp, OmnioColors.FocusRing),
                shape = RoundedCornerShape(SettingsPillRadius)
            ) else Border.None,
            focusedBorder = Border(
                border = BorderStroke(2.dp, OmnioColors.FocusRing),
                shape = RoundedCornerShape(SettingsPillRadius)
            )
        ),
        shape = CardDefaults.shape(RoundedCornerShape(SettingsPillRadius)),
        scale = CardDefaults.scale(focusedScale = 1f, pressedScale = 1f)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = SettingsRailItemHeight),
            contentAlignment = Alignment.CenterStart
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (rawIconRes != null) {
                        Image(
                            painter = rememberRawSvgPainter(rawIconRes),
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            contentScale = ContentScale.Fit,
                            colorFilter = ColorFilter.tint(
                                if (isSelected || isFocused) OmnioColors.TextPrimary else OmnioColors.TextSecondary
                            )
                        )
                    } else if (icon != null) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = if (isSelected || isFocused) OmnioColors.TextPrimary else OmnioColors.TextSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    if (rawIconRes != null || icon != null) {
                        Spacer(modifier = Modifier.width(10.dp))
                    }

                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = if (isSelected || isFocused) FontWeight.SemiBold else FontWeight.Medium,
                        color = if (isSelected || isFocused) OmnioColors.TextPrimary else OmnioColors.TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = OmnioColors.TextTertiary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
internal fun SettingsDetailHeader(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    cinematicStyle: Boolean = false
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(if (cinematicStyle) 10.dp else 6.dp)
    ) {
        if (cinematicStyle) {
            Text(
                text = "CURATED PANEL",
                style = MaterialTheme.typography.labelMedium,
                color = SettingsCinematicHeaderRed,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 1.4.sp
            )
        }
        Text(
            text = title,
            style = if (cinematicStyle) MaterialTheme.typography.headlineLarge else MaterialTheme.typography.headlineMedium,
            color = OmnioColors.TextPrimary
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = OmnioColors.TextSecondary,
            maxLines = if (cinematicStyle) 3 else Int.MAX_VALUE,
            overflow = if (cinematicStyle) TextOverflow.Ellipsis else TextOverflow.Clip
        )
    }
}

@Composable
internal fun SettingsGroupCard(
    modifier: Modifier = Modifier,
    title: String? = null,
    subtitle: String? = null,
    cinematicStyle: Boolean = false,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(SettingsSecondaryCardRadius))
            .background(if (cinematicStyle) SettingsCinematicPanel else OmnioColors.BackgroundCard)
            .border(
                width = 1.dp,
                color = if (cinematicStyle) Color.White.copy(alpha = 0.08f) else OmnioColors.Border,
                shape = RoundedCornerShape(SettingsSecondaryCardRadius)
            )
            .padding(if (cinematicStyle) 18.dp else 14.dp),
        verticalArrangement = Arrangement.spacedBy(if (cinematicStyle) 14.dp else 10.dp)
    ) {
        if (!title.isNullOrBlank()) {
            Text(
                text = title,
                style = if (cinematicStyle) MaterialTheme.typography.titleLarge else MaterialTheme.typography.titleMedium,
                color = if (cinematicStyle) SettingsCinematicHeaderRed else OmnioColors.TextPrimary,
                fontWeight = if (cinematicStyle) FontWeight.SemiBold else FontWeight.Normal
            )
        }
        if (!subtitle.isNullOrBlank()) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = OmnioColors.TextSecondary
            )
        }
        content()
    }
}

@Composable
internal fun SettingsToggleRow(
    title: String,
    subtitle: String?,
    checked: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    onFocused: () -> Unit = {},
    enabled: Boolean = true
) {
    val contentAlpha = if (enabled) 1f else 0.4f
    var isFocused by remember { mutableStateOf(false) }

    Card(
        onClick = {
            if (enabled) onToggle()
        },
        modifier = modifier
            .fillMaxWidth()
            .height(62.dp)
            .onFocusChanged { state ->
                val nowFocused = state.isFocused
                if (isFocused != nowFocused) {
                    isFocused = nowFocused
                    if (nowFocused) onFocused()
                }
            },
        colors = CardDefaults.colors(
            containerColor = OmnioColors.Background,
            focusedContainerColor = OmnioColors.Background
        ),
        border = CardDefaults.border(
            focusedBorder = Border(
                border = BorderStroke(2.dp, OmnioColors.FocusRing.copy(alpha = contentAlpha)),
                shape = RoundedCornerShape(SettingsPillRadius)
            )
        ),
        shape = CardDefaults.shape(RoundedCornerShape(SettingsPillRadius)),
        scale = CardDefaults.scale(focusedScale = 1f, pressedScale = 1f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = OmnioColors.TextPrimary.copy(alpha = contentAlpha),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (!subtitle.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = OmnioColors.TextSecondary.copy(alpha = contentAlpha),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            SettingsTogglePill(
                checked = checked,
                enabled = enabled
            )
        }
    }
}

@Composable
internal fun SettingsActionRow(
    title: String,
    subtitle: String?,
    value: String? = null,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onFocused: () -> Unit = {},
    enabled: Boolean = true,
    trailingIcon: ImageVector = Icons.Default.ChevronRight,
    cinematicStyle: Boolean = false,
    kicker: String? = null,
    leadingContent: (@Composable () -> Unit)? = null
) {
    val contentAlpha = if (enabled) 1f else 0.4f
    var isFocused by remember { mutableStateOf(false) }

    Card(
        onClick = { if (enabled) onClick() },
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 62.dp)
            .onFocusChanged { state ->
                val nowFocused = state.isFocused
                if (isFocused != nowFocused) {
                    isFocused = nowFocused
                    if (nowFocused) onFocused()
                }
            },
        colors = CardDefaults.colors(
            containerColor = if (cinematicStyle) SettingsCinematicSurface else OmnioColors.Background,
            focusedContainerColor = if (cinematicStyle) SettingsCinematicSurfaceFocused else OmnioColors.Background
        ),
        border = CardDefaults.border(
            focusedBorder = Border(
                border = BorderStroke(
                    2.dp,
                    if (cinematicStyle) SettingsCinematicHeaderRed.copy(alpha = contentAlpha) else OmnioColors.FocusRing.copy(alpha = contentAlpha)
                ),
                shape = RoundedCornerShape(SettingsPillRadius)
            ),
            border = if (cinematicStyle) Border(
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.06f * contentAlpha)),
                shape = RoundedCornerShape(SettingsPillRadius)
            ) else Border.None
        ),
        shape = CardDefaults.shape(RoundedCornerShape(SettingsPillRadius)),
        scale = CardDefaults.scale(focusedScale = 1f, pressedScale = 1f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = if (cinematicStyle) 20.dp else 18.dp, vertical = if (cinematicStyle) 14.dp else 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (leadingContent != null) {
                leadingContent()
                Spacer(modifier = Modifier.width(16.dp))
            }

            Column(modifier = Modifier.weight(1f)) {
                if (!kicker.isNullOrBlank()) {
                    Text(
                        text = kicker.uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = SettingsCinematicHeaderRed.copy(alpha = contentAlpha),
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 1.1.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                }
                Text(
                    text = title,
                    style = if (cinematicStyle) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyLarge,
                    color = OmnioColors.TextPrimary.copy(alpha = contentAlpha),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (!subtitle.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(if (cinematicStyle) 4.dp else 2.dp))
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = OmnioColors.TextSecondary.copy(alpha = contentAlpha),
                        maxLines = if (cinematicStyle) 2 else 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            if (!value.isNullOrBlank()) {
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = value,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (cinematicStyle) Color.White.copy(alpha = 0.78f * contentAlpha) else OmnioColors.TextSecondary.copy(alpha = contentAlpha),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.width(10.dp))
            Icon(
                imageVector = trailingIcon,
                contentDescription = null,
                tint = if (cinematicStyle) SettingsCinematicHeaderRed.copy(alpha = contentAlpha) else OmnioColors.TextTertiary.copy(alpha = contentAlpha),
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
internal fun SettingsChoiceChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onFocused: () -> Unit = {}
) {
    var isFocused by remember { mutableStateOf(false) }

    Card(
        onClick = onClick,
        modifier = modifier.onFocusChanged { state ->
            val nowFocused = state.isFocused
            if (isFocused != nowFocused) {
                isFocused = nowFocused
                if (nowFocused) onFocused()
            }
        },
        colors = CardDefaults.colors(
            containerColor = if (selected) OmnioColors.FocusRing.copy(alpha = 0.2f) else OmnioColors.Background,
            focusedContainerColor = if (selected) OmnioColors.FocusRing.copy(alpha = 0.2f) else OmnioColors.Background
        ),
        border = CardDefaults.border(
            border = if (selected) Border(
                border = BorderStroke(1.dp, OmnioColors.FocusRing),
                shape = RoundedCornerShape(SettingsPillRadius)
            ) else Border.None,
            focusedBorder = Border(
                border = BorderStroke(1.dp, OmnioColors.FocusRing),
                shape = RoundedCornerShape(SettingsPillRadius)
            )
        ),
        shape = CardDefaults.shape(RoundedCornerShape(SettingsPillRadius)),
        scale = CardDefaults.scale(focusedScale = 1f, pressedScale = 1f)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected || isFocused) OmnioColors.TextPrimary else OmnioColors.TextSecondary,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
        )
    }
}

@Composable
private fun SettingsTogglePill(
    checked: Boolean,
    enabled: Boolean
) {
    val alpha = if (enabled) 1f else 0.35f
    Box(
        modifier = Modifier
            .width(46.dp)
            .height(24.dp)
            .clip(RoundedCornerShape(SettingsPillRadius))
            .background(
                if (checked) {
                    OmnioColors.Secondary.copy(alpha = 0.92f * alpha)
                } else {
                    Color.White.copy(alpha = 0.14f * alpha)
                }
            )
            .padding(2.dp),
        contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart
    ) {
        Box(
            modifier = Modifier
                .size(20.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = alpha))
        )
    }
}

@Composable
private fun rememberRawSvgPainter(rawIconRes: Int): Painter {
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

internal data class SettingsPickerOption<T>(
    val value: T,
    val title: String,
    val description: String? = null,
    val trailing: String? = null,
)

@Composable
internal fun <T> SettingsSingleChoiceDialog(
    title: String,
    options: List<SettingsPickerOption<T>>,
    selected: T,
    onSelected: (T) -> Unit,
    onDismiss: () -> Unit,
    subtitle: String? = null,
    width: Dp = 420.dp,
    maxHeight: Dp = 320.dp,
) {
    val focusRequester = remember { FocusRequester() }
    val selectedIndex = options.indexOfFirst { it.value == selected }
    val firstFocusIndex = if (selectedIndex >= 0) selectedIndex else 0

    OmnioDialog(
        onDismiss = onDismiss,
        title = title,
        subtitle = subtitle,
        width = width,
        suppressFirstKeyUp = true
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = maxHeight)
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(
                    count = options.size,
                    key = { options[it].value.toString() }
                ) { index ->
                    val option = options[index]
                    val isSelected = option.value == selected
                    SettingsPickerOptionItem(
                        title = option.title,
                        description = option.description,
                        trailing = option.trailing,
                        isSelected = isSelected,
                        onClick = {
                            onSelected(option.value)
                            onDismiss()
                        },
                        modifier = if (index == firstFocusIndex) {
                            Modifier.focusRequester(focusRequester)
                        } else {
                            Modifier
                        }
                    )
                }
            }
        }

        LaunchedEffect(Unit) {
            focusRequester.requestFocus()
        }
    }
}

@Composable
internal fun <T> SettingsMultiChoiceDialog(
    title: String,
    options: List<SettingsPickerOption<T>>,
    initiallySelected: List<T>,
    onSave: (List<T>) -> Unit,
    onDismiss: () -> Unit,
    subtitle: String? = null,
    onClear: (() -> Unit)? = null,
    saveLabel: String? = null,
    clearLabel: String? = null,
    width: Dp = 520.dp,
    maxHeight: Dp = 420.dp,
) {
    val focusRequester = remember { FocusRequester() }
    val selectedList = remember { mutableStateListOf<T>().apply { addAll(initiallySelected) } }
    val effectiveSaveLabel = saveLabel ?: stringResource(R.string.action_save)
    val effectiveClearLabel = clearLabel ?: stringResource(R.string.action_clear)

    OmnioDialog(
        onDismiss = onDismiss,
        title = title,
        subtitle = subtitle,
        width = width,
        suppressFirstKeyUp = true
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = maxHeight)
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(
                    count = options.size,
                    key = { options[it].value.toString() }
                ) { index ->
                    val option = options[index]
                    val isSelected = option.value in selectedList
                    SettingsPickerOptionItem(
                        title = option.title,
                        description = option.description,
                        trailing = option.trailing,
                        isSelected = isSelected,
                        onClick = {
                            if (isSelected) {
                                selectedList.remove(option.value)
                            } else {
                                selectedList.add(option.value)
                            }
                        },
                        modifier = if (index == 0) {
                            Modifier.focusRequester(focusRequester)
                        } else {
                            Modifier
                        }
                    )
                }

                item(key = "multi_choice_footer") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        SettingsChoiceChip(
                            label = effectiveSaveLabel,
                            selected = true,
                            onClick = {
                                onSave(selectedList.toList())
                                onDismiss()
                            },
                            modifier = Modifier.weight(1f)
                        )
                        if (onClear != null) {
                            SettingsChoiceChip(
                                label = effectiveClearLabel,
                                selected = false,
                                onClick = { selectedList.clear() },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }

        LaunchedEffect(Unit) {
            focusRequester.requestFocus()
        }
    }
}

@Composable
private fun SettingsPickerOptionItem(
    title: String,
    description: String?,
    trailing: String?,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var isFocused by remember { mutableStateOf(false) }

    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .then(modifier)
            .onFocusChanged { isFocused = it.isFocused },
        colors = CardDefaults.colors(
            containerColor = if (isSelected) OmnioColors.FocusBackground else OmnioColors.BackgroundCard,
            focusedContainerColor = OmnioColors.FocusBackground
        ),
        shape = CardDefaults.shape(shape = RoundedCornerShape(10.dp)),
        scale = CardDefaults.scale(focusedScale = 1f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (isSelected) OmnioColors.Primary else OmnioColors.TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (!description.isNullOrBlank()) {
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodySmall,
                        color = OmnioColors.TextSecondary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            if (!trailing.isNullOrBlank()) {
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = trailing,
                    style = MaterialTheme.typography.bodySmall,
                    color = OmnioColors.TextSecondary
                )
            }

            if (isSelected) {
                Spacer(modifier = Modifier.width(12.dp))
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = stringResource(R.string.cd_selected),
                    tint = OmnioColors.Primary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

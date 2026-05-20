@file:OptIn(ExperimentalTvMaterial3Api::class)

package com.omnio.tv.ui.screens.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.view.KeyEvent as AndroidKeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.Image
import androidx.compose.ui.Alignment
import androidx.compose.ui.layout.ContentScale
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Border
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.omnio.tv.R
import com.omnio.tv.core.qr.QrCodeGenerator
import com.omnio.tv.core.uishared.OmnioColors
import com.omnio.tv.domain.model.SourceCloudService
import com.omnio.tv.ui.components.OmnioDialog

@Composable
fun SourceCloudSettingsContent(
    viewModel: SourceCloudSettingsViewModel = hiltViewModel(),
    initialFocusRequester: FocusRequester? = null
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val status = uiState.status
    val services = status?.services.orEmpty()
    val config = status?.config
    val hasConnectedService = services.any { it.connected }
    val sourceCloudEnabled = status?.enabled == true
    val advancedSession = uiState.advancedConfigSession
    val canRequestAdvancedSession = status?.baseUrlConfigured == true &&
        !uiState.isAdvancedConfigLoading &&
        !uiState.isLoading
    var disconnectConfirmService by remember { mutableStateOf<SourceCloudService?>(null) }
    var connectChooserService by remember { mutableStateOf<SourceCloudService?>(null) }
    var connectApiKeyService by remember { mutableStateOf<SourceCloudService?>(null) }
    var showAiostreamsQrFullscreen by remember { mutableStateOf(false) }
    val context = LocalContext.current

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        SettingsDetailHeader(
            title = stringResource(R.string.source_cloud_title),
            subtitle = stringResource(R.string.source_cloud_subtitle)
        )

        SettingsGroupCard(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                uiState.errorMessage?.let { message ->
                    SourceCloudInfoCard(message = message, tone = SourceCloudInfoTone.Error)
                }

                if (status?.baseUrlConfigured == false) {
                    SourceCloudInfoCard(message = stringResource(R.string.source_cloud_backend_missing))
                }

                if (status != null && !hasConnectedService) {
                    SourceCloudInfoCard(message = stringResource(R.string.source_cloud_no_services))
                }

                if (config != null) {
                    SourceCloudConfigStatusCard(
                        title = stringResource(R.string.source_cloud_config_status_title),
                        subtitle = sourceCloudConfigSubtitle(config.label, config.message)
                    )
                }

                SettingsToggleRow(
                    title = stringResource(R.string.source_cloud_enable_title),
                    subtitle = stringResource(R.string.source_cloud_enable_subtitle),
                    checked = sourceCloudEnabled,
                    enabled = status != null && !uiState.isLoading,
                    onToggle = { viewModel.setEnabled(!sourceCloudEnabled) },
                    modifier = if (initialFocusRequester != null) {
                        Modifier.focusRequester(initialFocusRequester)
                    } else {
                        Modifier
                    }
                )

                SourceCloudService.entries.forEach { service ->
                    val serviceStatus = services.firstOrNull { it.service == service }
                    val isConnected = serviceStatus?.connected == true
                    val isDisconnecting = uiState.disconnectingService == service
                    val rowEnabled = status != null && !isDisconnecting && !uiState.isLoading
                    SettingsActionRow(
                        title = serviceStatus?.label ?: service.displayName,
                        subtitle = sourceCloudServiceSubtitle(isConnected),
                        onClick = {
                            if (isConnected) {
                                disconnectConfirmService = service
                            } else {
                                connectChooserService = service
                            }
                        },
                        enabled = rowEnabled
                    )
                }

                SettingsActionRow(
                    title = stringResource(R.string.source_cloud_advanced_config_title),
                    subtitle = stringResource(R.string.source_cloud_advanced_config_subtitle),
                    onClick = { viewModel.requestAdvancedConfigSession() },
                    enabled = canRequestAdvancedSession
                )

                if (advancedSession != null) {
                    SourceCloudAdvancedQrCard(
                        url = advancedSession.url,
                        message = advancedSession.message ?: stringResource(R.string.source_cloud_advanced_qr_message)
                    )

                    SettingsActionRow(
                        title = stringResource(R.string.source_cloud_advanced_regenerate_title),
                        subtitle = stringResource(R.string.source_cloud_advanced_regenerate_subtitle),
                        onClick = { viewModel.requestAdvancedConfigSession() },
                        enabled = !uiState.isAdvancedConfigLoading
                    )

                    val configureUrl = advancedSession.directConfigureUrl
                    val configurePassword = advancedSession.configurePassword
                    if (!configureUrl.isNullOrBlank() || !configurePassword.isNullOrBlank()) {
                        Text(
                            text = stringResource(R.string.source_cloud_aiostreams_section_title),
                            style = MaterialTheme.typography.titleSmall,
                            color = OmnioColors.TextSecondary,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
                        )

                        if (!configureUrl.isNullOrBlank()) {
                            SettingsActionRow(
                                title = stringResource(R.string.source_cloud_aiostreams_configure_url_title),
                                subtitle = stringResource(R.string.source_cloud_aiostreams_configure_url_subtitle),
                                value = shortenUrl(configureUrl),
                                onClick = { copyToClipboard(context, "AIOStreams URL", configureUrl) }
                            )
                        }

                        if (!configurePassword.isNullOrBlank()) {
                            SettingsActionRow(
                                title = stringResource(R.string.source_cloud_aiostreams_configure_password_title),
                                subtitle = stringResource(R.string.source_cloud_aiostreams_configure_password_subtitle),
                                value = "••••••••",
                                onClick = { copyToClipboard(context, "AIOStreams password", configurePassword) }
                            )
                        }

                        if (!configureUrl.isNullOrBlank()) {
                            SettingsActionRow(
                                title = stringResource(R.string.source_cloud_aiostreams_show_qr_title),
                                subtitle = stringResource(R.string.source_cloud_aiostreams_show_qr_subtitle),
                                onClick = { showAiostreamsQrFullscreen = true }
                            )
                        }
                    }
                }

                if (config?.canReset == true) {
                    SettingsActionRow(
                        title = stringResource(R.string.source_cloud_reset_config_title),
                        subtitle = stringResource(R.string.source_cloud_reset_config_subtitle),
                        onClick = { viewModel.resetConfig() },
                        enabled = !uiState.isLoading
                    )
                }
            }
        }

        if (disconnectConfirmService != null) {
            val service = disconnectConfirmService!!
            SettingsSingleChoiceDialog(
                title = stringResource(R.string.source_cloud_disconnect_confirm_title, service.displayName),
                options = listOf(
                    SettingsPickerOption(
                        value = "cancel",
                        title = stringResource(R.string.action_cancel)
                    ),
                    SettingsPickerOption(
                        value = "disconnect",
                        title = stringResource(R.string.source_cloud_disconnect_confirm_action)
                    )
                ),
                selected = "cancel",
                onSelected = { selection ->
                    if (selection == "disconnect") {
                        viewModel.disconnectService(service)
                    }
                    disconnectConfirmService = null
                },
                onDismiss = { disconnectConfirmService = null }
            )
        }

        if (connectChooserService != null) {
            val service = connectChooserService!!
            SettingsSingleChoiceDialog(
                title = stringResource(R.string.source_cloud_connect_choose_title),
                options = listOf(
                    SettingsPickerOption(
                        value = "phone",
                        title = stringResource(R.string.source_cloud_connect_via_phone),
                        description = stringResource(R.string.source_cloud_connect_via_phone_desc)
                    ),
                    SettingsPickerOption(
                        value = "apikey",
                        title = stringResource(R.string.source_cloud_connect_via_apikey),
                        description = stringResource(R.string.source_cloud_connect_via_apikey_desc)
                    )
                ),
                selected = "phone",
                onSelected = { selection ->
                    if (selection == "phone") {
                        viewModel.requestAdvancedConfigSession()
                    } else {
                        connectApiKeyService = service
                    }
                    connectChooserService = null
                },
                onDismiss = { connectChooserService = null }
            )
        }

        if (connectApiKeyService != null) {
            SourceCloudApiKeyDialog(
                service = connectApiKeyService!!,
                isConnecting = uiState.connectingService == connectApiKeyService,
                error = uiState.connectError,
                onDismiss = {
                    connectApiKeyService = null
                    viewModel.clearConnectError()
                },
                onSubmit = { apiKey ->
                    viewModel.connectService(connectApiKeyService!!, apiKey)
                }
            )
        }
    }

    val aioConfigureUrl = advancedSession?.directConfigureUrl
    if (showAiostreamsQrFullscreen && !aioConfigureUrl.isNullOrBlank()) {
        AiostreamsConfigureQrFullscreenOverlay(
            url = aioConfigureUrl,
            onDismiss = { showAiostreamsQrFullscreen = false }
        )
    }
}

@Composable
private fun AiostreamsConfigureQrFullscreenOverlay(url: String, onDismiss: () -> Unit) {
    BackHandler(onBack = onDismiss)

    val bitmap = remember(url) {
        runCatching { QrCodeGenerator.generate(url, 720) }.getOrNull()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.94f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp),
            modifier = Modifier.padding(32.dp)
        ) {
            if (bitmap != null) {
                Box(
                    modifier = Modifier
                        .background(Color.White)
                        .padding(20.dp)
                ) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = stringResource(R.string.cd_source_cloud_aiostreams_qr),
                        modifier = Modifier.size(420.dp),
                        contentScale = ContentScale.Fit
                    )
                }
            }
            Text(
                text = stringResource(R.string.source_cloud_aiostreams_qr_caption),
                style = MaterialTheme.typography.bodyLarge,
                color = OmnioColors.TextPrimary
            )
            Text(
                text = url,
                style = MaterialTheme.typography.bodySmall,
                color = OmnioColors.TextSecondary
            )
            Text(
                text = stringResource(R.string.source_cloud_aiostreams_qr_dismiss_hint),
                style = MaterialTheme.typography.bodySmall,
                color = OmnioColors.TextTertiary
            )
        }
    }
}

private fun shortenUrl(url: String): String {
    if (url.length <= 48) return url
    return url.take(24) + "…" + url.takeLast(20)
}

private fun copyToClipboard(context: Context, label: String, value: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
    clipboard.setPrimaryClip(ClipData.newPlainText(label, value))
}

@Composable
private fun sourceCloudConfigSubtitle(label: String?, message: String?): String = listOfNotNull(
    label?.takeIf { it.isNotBlank() },
    message?.takeIf { it.isNotBlank() }
).joinToString(" · ").ifBlank { stringResource(R.string.source_cloud_config_status_unknown) }

@Composable
private fun sourceCloudServiceSubtitle(connected: Boolean): String = if (connected) {
    stringResource(R.string.source_cloud_service_connected)
} else {
    stringResource(R.string.source_cloud_service_disconnected)
}

@Composable
private fun SourceCloudServiceStatusCard(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(OmnioColors.Background, RoundedCornerShape(12.dp))
            .border(1.dp, OmnioColors.Border, RoundedCornerShape(12.dp))
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            color = OmnioColors.TextPrimary
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = OmnioColors.TextSecondary
        )
    }
}

@Composable
private fun SourceCloudConfigStatusCard(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier
) {
    SourceCloudServiceStatusCard(
        title = title,
        subtitle = subtitle,
        modifier = modifier
    )
}

@Composable
private fun SourceCloudAdvancedQrCard(
    url: String,
    message: String,
    modifier: Modifier = Modifier
) {
    val qrBitmap = remember(url) { runCatching { QrCodeGenerator.generate(url, 360) }.getOrNull() }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(OmnioColors.Background, RoundedCornerShape(12.dp))
            .border(1.dp, OmnioColors.Border, RoundedCornerShape(12.dp))
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = stringResource(R.string.source_cloud_advanced_qr_title),
            style = MaterialTheme.typography.bodyLarge,
            color = OmnioColors.TextPrimary
        )
        Text(
            text = message,
            style = MaterialTheme.typography.bodySmall,
            color = OmnioColors.TextSecondary
        )
        if (qrBitmap != null) {
            Image(
                bitmap = qrBitmap.asImageBitmap(),
                contentDescription = stringResource(R.string.cd_source_cloud_advanced_qr),
                modifier = Modifier
                    .size(220.dp)
                    .clip(RoundedCornerShape(20.dp))
            )
        }
    }
}

@Composable
private fun SourceCloudInfoCard(
    message: String,
    modifier: Modifier = Modifier,
    tone: SourceCloudInfoTone = SourceCloudInfoTone.Neutral
) {
    val borderColor = when (tone) {
        SourceCloudInfoTone.Neutral -> OmnioColors.Border
        SourceCloudInfoTone.Error -> OmnioColors.Error.copy(alpha = 0.45f)
    }
    val textColor = when (tone) {
        SourceCloudInfoTone.Neutral -> OmnioColors.TextSecondary
        SourceCloudInfoTone.Error -> OmnioColors.Error
    }

    Text(
        text = message,
        style = MaterialTheme.typography.bodyMedium,
        color = textColor,
        modifier = modifier
            .fillMaxWidth()
            .background(Color.White.copy(alpha = 0.04f), RoundedCornerShape(12.dp))
            .border(1.dp, borderColor, RoundedCornerShape(12.dp))
            .padding(horizontal = 18.dp, vertical = 14.dp)
    )
}

private enum class SourceCloudInfoTone {
    Neutral,
    Error
}

@Composable
private fun SourceCloudApiKeyDialog(
    service: SourceCloudService,
    isConnecting: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onSubmit: (String) -> Unit
) {
    var value by remember { mutableStateOf("") }
    var isInputFocused by remember { mutableStateOf(false) }
    val inputFocusRequester = remember { FocusRequester() }
    val context = LocalContext.current
    val keyboardController = LocalSoftwareKeyboardController.current

    LaunchedEffect(Unit) { runCatching { inputFocusRequester.requestFocus() } }

    OmnioDialog(
        onDismiss = onDismiss,
        title = stringResource(R.string.source_cloud_apikey_dialog_title, service.displayName),
        width = 600.dp
    ) {
        Card(
            onClick = { inputFocusRequester.requestFocus() },
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged { isInputFocused = it.isFocused || it.hasFocus },
            colors = CardDefaults.colors(
                containerColor = OmnioColors.BackgroundElevated,
                focusedContainerColor = OmnioColors.BackgroundElevated
            ),
            border = CardDefaults.border(
                border = Border(
                    border = BorderStroke(1.dp, OmnioColors.Border),
                    shape = RoundedCornerShape(10.dp)
                ),
                focusedBorder = Border(
                    border = BorderStroke(2.dp, OmnioColors.FocusRing),
                    shape = RoundedCornerShape(10.dp)
                )
            ),
            shape = CardDefaults.shape(RoundedCornerShape(10.dp)),
            scale = CardDefaults.scale(focusedScale = 1f)
        ) {
            Box(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
                BasicTextField(
                    value = value,
                    onValueChange = { value = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(inputFocusRequester)
                        .onKeyEvent { event ->
                            event.nativeKeyEvent.keyCode == AndroidKeyEvent.KEYCODE_DPAD_CENTER &&
                                event.nativeKeyEvent.action == AndroidKeyEvent.ACTION_DOWN
                        },
                    singleLine = true,
                    keyboardActions = KeyboardActions(onDone = {
                        keyboardController?.hide()
                        if (value.isNotBlank() && !isConnecting) onSubmit(value.trim())
                    }),
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = OmnioColors.TextPrimary),
                    cursorBrush = SolidColor(
                        if (isInputFocused) OmnioColors.Primary
                        else Color.Transparent
                    ),
                    visualTransformation = PasswordVisualTransformation(),
                    enabled = !isConnecting,
                    decorationBox = { innerTextField ->
                        if (value.isBlank()) {
                            Text(
                                text = stringResource(R.string.source_cloud_apikey_field_label),
                                style = MaterialTheme.typography.bodyMedium,
                                color = OmnioColors.TextTertiary
                            )
                        }
                        innerTextField()
                    }
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SettingsChoiceChip(
                label = stringResource(R.string.source_cloud_apikey_paste),
                selected = false,
                onClick = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                    clipboard?.primaryClip?.getItemAt(0)?.text?.toString()?.let { text -> value = text }
                },
                modifier = Modifier.weight(1f)
            )
        }

        if (error != null) {
            Text(
                text = error,
                style = MaterialTheme.typography.bodySmall,
                color = OmnioColors.Error
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.colors(
                    containerColor = OmnioColors.BackgroundElevated,
                    contentColor = OmnioColors.TextPrimary
                )
            ) {
                Text(stringResource(R.string.action_cancel))
            }
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                onClick = {
                    val trimmed = value.trim()
                    if (trimmed.isNotBlank()) onSubmit(trimmed)
                },
                enabled = value.isNotBlank() && !isConnecting,
                colors = ButtonDefaults.colors(
                    containerColor = OmnioColors.BackgroundCard,
                    contentColor = OmnioColors.TextPrimary
                )
            ) {
                Text(stringResource(R.string.source_cloud_connect_submit))
            }
        }
    }
}

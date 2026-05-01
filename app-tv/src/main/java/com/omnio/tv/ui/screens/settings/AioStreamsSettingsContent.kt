@file:OptIn(ExperimentalTvMaterial3Api::class)

package com.omnio.tv.ui.screens.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.omnio.tv.R
import com.omnio.tv.core.qr.QrCodeGenerator
import com.omnio.tv.core.uishared.OmnioColors
import com.omnio.tv.ui.components.OmnioDialog

@Composable
fun AioStreamsSettingsContent(
    viewModel: AioStreamsSettingsViewModel = hiltViewModel(),
    initialFocusRequester: FocusRequester? = null,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var showResetConfirm by remember { mutableStateOf(false) }
    var showQrFullscreen by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        SettingsDetailHeader(
            title = stringResource(R.string.aio_streams_title),
            subtitle = stringResource(R.string.aio_streams_subtitle)
        )

        if (uiState.isPrimaryProfileBlocked) {
            PrimaryProfileBlockedBanner(message = stringResource(R.string.aio_streams_primary_profile_blocked))
        }

        uiState.errorMessage?.let { message ->
            ErrorBanner(message = message, onDismiss = { viewModel.consumeError() })
        }

        uiState.statusMessage?.let { message ->
            StatusBanner(message = message, onDismiss = { viewModel.consumeStatus() })
        }

        if (uiState.canProvisionFromMain && !uiState.hasConfig && !uiState.isPrimaryProfileBlocked) {
            ProvisionFromMainBanner(
                message = stringResource(R.string.aio_streams_no_config_banner),
                actionLabel = stringResource(R.string.aio_streams_provision_action),
                inProgressLabel = stringResource(R.string.aio_streams_provision_in_progress),
                isProvisioning = uiState.isProvisioning,
                onProvision = { viewModel.onProvisionFromMainClick() }
            )
        }

        SettingsGroupCard(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            LazyColumn(
                contentPadding = PaddingValues(bottom = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item(key = "aio_streams_enable") {
                    SettingsToggleRow(
                        title = stringResource(R.string.aio_streams_enable_title),
                        subtitle = stringResource(R.string.aio_streams_enable_subtitle),
                        checked = uiState.enabled,
                        enabled = !uiState.isPrimaryProfileBlocked && !uiState.isMutating,
                        onToggle = { viewModel.onToggleEnabled() },
                        modifier = if (initialFocusRequester != null) {
                            Modifier.focusRequester(initialFocusRequester)
                        } else {
                            Modifier
                        }
                    )
                }

                if (uiState.uuid.isNotBlank()) {
                    item(key = "aio_streams_uuid") {
                        SettingsActionRow(
                            title = stringResource(R.string.aio_streams_uuid_title),
                            subtitle = stringResource(R.string.aio_streams_uuid_subtitle),
                            value = uiState.uuid,
                            onClick = { copyToClipboard(context, "AIOStreams UUID", uiState.uuid) }
                        )
                    }
                }

                if (uiState.manifestUrl.isNotBlank()) {
                    item(key = "aio_streams_manifest") {
                        SettingsActionRow(
                            title = stringResource(R.string.aio_streams_manifest_url_title),
                            subtitle = stringResource(R.string.aio_streams_manifest_url_subtitle),
                            value = shortenUrl(uiState.manifestUrl),
                            onClick = { copyToClipboard(context, "AIOStreams Manifest", uiState.manifestUrl) }
                        )
                    }
                }

                if (uiState.manageUrl.isNotBlank()) {
                    item(key = "aio_streams_manage") {
                        SettingsActionRow(
                            title = stringResource(R.string.aio_streams_manage_on_web_title),
                            subtitle = stringResource(R.string.aio_streams_manage_on_web_subtitle),
                            value = shortenUrl(uiState.manageUrl),
                            onClick = { copyToClipboard(context, "AIOStreams Manage", uiState.manageUrl) }
                        )
                    }
                }

                if (uiState.configPassword.isNotBlank()) {
                    item(key = "aio_streams_password") {
                        SettingsActionRow(
                            title = stringResource(R.string.aio_streams_config_password_title),
                            subtitle = stringResource(R.string.aio_streams_config_password_subtitle),
                            value = uiState.configPassword,
                            onClick = { copyToClipboard(context, "AIOStreams Password", uiState.configPassword) }
                        )
                    }
                }

                if (uiState.manageUrl.isNotBlank()) {
                    item(key = "aio_streams_qr") {
                        SettingsActionRow(
                            title = stringResource(R.string.aio_streams_show_qr_title),
                            subtitle = stringResource(R.string.aio_streams_show_qr_subtitle),
                            onClick = { showQrFullscreen = true }
                        )
                    }
                }

                item(key = "aio_streams_refresh") {
                    SettingsActionRow(
                        title = stringResource(R.string.action_retry),
                        subtitle = stringResource(R.string.aio_streams_refresh_subtitle),
                        enabled = !uiState.isMutating && !uiState.isProvisioning,
                        onClick = { viewModel.onRefreshClick() }
                    )
                }

                if (uiState.canResetFromMain) {
                    item(key = "aio_streams_reset") {
                        SettingsActionRow(
                            title = stringResource(R.string.aio_streams_reset_title),
                            subtitle = stringResource(R.string.aio_streams_reset_subtitle),
                            enabled = !uiState.isProvisioning,
                            onClick = { showResetConfirm = true }
                        )
                    }
                }
            }
        }
    }

    if (showResetConfirm) {
        AioResetConfirmDialog(
            title = stringResource(R.string.aio_streams_reset_confirm_title),
            message = stringResource(R.string.aio_streams_reset_confirm_message),
            actionLabel = stringResource(R.string.aio_streams_reset_confirm_action),
            onConfirm = {
                showResetConfirm = false
                viewModel.onResetFromMainClick()
            },
            onDismiss = { showResetConfirm = false }
        )
    }

    if (showQrFullscreen && uiState.manageUrl.isNotBlank()) {
        AioConfigureQrFullscreenOverlay(
            url = uiState.manageUrl,
            contentDescription = stringResource(R.string.cd_aio_streams_qr),
            caption = stringResource(R.string.aio_streams_qr_caption),
            dismissHint = stringResource(R.string.aio_streams_qr_dismiss_hint),
            onDismiss = { showQrFullscreen = false }
        )
    }
}

@Composable
private fun AioConfigureQrFullscreenOverlay(
    url: String,
    contentDescription: String,
    caption: String,
    dismissHint: String,
    onDismiss: () -> Unit,
) {
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
                        contentDescription = contentDescription,
                        modifier = Modifier.size(420.dp),
                        contentScale = ContentScale.Fit
                    )
                }
            }
            Text(
                text = caption,
                style = MaterialTheme.typography.bodyLarge,
                color = OmnioColors.TextPrimary
            )
            Text(
                text = url,
                style = MaterialTheme.typography.bodySmall,
                color = OmnioColors.TextSecondary
            )
            Text(
                text = dismissHint,
                style = MaterialTheme.typography.bodySmall,
                color = OmnioColors.TextTertiary
            )
        }
    }
}

@Composable
private fun PrimaryProfileBlockedBanner(message: String) {
    Text(
        text = message,
        style = MaterialTheme.typography.bodyMedium,
        color = OmnioColors.Error,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 6.dp)
    )
}

@Composable
private fun ProvisionFromMainBanner(
    message: String,
    actionLabel: String,
    inProgressLabel: String,
    isProvisioning: Boolean,
    onProvision: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = OmnioColors.TextSecondary
        )
        Button(
            onClick = onProvision,
            enabled = !isProvisioning,
            colors = ButtonDefaults.colors(
                containerColor = OmnioColors.BackgroundElevated,
                contentColor = OmnioColors.TextPrimary
            )
        ) {
            Text(if (isProvisioning) inProgressLabel else actionLabel)
        }
    }
}

@Composable
private fun StatusBanner(message: String, onDismiss: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = OmnioColors.TextSecondary,
            modifier = Modifier.weight(1f)
        )
        Button(
            onClick = onDismiss,
            colors = ButtonDefaults.colors(
                containerColor = OmnioColors.BackgroundElevated,
                contentColor = OmnioColors.TextPrimary
            )
        ) {
            Text(stringResource(R.string.action_cancel))
        }
    }
}

@Composable
private fun ErrorBanner(message: String, onDismiss: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = OmnioColors.Error,
            modifier = Modifier.weight(1f)
        )
        Button(
            onClick = onDismiss,
            colors = ButtonDefaults.colors(
                containerColor = OmnioColors.BackgroundElevated,
                contentColor = OmnioColors.TextPrimary
            )
        ) {
            Text(stringResource(R.string.action_cancel))
        }
    }
}

@Composable
private fun AioResetConfirmDialog(
    title: String,
    message: String,
    actionLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    OmnioDialog(
        onDismiss = onDismiss,
        title = title,
        subtitle = message,
        width = 560.dp,
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.colors(
                    containerColor = OmnioColors.BackgroundElevated,
                    contentColor = OmnioColors.TextPrimary
                )
            ) {
                Text(stringResource(R.string.action_cancel))
            }
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.colors(
                    containerColor = OmnioColors.BackgroundCard,
                    contentColor = OmnioColors.TextPrimary
                )
            ) {
                Text(actionLabel)
            }
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

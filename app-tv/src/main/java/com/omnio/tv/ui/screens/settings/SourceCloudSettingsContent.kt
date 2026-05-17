@file:OptIn(ExperimentalTvMaterial3Api::class)

package com.omnio.tv.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.omnio.tv.R
import com.omnio.tv.core.qr.QrCodeGenerator
import com.omnio.tv.core.uishared.OmnioColors
import com.omnio.tv.domain.model.SourceCloudService

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
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                uiState.errorMessage?.let { message ->
                    item(key = "source_cloud_error") {
                        SourceCloudInfoCard(message = message, tone = SourceCloudInfoTone.Error)
                    }
                }

                if (status?.baseUrlConfigured == false) {
                    item(key = "source_cloud_backend_missing") {
                        SourceCloudInfoCard(message = stringResource(R.string.source_cloud_backend_missing))
                    }
                }

                if (status != null && !hasConnectedService) {
                    item(key = "source_cloud_no_services") {
                        SourceCloudInfoCard(message = stringResource(R.string.source_cloud_no_services))
                    }
                }

                if (config != null) {
                    item(key = "source_cloud_config_state") {
                        SourceCloudConfigStatusCard(
                            title = stringResource(R.string.source_cloud_config_status_title),
                            subtitle = sourceCloudConfigSubtitle(config.label, config.message)
                        )
                    }
                }

                item(key = "source_cloud_enabled") {
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
                }

                items(
                    items = SourceCloudService.entries,
                    key = { service -> "source_cloud_service_${service.key}" }
                ) { service ->
                    val serviceStatus = services.firstOrNull { it.service == service }
                    SourceCloudServiceStatusCard(
                        title = serviceStatus?.label ?: service.displayName,
                        subtitle = sourceCloudServiceSubtitle(serviceStatus?.connected == true)
                    )
                }

                item(key = "source_cloud_connection_flows_soon") {
                    SourceCloudInfoCard(message = stringResource(R.string.source_cloud_connection_flows_soon))
                }

                item(key = "source_cloud_advanced_config") {
                    SettingsActionRow(
                        title = stringResource(R.string.source_cloud_advanced_config_title),
                        subtitle = stringResource(R.string.source_cloud_advanced_config_subtitle),
                        onClick = { viewModel.requestAdvancedConfigSession() },
                        enabled = config?.advancedConfigAvailable == true && !uiState.isAdvancedConfigLoading
                    )
                }

                if (advancedSession != null) {
                    item(key = "source_cloud_advanced_qr") {
                        SourceCloudAdvancedQrCard(
                            url = advancedSession.url,
                            message = advancedSession.message ?: stringResource(R.string.source_cloud_advanced_qr_message)
                        )
                    }
                }

                if (advancedSession != null) {
                    item(key = "source_cloud_regenerate_advanced") {
                        SettingsActionRow(
                            title = stringResource(R.string.source_cloud_advanced_regenerate_title),
                            subtitle = stringResource(R.string.source_cloud_advanced_regenerate_subtitle),
                            onClick = { viewModel.requestAdvancedConfigSession() },
                            enabled = !uiState.isAdvancedConfigLoading
                        )
                    }
                }

                if (config?.canReset == true) {
                    item(key = "source_cloud_reset_config") {
                        SettingsActionRow(
                            title = stringResource(R.string.source_cloud_reset_config_title),
                            subtitle = stringResource(R.string.source_cloud_reset_config_subtitle),
                            onClick = { viewModel.resetConfig() },
                            enabled = !uiState.isLoading
                        )
                    }
                }
            }
        }
    }
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

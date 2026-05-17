@file:OptIn(ExperimentalTvMaterial3Api::class)

package com.omnio.tv.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.omnio.tv.R
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
    val hasConnectedService = services.any { it.connected }
    val sourceCloudEnabled = status?.enabled == true

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
            }
        }
    }
}

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

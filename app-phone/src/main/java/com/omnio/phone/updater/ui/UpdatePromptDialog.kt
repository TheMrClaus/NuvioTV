package com.omnio.phone.updater.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.omnio.phone.updater.UpdateUiState

@Composable
fun UpdatePromptDialog(
    state: UpdateUiState,
    onDismiss: () -> Unit,
    onDownload: () -> Unit,
    onInstall: () -> Unit,
    onIgnore: () -> Unit,
    onOpenUnknownSources: () -> Unit
) {
    if (!state.showDialog) return

    val canDownload = state.isUpdateAvailable && state.update != null && !state.isDownloading
    val readyToInstall = state.downloadedApkPath != null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            val title = when {
                state.showUnknownSourcesDialog -> "Install permission required"
                readyToInstall -> "Update downloaded"
                state.isDownloading -> "Downloading update"
                state.update != null && state.isUpdateAvailable ->
                    "Update available — ${state.update.tag}"
                state.errorMessage != null -> "Update check failed"
                else -> "OmnioTV is up to date"
            }
            Text(title, style = MaterialTheme.typography.titleLarge)
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                when {
                    state.showUnknownSourcesDialog -> Text(
                        "Allow OmnioTV to install unknown apps to finish updating.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    state.isDownloading -> {
                        val progress = state.downloadProgress
                        if (progress != null) {
                            LinearProgressIndicator(
                                progress = { progress },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Text(
                                text = "${(progress * 100).toInt()}%",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        } else {
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        }
                    }
                    state.errorMessage != null -> Text(
                        text = state.errorMessage,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                    state.update != null && state.update.notes.isNotBlank() -> {
                        Text(
                            text = state.update.notes,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier
                                .heightIn(max = 240.dp)
                                .verticalScroll(rememberScrollState())
                                .padding(end = 8.dp)
                        )
                    }
                    state.update != null && !state.isUpdateAvailable -> Text(
                        text = "You're on the latest beta.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        },
        confirmButton = {
            when {
                state.showUnknownSourcesDialog -> TextButton(onClick = onOpenUnknownSources) {
                    Text("Open settings")
                }
                readyToInstall -> TextButton(onClick = onInstall) {
                    Text("Install")
                }
                canDownload -> TextButton(onClick = onDownload) {
                    Text("Download")
                }
                else -> TextButton(onClick = onDismiss) {
                    Text("Close")
                }
            }
        },
        dismissButton = {
            when {
                state.showUnknownSourcesDialog -> TextButton(onClick = onDismiss) {
                    Text("Cancel")
                }
                canDownload -> TextButton(onClick = onIgnore) {
                    Text("Skip this version")
                }
                state.isDownloading -> {} // No-op while downloading.
                else -> TextButton(onClick = onDismiss) {
                    Text("Close")
                }
            }
        }
    )
}

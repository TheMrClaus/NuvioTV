package com.omnio.phone.diag

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp

@Composable
fun CrashReportDialog() {
    val context = LocalContext.current
    var trace by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        trace = CrashReporter.readLastCrash(context)
    }

    val current = trace ?: return

    AlertDialog(
        onDismissRequest = {
            CrashReporter.clearLastCrash(context)
            trace = null
        },
        title = {
            Text(
                "OmnioTV crashed last session",
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Tap Copy to put the trace on the clipboard, then paste it back to the developer.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = current,
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 320.dp)
                        .verticalScroll(rememberScrollState())
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                copyToClipboard(context, current)
            }) {
                Text("Copy")
            }
        },
        dismissButton = {
            TextButton(onClick = {
                CrashReporter.clearLastCrash(context)
                trace = null
            }) {
                Text("Dismiss")
            }
        }
    )
}

private fun copyToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        ?: return
    clipboard.setPrimaryClip(ClipData.newPlainText("OmnioTV crash", text))
}

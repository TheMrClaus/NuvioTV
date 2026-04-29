package com.omnio.phone.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.verticalScroll
import android.content.Intent
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.omnio.phone.R
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch

private const val PRIVACY_POLICY_URL = "https://tapframe.github.io/NuvioStreaming/#privacy-policy"
private const val OPEN_SOURCE_URL = "https://github.com/TheMrClaus/OmnioTV"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhoneSettingsScreen(
    onBack: () -> Unit,
    onScanTvQr: () -> Unit,
    onPlayerDefaults: () -> Unit,
    onLanguage: () -> Unit,
    onManageAddons: () -> Unit,
    onOpenLibrary: () -> Unit,
    onOpenHome: () -> Unit,
    viewModel: PhoneSettingsViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val linkErrorMessage = stringResource(R.string.settings_about_link_error)

    LaunchedEffect(state.message) {
        val msg = state.message ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(msg)
        viewModel.clearMessage()
    }

    val openExternalUrl: (String) -> Unit = { url ->
        val opened = runCatching {
            val intent = CustomTabsIntent.Builder().build()
            intent.intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            intent.launchUrl(context, Uri.parse(url))
        }.isSuccess
        if (!opened) {
            coroutineScope.launch { snackbarHostState.showSnackbar(linkErrorMessage) }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
        ) {
            SectionHeader(text = "Account")
            AccountRow(
                email = state.email,
                isSigningOut = state.isSigningOut,
                onSignOut = viewModel::signOut
            )
            AccountConnectedStatsStrip(
                stats = state.connectedStats,
                isLoading = state.isStatsLoading,
                onAddonsClick = onManageAddons,
                onLibraryClick = onOpenLibrary,
                onProgressClick = onOpenHome,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp)
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

            SectionHeader(text = stringResource(R.string.settings_section_tv_login))
            ActionRow(
                icon = Icons.Filled.QrCodeScanner,
                title = stringResource(R.string.settings_action_scan_tv_qr),
                subtitle = stringResource(R.string.settings_action_scan_tv_qr_subtitle),
                onClick = onScanTvQr
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

            SectionHeader(text = stringResource(R.string.settings_section_player))
            ActionRow(
                icon = Icons.Filled.PlayCircle,
                title = stringResource(R.string.settings_action_player_defaults),
                subtitle = stringResource(R.string.settings_action_player_defaults_subtitle),
                onClick = onPlayerDefaults
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

            SectionHeader(text = stringResource(R.string.settings_section_content))
            ActionRow(
                icon = Icons.Filled.Extension,
                title = stringResource(R.string.settings_action_manage_addons),
                subtitle = stringResource(R.string.settings_action_manage_addons_subtitle),
                onClick = onManageAddons
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

            SectionHeader(text = stringResource(R.string.settings_section_general))
            ActionRow(
                icon = Icons.Filled.Language,
                title = stringResource(R.string.settings_action_language),
                subtitle = stringResource(R.string.settings_action_language_subtitle),
                onClick = onLanguage
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

            SectionHeader(text = stringResource(R.string.settings_section_about))
            AboutRow(
                versionName = state.versionName,
                versionCode = state.versionCode,
                isDebugBuild = state.isDebugBuild
            )
            ActionRow(
                icon = Icons.Filled.PrivacyTip,
                title = stringResource(R.string.settings_about_privacy),
                subtitle = stringResource(R.string.settings_about_privacy_subtitle),
                onClick = { openExternalUrl(PRIVACY_POLICY_URL) }
            )
            ActionRow(
                icon = Icons.Filled.Code,
                title = stringResource(R.string.settings_about_open_source),
                subtitle = stringResource(R.string.settings_about_open_source_subtitle),
                onClick = { openExternalUrl(OPEN_SOURCE_URL) }
            )

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
    )
}

@Composable
private fun AccountRow(
    email: String?,
    isSigningOut: Boolean,
    onSignOut: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
    ) {
        Text(
            text = "Signed in as",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = email ?: "Anonymous",
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold
        )

        Spacer(Modifier.height(12.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = !isSigningOut, onClick = onSignOut)
                .padding(vertical = 14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (isSigningOut) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp
                    )
                    Spacer(Modifier.size(12.dp))
                }
                Text(
                    text = "Sign out",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
private fun AboutRow(
    versionName: String,
    versionCode: String,
    isDebugBuild: Boolean
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        SettingsKv(label = "Version", value = versionName.ifBlank { "—" })
        SettingsKv(label = "Build", value = versionCode.ifBlank { "—" })
        SettingsKv(label = "Variant", value = if (isDebugBuild) "Debug" else "Release")
    }
}

@Composable
private fun ActionRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp)
        )
        Spacer(Modifier.size(16.dp))
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun AccountConnectedStatsStrip(
    stats: AccountConnectedStats?,
    isLoading: Boolean,
    onAddonsClick: () -> Unit,
    onLibraryClick: () -> Unit,
    onProgressClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val values = if (isLoading || stats == null) {
        listOf("…", "…", "…", "…")
    } else {
        listOf(
            stats.addons.toString(),
            stats.plugins.toString(),
            stats.library.toString(),
            stats.watchProgress.toString()
        )
    }
    val labels = listOf(
        stringResource(R.string.settings_account_stat_addons),
        stringResource(R.string.settings_account_stat_plugins),
        stringResource(R.string.settings_account_stat_library),
        stringResource(R.string.settings_account_stat_progress)
    )
    // Plugins index intentionally has no destination yet — phone has no plugin manager screen.
    val onClicks: List<(() -> Unit)?> = listOf(
        onAddonsClick,
        null,
        onLibraryClick,
        onProgressClick
    )
    val borderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(values.size) { index ->
            AccountStatItem(
                value = values[index],
                label = labels[index],
                onClick = onClicks[index],
                modifier = Modifier.weight(1f)
            )
            if (index != values.lastIndex) {
                Box(
                    modifier = Modifier
                        .height(36.dp)
                        .width(1.dp)
                        .background(borderColor)
                )
            }
        }
    }
}

@Composable
private fun AccountStatItem(
    value: String,
    label: String,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .let { if (onClick != null) it.clickable(onClick = onClick) else it }
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SettingsKv(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

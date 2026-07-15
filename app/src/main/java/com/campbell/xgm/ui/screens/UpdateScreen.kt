package com.campbell.xgm.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.campbell.xgm.ui.components.AlienButton
import com.campbell.xgm.ui.components.HeaderBar
import com.campbell.xgm.util.ReleaseInfo

@Composable
fun UpdateScreen(
    autoPrompt: Boolean,
    onDismiss: () -> Unit,
    viewModel: UpdateViewModel = viewModel()
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(Unit) {
        if (autoPrompt && state is UpdateUiState.Idle) {
            viewModel.checkForUpdate(autoPrompt = true)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        HeaderBar()
        Spacer(modifier = Modifier.height(16.dp))

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            when (val s = state) {
                is UpdateUiState.Idle -> {
                    Text(
                        text = "App Updates",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Current version: ${viewModel.currentVersion}",
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    if (!autoPrompt) {
                        AlienButton(text = "Check for Updates", onClick = { viewModel.checkForUpdate() })
                        Spacer(modifier = Modifier.height(12.dp))
                        AlienButton(text = "Back", onClick = onDismiss)
                    }
                }

                is UpdateUiState.Checking -> {
                    Spacer(modifier = Modifier.height(32.dp))
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.secondary)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Checking for updates...",
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                }

                is UpdateUiState.UpToDate -> {
                    Spacer(modifier = Modifier.height(24.dp))
                    Text(
                        text = "Up to Date",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.secondary,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "You are running the latest version (${viewModel.currentVersion}).",
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    if (!autoPrompt) {
                        AlienButton(text = "Back", onClick = onDismiss)
                    }
                }

                is UpdateUiState.Available -> {
                    UpdateAvailableCard(release = s.release, currentVersion = viewModel.currentVersion)
                    Spacer(modifier = Modifier.height(20.dp))
                    AlienButton(
                        text = "Download & Install",
                        onClick = { viewModel.downloadAndInstall(s.release) },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    AlienButton(
                        text = if (autoPrompt) "Skip" else "Cancel",
                        isDanger = autoPrompt,
                        onClick = onDismiss
                    )
                }

                is UpdateUiState.Downloading -> {
                    val pct = if (s.total > 0) (s.downloaded * 100 / s.total).toInt() else 0
                    Spacer(modifier = Modifier.height(24.dp))
                    Text(
                        text = "Downloading Update...",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    LinearProgressIndicator(
                        progress = { if (s.total > 0) s.downloaded.toFloat() / s.total else 0f },
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.secondary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "$pct%  ·  ${formatBytes(s.downloaded)} / ${formatBytes(s.total)}",
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                }

                is UpdateUiState.Ready -> {
                    Spacer(modifier = Modifier.height(24.dp))
                    Text(
                        text = "Download Complete",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.secondary,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Tap install to update to ${s.release.version}.",
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                    AlienButton(
                        text = "Install Now",
                        onClick = { viewModel.install(s.file) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                is UpdateUiState.Error -> {
                    Spacer(modifier = Modifier.height(24.dp))
                    Text(
                        text = "Update Error",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = s.message,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                    AlienButton(text = "Retry", onClick = { viewModel.checkForUpdate(autoPrompt) })
                    Spacer(modifier = Modifier.height(12.dp))
                    AlienButton(text = "Back", onClick = onDismiss)
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
fun UpdateAvailableCard(release: ReleaseInfo, currentVersion: String) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Update Available",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.secondary,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "v${release.version}  (you have v$currentVersion)",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold
            )
            if (release.apkSize > 0) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Size: ${formatBytes(release.apkSize)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            if (release.body.isNotBlank()) {
                Text(
                    text = "Release Notes",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = release.body.trim(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                )
            }
        }
    }
}

fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "unknown"
    val units = arrayOf("B", "KB", "MB", "GB")
    var value = bytes.toDouble()
    var i = 0
    while (value >= 1024 && i < units.lastIndex) {
        value /= 1024
        i++
    }
    return "%.1f %s".format(value, units[i])
}

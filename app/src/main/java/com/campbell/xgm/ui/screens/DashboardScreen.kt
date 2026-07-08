package com.campbell.xgm.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.viewmodel.compose.viewModel
import com.campbell.xgm.ui.components.AlienButton
import com.campbell.xgm.ui.components.HeaderBar
import com.campbell.xgm.data.local.GameTargetEntity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    onNavigateToSettings: () -> Unit,
    onNavigateToAbout: () -> Unit,
    viewModel: DashboardViewModel = viewModel()
) {
    val allowedGames by viewModel.allowedGames.collectAsState(initial = emptyList())
    val installedApps by viewModel.installedApps.collectAsState()
    var showAppDialog by remember { mutableStateOf(false) }

    if (showAppDialog) {
        AppSelectionDialog(
            installedApps = installedApps,
            onAppSelected = { app ->
                viewModel.addGame(app)
            },
            onDismissRequest = { showAppDialog = false }
        )
    }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    viewModel.loadInstalledApps()
                    showAppDialog = true
                },
                containerColor = MaterialTheme.colorScheme.secondary,
                contentColor = MaterialTheme.colorScheme.background
            ) {
                Text("+", style = MaterialTheme.typography.headlineMedium)
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            HeaderBar()
            
            Spacer(modifier = Modifier.height(24.dp))
            
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp)
            ) {
                Text(
                    text = "Allowed Games",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.secondary
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                if (allowedGames.isEmpty()) {
                    // Empty State
                    Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Text(
                            text = "No games added yet.\nClick the + button to add a game.",
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                            textAlign = TextAlign.Center
                        )
                    }
                } else {
                    LazyColumn(modifier = Modifier.weight(1f)) {
                        items(allowedGames) { game ->
                            GameRow(
                                game = game,
                                onRemove = { viewModel.removeGame(game.packageName) }
                            )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    AlienButton(text = "Settings", onClick = onNavigateToSettings)
                    AlienButton(text = "About", onClick = onNavigateToAbout)
                }
                
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@Composable
fun GameRow(game: GameTargetEntity, onRemove: () -> Unit) {
    val context = LocalContext.current
    val appIcon = remember(game.packageName) {
        try {
            context.packageManager.getApplicationIcon(game.packageName)
        } catch (_: Exception) { null }
    }

    val appBitmap = remember(appIcon) {
        appIcon?.toBitmap(48, 48)?.asImageBitmap()
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        appBitmap?.let { bitmap ->
            Image(
                bitmap = bitmap,
                contentDescription = game.gameName,
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(10.dp))
            )
            Spacer(modifier = Modifier.width(14.dp))
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = game.gameName,
                color = MaterialTheme.colorScheme.onBackground,
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                text = game.packageName,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                style = MaterialTheme.typography.bodySmall
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            AlienButton(text = "Launch", onClick = {
                val intent = android.content.Intent(context, com.campbell.xgm.domain.services.PipelineService::class.java).apply {
                    putExtra("TARGET_PACKAGE", game.packageName)
                }
                context.startForegroundService(intent)
            })
            AlienButton(text = "Remove", onClick = onRemove, isDanger = true)
        }
    }
}

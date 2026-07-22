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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.viewmodel.compose.viewModel
import com.campbell.xgm.ui.components.AlienButton
import com.campbell.xgm.ui.components.HeaderBar
import com.campbell.xgm.data.local.GameTarget
import com.campbell.xgm.data.local.GameProfile
import com.campbell.xgm.util.PermissionUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    onNavigateToSettings: () -> Unit,
    onNavigateToAbout: () -> Unit,
    onNavigateToPermissions: () -> Unit = {},
    viewModel: DashboardViewModel = viewModel()
) {
    val allowedGames by viewModel.allowedGames.collectAsState(initial = emptyList())
    val installedApps by viewModel.installedApps.collectAsState()
    val runningCount by viewModel.runningAppsCount.collectAsState()
    val isBoosting by viewModel.isBoosting.collectAsState()
    val boostResult by viewModel.boostResult.collectAsState()
    var showAppDialog by remember { mutableStateOf(false) }
    var showProfileDialog by remember { mutableStateOf<Pair<String, String>?>(null) }
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                val permState = PermissionUtils.checkAllPermissions(context)
                if (!permState.allGranted) {
                    onNavigateToPermissions()
                }
                viewModel.refreshRunningCount()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(boostResult) {
        boostResult?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearBoostResult()
        }
    }

    if (showAppDialog) {
        AppSelectionDialog(
            installedApps = installedApps,
            onAppSelected = { app ->
                viewModel.addGame(app)
            },
            onDismissRequest = { showAppDialog = false }
        )
    }

    showProfileDialog?.let { (pkg, name) ->
        val currentProfile = GameProfile.getForPackage(context, pkg)
        GameProfileDialog(
            gameName = name,
            currentProfile = currentProfile,
            onDismiss = { showProfileDialog = null },
            onSave = { profile ->
                GameProfile.saveForPackage(context, pkg, profile)
                showProfileDialog = null
            }
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
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
                                onRemove = { viewModel.removeGame(game.packageName) },
                                onProfile = { showProfileDialog = it }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = if (isBoosting) "Boosting..." else "$runningCount apps running",
                    color = if (isBoosting) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    AlienButton(text = "Settings", onClick = onNavigateToSettings)
                    AlienButton(
                        text = if (isBoosting) "Boosting..." else "Boost",
                        onClick = { viewModel.startBoost() },
                        isEnabled = !isBoosting
                    )
                    AlienButton(text = "About", onClick = onNavigateToAbout)
                }

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@Composable
fun GameRow(game: GameTarget, onRemove: () -> Unit, onProfile: (Pair<String, String>) -> Unit = {}) {
    val context = LocalContext.current
    val appIcon = remember(game.packageName) {
        try {
            context.packageManager.getApplicationIcon(game.packageName)
        } catch (_: Exception) { null }
    }

    val appBitmap = remember(appIcon) {
        appIcon?.toBitmap(48, 48)?.asImageBitmap()
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            appBitmap?.let { bitmap ->
                Image(
                    bitmap = bitmap,
                    contentDescription = game.gameName,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(10.dp))
                )
                Spacer(modifier = Modifier.width(14.dp))
            }

            Column {
                Text(
                    text = game.gameName,
                    color = MaterialTheme.colorScheme.onBackground,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = game.packageName,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            AlienButton(text = "Profile", onClick = { onProfile(game.packageName to game.gameName) }, modifier = Modifier.weight(1f))
            AlienButton(text = "Launch", onClick = {
                val intent = android.content.Intent(context, com.campbell.xgm.domain.services.PipelineService::class.java).apply {
                    putExtra("TARGET_PACKAGE", game.packageName)
                }
                context.startForegroundService(intent)
            }, modifier = Modifier.weight(1f))
            AlienButton(text = "Remove", onClick = onRemove, isDanger = true, modifier = Modifier.weight(1f))
        }
    }
}

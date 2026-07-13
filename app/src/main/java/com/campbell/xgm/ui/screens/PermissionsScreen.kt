package com.campbell.xgm.ui.screens

import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.net.toUri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import com.campbell.xgm.ui.components.AlienButton
import com.campbell.xgm.ui.components.HeaderBar

@Composable
fun PermissionsScreen(
    onPermissionsGranted: () -> Unit,
    viewModel: PermissionsViewModel = viewModel()
) {
    val hasAdmin by viewModel.hasAdmin.collectAsState()
    val hasDnd by viewModel.hasDnd.collectAsState()
    val hasNotifications by viewModel.hasNotifications.collectAsState()
    val hasWriteSettings by viewModel.hasWriteSettings.collectAsState()
    val hasAccessibility by viewModel.hasAccessibility.collectAsState()
    val hasUsageStats by viewModel.hasUsageStats.collectAsState()
    val hasNotificationAccess by viewModel.hasNotificationAccess.collectAsState()

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.checkPermissions()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(hasAdmin, hasDnd, hasNotifications, hasWriteSettings, hasAccessibility, hasUsageStats) {
        if (hasAdmin && hasDnd && hasNotifications && hasWriteSettings && hasAccessibility && hasUsageStats) {
            kotlinx.coroutines.delay(500)
            onPermissionsGranted()
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        HeaderBar()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                text = "System Access Required",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "CampbellXGM is a powerful optimization engine. To isolate your system and freeze apps, you must grant the following accesses.",
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f),
                style = MaterialTheme.typography.bodyMedium
            )
            
            Spacer(modifier = Modifier.height(32.dp))

            val context = LocalContext.current
            
            val adminLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
                viewModel.checkPermissions()
            }
            PermissionItem(
                title = "Device Admin",
                description = "Combined with Accessibility, this enables aggressive force-stopping of background processes.",
                isGranted = hasAdmin,
                onGrantClick = {
                    val component = android.content.ComponentName(context, com.campbell.xgm.domain.services.CampbellAdminReceiver::class.java)
                    val intent = Intent(android.app.admin.DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                        putExtra(android.app.admin.DevicePolicyManager.EXTRA_DEVICE_ADMIN, component)
                        putExtra(android.app.admin.DevicePolicyManager.EXTRA_ADD_EXPLANATION, "Required for Aggressive App Freezing.")
                    }
                    adminLauncher.launch(intent)
                }
            )

            Spacer(modifier = Modifier.height(24.dp))

            val dndLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
                viewModel.checkPermissions()
            }
            PermissionItem(
                title = "Do Not Disturb",
                description = "Allows the engine to silence calls and notifications during gameplay.",
                isGranted = hasDnd,
                onGrantClick = {
                    dndLauncher.launch(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
                }
            )

            Spacer(modifier = Modifier.height(24.dp))

            val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
                viewModel.checkPermissions()
            }
            PermissionItem(
                title = "Notifications",
                description = "Required to show the 'Stop Game Mode' persistent control.",
                isGranted = hasNotifications,
                onGrantClick = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        notifLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                    }
                }
            )

            Spacer(modifier = Modifier.height(24.dp))

            val writeSettingsLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
                viewModel.checkPermissions()
            }
            PermissionItem(
                title = "Modify System Settings",
                description = "Required for Keep Screen Awake and Auto-Brightness Lock features.",
                isGranted = hasWriteSettings,
                onGrantClick = {
                    val intent = Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS).apply {
                        data = "package:${context.packageName}".toUri()
                    }
                    writeSettingsLauncher.launch(intent)
                }
            )

            Spacer(modifier = Modifier.height(24.dp))

            PermissionItem(
                title = "Accessibility Service",
                description = "Required to force-stop background apps during gameplay. This is how Greenify-style app hibernation works.",
                isGranted = hasAccessibility,
                onGrantClick = {
                    val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                    context.startActivity(intent)
                }
            )

            Spacer(modifier = Modifier.height(24.dp))

            PermissionItem(
                title = "Usage Access",
                description = "Required to detect when you leave a game and auto-restore system settings.",
                isGranted = hasUsageStats,
                onGrantClick = {
                    val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
                    context.startActivity(intent)
                }
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Optional: keeps music/streaming alive during game mode by excluding media apps from freezing.
            val notifAccessLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
                viewModel.checkPermissions()
            }
            PermissionItem(
                title = "Notification Access (Keep Media)",
                description = "Optional. Lets game mode detect and keep your music playing while freezing other apps.",
                isGranted = hasNotificationAccess,
                onGrantClick = {
                    notifAccessLauncher.launch(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                }
            )
        }
    }
}

@Composable
fun PermissionItem(
    title: String,
    description: String,
    isGranted: Boolean,
    onGrantClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.secondary,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
            )
        }
        
        Spacer(modifier = Modifier.width(16.dp))

        if (isGranted) {
            Text(
                text = "✓",
                fontSize = 32.sp,
                color = MaterialTheme.colorScheme.secondary,
                fontWeight = FontWeight.Black
            )
        } else {
            AlienButton(text = "Grant", onClick = onGrantClick)
        }
    }
}

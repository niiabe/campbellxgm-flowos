package com.campbell.xgm.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.campbell.xgm.ui.components.AlienButton
import androidx.core.net.toUri
import com.campbell.xgm.ui.components.HeaderBar
import android.content.pm.PackageManager
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers

@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    onNavigateToAdbSetup: () -> Unit,
    onNavigateToUpdate: () -> Unit,
    viewModel: SettingsViewModel = viewModel()
) {
    val isDeviceOwner by viewModel.isDeviceOwner.collectAsState()
    val isDeviceAdmin by viewModel.isDeviceAdmin.collectAsState()
    val isRooted by viewModel.isRooted.collectAsState()
    val hasFullPrivileges = isDeviceOwner || isRooted
    val isAggressiveFreezingEnabled by viewModel.isAggressiveFreezingEnabled.collectAsState()
    val isDndEnabled by viewModel.isDndEnabled.collectAsState()
    val isPingStabilizerEnabled by viewModel.isPingStabilizerEnabled.collectAsState()
    val isKeepScreenAwakeEnabled by viewModel.isKeepScreenAwakeEnabled.collectAsState()
    val isAutoBrightnessLockEnabled by viewModel.isAutoBrightnessLockEnabled.collectAsState()
    val isFpsOverlayEnabled by viewModel.isFpsOverlayEnabled.collectAsState()
    val isNetworkBoostEnabled by viewModel.isNetworkBoostEnabled.collectAsState()
    val isBatteryProfileEnabled by viewModel.isBatteryProfileEnabled.collectAsState()
    val isAutoStartEnabled by viewModel.isAutoStartEnabled.collectAsState()
    val isCpuTunerEnabled by viewModel.isCpuTunerEnabled.collectAsState()
    val isNotificationFilterEnabled by viewModel.isNotificationFilterEnabled.collectAsState()
    val isCooldownEnabled by viewModel.isCooldownEnabled.collectAsState()
    val isStorageCleanerEnabled by viewModel.isStorageCleanerEnabled.collectAsState()
    val isDarkModeEnabled by viewModel.isDarkModeEnabled.collectAsState()
    val isStatsOverlayEnabled by viewModel.isStatsOverlayEnabled.collectAsState()
    val excludedApps by viewModel.excludedApps.collectAsState()
    val selectedDns by viewModel.selectedDns.collectAsState()
    val dnsError by viewModel.dnsError.collectAsState()

    // Refresh state when screen is shown
    LaunchedEffect(Unit) {
        viewModel.refreshAdminState()
    }

    Column(modifier = Modifier.fillMaxSize()) {
        HeaderBar()
        
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(modifier = Modifier.height(16.dp))
            
            Text(
                text = "Engine Settings",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.secondary,
                fontWeight = FontWeight.Bold
            )
            
            Spacer(modifier = Modifier.height(16.dp))

            val context = androidx.compose.ui.platform.LocalContext.current
            val adminLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
                androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
            ) {
                viewModel.refreshAdminState()
            }

            val writeSettingsLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
                androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
            ) {
                // Refresh state not strictly needed, user will tap toggle again
            }

            val vpnPermissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
                androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
            ) { result ->
                if (result.resultCode == android.app.Activity.RESULT_OK) {
                    viewModel.togglePingStabilizer(true)
                }
            }

            val overlayPermissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
                androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
            ) {
                if (android.provider.Settings.canDrawOverlays(context)) {
                    viewModel.toggleFpsOverlay(true)
                }
            }

            // System Privileges Category
            SettingsCategoryCard(title = "System Privileges") {
                SettingsSwitch(
                    title = "Standard Device Admin",
                    description = "Kills background processes using standard Android APIs. Combine with Ghost Finger for full force-stop.",
                    checked = isDeviceAdmin,
                    onCheckedChange = { enable ->
                        if (enable) {
                            val component = android.content.ComponentName(context, com.campbell.xgm.domain.services.CampbellAdminReceiver::class.java)
                            val intent = android.content.Intent(android.app.admin.DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                                putExtra(android.app.admin.DevicePolicyManager.EXTRA_DEVICE_ADMIN, component)
                                putExtra(android.app.admin.DevicePolicyManager.EXTRA_ADD_EXPLANATION, "Required to kill background apps for gaming mode.")
                            }
                            adminLauncher.launch(intent)
                        } else {
                            viewModel.removeDeviceAdmin()
                        }
                    }
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f), modifier = Modifier.padding(vertical = 8.dp))

                Text(
                    text = "Ultimate Device Owner",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = if (isDeviceOwner) "ACTIVE: App has full system isolation privileges to freeze apps in ice." else "INACTIVE: Aggressive freezing is disabled. Setup required.",
                    color = if (isDeviceOwner) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(modifier = Modifier.height(8.dp))
                if (isDeviceOwner) {
                    AlienButton(
                        text = "Remove Device Owner",
                        onClick = { viewModel.removeDeviceOwner() },
                        isDanger = true
                    )
                } else {
                    AlienButton(
                        text = "Setup Ultimate Privilege",
                        onClick = onNavigateToAdbSetup
                    )
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f), modifier = Modifier.padding(vertical = 16.dp))

                val isGhostFingerActive = com.campbell.xgm.domain.services.SafetyInterceptor.isRunning()
                val isGhostFingerEnabled by viewModel.isGhostFingerEnabled.collectAsState()
                Text(
                    text = "Ghost Finger (Accessibility)",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Automated 'Force Stop' clicking of background apps. WARNING: when active it opens each app's Settings page and takes you out of the game. Off by default.",
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(modifier = Modifier.height(8.dp))
                SettingsSwitch(
                    title = "Enable Ghost Finger",
                    description = if (isGhostFingerActive) "Accessibility service is ON. Ghost Finger will run during game mode." else "Accessibility service is OFF. Enable it in system settings to use Ghost Finger.",
                    checked = isGhostFingerEnabled,
                    onCheckedChange = { enable ->
                        viewModel.toggleGhostFinger(enable)
                        if (enable && !isGhostFingerActive) {
                            context.startActivity(android.content.Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS))
                        }
                    }
                )
            }

            // Performance Features Category
            SettingsCategoryCard(title = "Performance") {
                SettingsSwitch(
                    title = "Aggressive App Freezing",
                    description = "Force-stops all background apps to free RAM when a game is launched.",
                    checked = isAggressiveFreezingEnabled,
                    onCheckedChange = { viewModel.toggleAggressiveFreezing(it) }
                )

                SettingsSwitch(
                    title = "CPU/GPU Tuner",
                    description = "Optimizes CPU governor for maximum performance. Requires Device Owner.",
                    checked = isCpuTunerEnabled,
                    onCheckedChange = { viewModel.toggleCpuTuner(it) },
                    isEnabled = isDeviceOwner
                )

                SettingsSwitch(
                    title = "Battery Profile",
                    description = "Attempts to disable battery saver mode during gameplay for consistent performance.",
                    checked = isBatteryProfileEnabled,
                    onCheckedChange = { viewModel.toggleBatteryProfile(it) }
                )

                SettingsSwitch(
                    title = "Cool-down Mode",
                    description = "Reduces performance when device overheats to prevent throttling." +
                        if (!hasFullPrivileges) " (Limited: CPU throttle needs Device Owner/root)" else "",
                    checked = isCooldownEnabled,
                    onCheckedChange = { viewModel.toggleCooldown(it) }
                )

                SettingsSwitch(
                    title = "Storage Cleaner",
                    description = "Clears game cache and temp files before launch to free storage." +
                        if (!hasFullPrivileges) " (Limited: cannot clear other apps' cache without Device Owner/root)" else "",
                    checked = isStorageCleanerEnabled,
                    onCheckedChange = { viewModel.toggleStorageCleaner(it) }
                )
            }

            // Connectivity Category
            SettingsCategoryCard(title = "Connectivity") {
                SettingsSwitch(
                    title = "Network Boost",
                    description = "Disables background sync and enables WiFi verbose logging to reduce network contention." +
                        if (!hasFullPrivileges) " (Limited: full effect needs Device Owner/root + ADB)" else "",
                    checked = isNetworkBoostEnabled,
                    onCheckedChange = { viewModel.toggleNetworkBoost(it) }
                )
                
                SettingsSwitch(
                    title = "Ping Stabilizer",
                    description = "Keep network connection alive to prevent packet loss.",
                    checked = isPingStabilizerEnabled,
                    onCheckedChange = { enable -> 
                        if (enable) {
                            val intent = android.net.VpnService.prepare(context)
                            if (intent != null) {
                                vpnPermissionLauncher.launch(intent)
                            } else {
                                viewModel.togglePingStabilizer(true)
                            }
                        } else {
                            viewModel.togglePingStabilizer(false)
                        }
                    }
                )

                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "DNS Provider",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "Faster, more secure DNS for gaming.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
                Spacer(modifier = Modifier.height(8.dp))

                var dnsExpanded by remember { mutableStateOf(false) }
                Box {
                    OutlinedButton(
                        onClick = { dnsExpanded = true },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.onSurface
                        )
                    ) {
                        Text(
                            text = selectedDns.displayName,
                            modifier = Modifier.fillMaxWidth(),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    DropdownMenu(
                        expanded = dnsExpanded,
                        onDismissRequest = { dnsExpanded = false }
                    ) {
                        DnsProvider.entries.forEach { provider ->
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text(
                                            text = provider.displayName,
                                            fontWeight = if (provider == selectedDns) FontWeight.Bold else FontWeight.Normal,
                                            color = if (provider == selectedDns) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                        )
                                        if (provider != DnsProvider.SYSTEM_DEFAULT) {
                                            Text(
                                                text = provider.hostname,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                            )
                                        }
                                    }
                                },
                                onClick = {
                                    viewModel.setDnsProvider(provider)
                                    dnsExpanded = false
                                }
                            )
                        }
                    }
                }
                if (dnsError != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = dnsError ?: "",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            // Game Mode Features Category
            SettingsCategoryCard(title = "Game Mode") {
                SettingsSwitch(
                    title = "Do Not Disturb (DND)",
                    description = "Automatically silences calls and notifications during gameplay.",
                    checked = isDndEnabled,
                    onCheckedChange = { viewModel.toggleDndMode(it) }
                )

                SettingsSwitch(
                    title = "Notification Filter",
                    description = "Only allows priority contacts to reach you during gameplay.",
                    checked = isNotificationFilterEnabled,
                    onCheckedChange = { viewModel.toggleNotificationFilter(it) }
                )

                SettingsSwitch(
                    title = "Auto-Start Game Mode",
                    description = "Automatically activates game mode when you launch a game.",
                    checked = isAutoStartEnabled,
                    onCheckedChange = { enable ->
                        viewModel.toggleAutoStart(enable)
                        val monitorIntent = android.content.Intent(context, com.campbell.xgm.domain.services.GameLaunchMonitorService::class.java)
                        if (enable) {
                            context.startForegroundService(monitorIntent)
                        } else {
                            context.stopService(monitorIntent)
                        }
                    }
                )

                SettingsSwitch(
                    title = "FPS Overlay",
                    description = "Shows real-time frame rate on screen during gameplay.",
                    checked = isFpsOverlayEnabled,
                    onCheckedChange = { enable ->
                        if (enable && !android.provider.Settings.canDrawOverlays(context)) {
                            val intent = android.content.Intent(android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION).apply {
                                data = "package:${context.packageName}".toUri()
                            }
                            overlayPermissionLauncher.launch(intent)
                        } else {
                            viewModel.toggleFpsOverlay(enable)
                        }
                    }
                )

                SettingsSwitch(
                    title = "Keep Screen Awake",
                    description = "Prevents the screen from turning off while playing.",
                    checked = isKeepScreenAwakeEnabled,
                    onCheckedChange = { enable ->
                        if (enable && !android.provider.Settings.System.canWrite(context)) {
                            val intent = android.content.Intent(android.provider.Settings.ACTION_MANAGE_WRITE_SETTINGS).apply {
                                data = "package:${context.packageName}".toUri()
                            }
                            writeSettingsLauncher.launch(intent)
                        } else {
                            viewModel.toggleKeepScreenAwake(enable)
                        }
                    }
                )

                SettingsSwitch(
                    title = "Auto-Brightness Lock",
                    description = "Disables auto-brightness to keep lighting consistent.",
                    checked = isAutoBrightnessLockEnabled,
                    onCheckedChange = { enable ->
                        if (enable && !android.provider.Settings.System.canWrite(context)) {
                            val intent = android.content.Intent(android.provider.Settings.ACTION_MANAGE_WRITE_SETTINGS).apply {
                                data = "package:${context.packageName}".toUri()
                            }
                            writeSettingsLauncher.launch(intent)
                        } else {
                            viewModel.toggleAutoBrightnessLock(enable)
                        }
                    }
                )
            }

            // Appearance Category
            SettingsCategoryCard(title = "Appearance") {
                SettingsSwitch(
                    title = "Dark Mode",
                    description = "Use dark theme throughout the app. Disable for light theme.",
                    checked = isDarkModeEnabled,
                    onCheckedChange = { viewModel.toggleDarkMode(it) }
                )
            }

            // Overlays Category
            SettingsCategoryCard(title = "Overlays") {
                val context = androidx.compose.ui.platform.LocalContext.current

                SettingsSwitch(
                    title = "System Stats Overlay",
                    description = "Floating overlay showing live RAM, CPU, and Battery usage during gaming.",
                    checked = isStatsOverlayEnabled,
                    onCheckedChange = { enable ->
                        viewModel.toggleStatsOverlay(enable)
                        if (enable) {
                            if (android.provider.Settings.canDrawOverlays(context)) {
                                val intent = android.content.Intent(context, com.campbell.xgm.domain.services.StatsOverlayService::class.java)
                                context.startForegroundService(intent)
                            }
                        } else {
                            val intent = android.content.Intent(context, com.campbell.xgm.domain.services.StatsOverlayService::class.java)
                            intent.action = "STOP_STATS_OVERLAY"
                            context.startService(intent)
                        }
                    }
                )
            }

            // Updates Category
            SettingsCategoryCard(title = "App Updates") {
                val context = androidx.compose.ui.platform.LocalContext.current
                val currentVersion = remember {
                    try {
                        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "Unknown"
                    } catch (_: android.content.pm.PackageManager.NameNotFoundException) {
                        "Unknown"
                    }
                }
                Text(
                    text = "Installed version: $currentVersion",
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Download and install updates directly from GitHub Releases. The app checks automatically on launch.",
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(modifier = Modifier.height(12.dp))
                AlienButton(text = "Check for Updates", onClick = onNavigateToUpdate)
            }

            // Exclusion List Category
            SettingsCategoryCard(title = "App Exclusion List") {
                Text(
                    text = "These apps will never be frozen, killed, or suspended during Game Mode. Tap to manage.",
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(modifier = Modifier.height(8.dp))

                val context = androidx.compose.ui.platform.LocalContext.current
                var showExclusionDialog by remember { mutableStateOf(false) }

                AlienButton(
                    text = if (excludedApps.isEmpty()) "Manage Exclusions" else "Manage Exclusions (${excludedApps.size})",
                    onClick = { showExclusionDialog = true }
                )

                if (showExclusionDialog) {
                    ExclusionListDialog(
                        excludedPackages = excludedApps,
                        onDismiss = { showExclusionDialog = false },
                        onConfirm = { viewModel.setExcludedApps(it) }
                    )
                }
            }

            // Pro Features Category
            SettingsCategoryCard(
                title = "Advanced Features",
            ) {
                SettingsSwitch(
                    title = "Individual Game Profiles",
                    description = "Apply specific engine settings per game. Configure from each game's profile button on the Dashboard.",
                    checked = true,
                    onCheckedChange = {},
                    isEnabled = false
                )

                SettingsSwitch(
                    title = "Real-Time Hardware HUD",
                    description = "Floating overlay with live RAM, CPU, and Battery stats. Enable in the Overlays category above.",
                    checked = isStatsOverlayEnabled,
                    onCheckedChange = {},
                    isEnabled = false
                )

                SettingsSwitch(
                    title = "Custom Crosshair Overlay",
                    description = "Draws a customizable aiming reticle for FPS games. (Coming soon)",
                    checked = false,
                    onCheckedChange = {},
                    isEnabled = false
                )

                SettingsSwitch(
                    title = "Macro Recorder",
                    description = "Record and replay touch sequences for grinding. (Upgrade to unlock)",
                    checked = false,
                    onCheckedChange = {},
                    isEnabled = false
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
            
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                AlienButton(text = "Back to Dashboard", onClick = onNavigateBack)
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
fun SettingsCategoryCard(
    title: String,
    titleColor: Color = MaterialTheme.colorScheme.primary,
    headerExtra: @Composable RowScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = titleColor,
                    fontWeight = FontWeight.Bold
                )
                headerExtra()
            }
            Spacer(modifier = Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
fun SettingsSwitch(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    isEnabled: Boolean = true
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (isEnabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = if (isEnabled) onCheckedChange else null,
            enabled = isEnabled,
            colors = SwitchDefaults.colors(
                checkedThumbColor = MaterialTheme.colorScheme.background,
                checkedTrackColor = MaterialTheme.colorScheme.secondary,
                uncheckedThumbColor = MaterialTheme.colorScheme.onSurface,
                uncheckedTrackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
            )
        )
    }
}

@Composable
fun ExclusionListDialog(
    excludedPackages: Set<String>,
    onDismiss: () -> Unit,
    onConfirm: (Set<String>) -> Unit
) {
    val context = LocalContext.current
    var currentExcluded by remember { mutableStateOf(excludedPackages) }
    var searchQuery by remember { mutableStateOf("") }
    var installedApps by remember { mutableStateOf<List<Triple<String, String, android.graphics.drawable.Drawable>>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        scope.launch(Dispatchers.IO) {
            val pm = context.packageManager
            val apps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
                .filter { pm.getLaunchIntentForPackage(it.packageName) != null }
                .map { app ->
                    Triple(
                        app.packageName,
                        pm.getApplicationLabel(app).toString(),
                        pm.getApplicationIcon(app)
                    )
                }
                .sortedBy { it.second }
            installedApps = apps
            isLoading = false
        }
    }

    val filteredApps = remember(searchQuery, installedApps) {
        if (searchQuery.isBlank()) installedApps
        else installedApps.filter {
            it.second.contains(searchQuery, ignoreCase = true) ||
                    it.first.contains(searchQuery, ignoreCase = true)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        title = {
            Text(
                text = "Excluded Apps (${currentExcluded.size})",
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(modifier = Modifier.fillMaxHeight(0.7f)) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    label = { Text("Search apps...") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.secondary,
                        cursorColor = MaterialTheme.colorScheme.secondary
                    )
                )
                Spacer(modifier = Modifier.height(8.dp))
                if (isLoading) {
                    Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.secondary)
                    }
                } else {
                    LazyColumn(modifier = Modifier.weight(1f)) {
                        items(filteredApps) { (pkg, name, icon) ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        currentExcluded = if (pkg in currentExcluded) {
                                            currentExcluded - pkg
                                        } else {
                                            currentExcluded + pkg
                                        }
                                    }
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = pkg in currentExcluded,
                                    onCheckedChange = { checked ->
                                        currentExcluded = if (checked) {
                                            currentExcluded + pkg
                                        } else {
                                            currentExcluded - pkg
                                        }
                                    },
                                    colors = CheckboxDefaults.colors(
                                        checkedColor = MaterialTheme.colorScheme.secondary
                                    )
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = name,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    text = pkg,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            AlienButton(text = "Save", onClick = {
                onConfirm(currentExcluded)
                onDismiss()
            })
        },
        dismissButton = {
            AlienButton(text = "Cancel", onClick = onDismiss)
        }
    )
}

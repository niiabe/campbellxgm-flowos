package com.campbell.xgm.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.campbell.xgm.data.local.GameProfile
import com.campbell.xgm.ui.components.AlienButton

@Composable
fun GameProfileDialog(
    gameName: String,
    currentProfile: GameProfile,
    onDismiss: () -> Unit,
    onSave: (GameProfile) -> Unit
) {
    var profile by remember { mutableStateOf(currentProfile) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.85f)
                .padding(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = "Profile: $gameName",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Customize engine settings for this game",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
                Spacer(modifier = Modifier.height(16.dp))

                ProfileSwitch("Aggressive Freezing", "Freeze and force-stop background apps", profile.aggressiveFreezing) {
                    profile = profile.copy(aggressiveFreezing = it)
                }
                ProfileSwitch("Do Not Disturb", "Block notifications during gameplay", profile.dndMode) {
                    profile = profile.copy(dndMode = it)
                }
                ProfileSwitch("Network Ping Stabilizer", "Stabilize network latency via VPN", profile.pingStabilizer) {
                    profile = profile.copy(pingStabilizer = it)
                }
                ProfileSwitch("Keep Screen Awake", "Prevent screen from turning off", profile.keepScreenAwake) {
                    profile = profile.copy(keepScreenAwake = it)
                }
                ProfileSwitch("Auto-Brightness Lock", "Lock brightness during gaming", profile.autoBrightnessLock) {
                    profile = profile.copy(autoBrightnessLock = it)
                }
                ProfileSwitch("FPS Overlay", "Show live FPS counter", profile.fpsOverlay) {
                    profile = profile.copy(fpsOverlay = it)
                }
                ProfileSwitch("System Stats Overlay", "Show RAM, CPU, and Battery stats", profile.statsOverlay) {
                    profile = profile.copy(statsOverlay = it)
                }
                ProfileSwitch("Network Boost", "Optimize network for low latency", profile.networkBoost) {
                    profile = profile.copy(networkBoost = it)
                }
                ProfileSwitch("Battery Profile", "Optimize battery usage for gaming", profile.batteryProfile) {
                    profile = profile.copy(batteryProfile = it)
                }
                ProfileSwitch("CPU Tuner", "Tune CPU governor for performance", profile.cpuTuner) {
                    profile = profile.copy(cpuTuner = it)
                }
                ProfileSwitch("Notification Filter", "Filter non-essential notifications", profile.notificationFilter) {
                    profile = profile.copy(notificationFilter = it)
                }
                ProfileSwitch("Thermal Cooldown", "Monitor and manage device temperature", profile.cooldownMode) {
                    profile = profile.copy(cooldownMode = it)
                }
                ProfileSwitch("Storage Cleaner", "Clear cache before launching game", profile.storageCleaner) {
                    profile = profile.copy(storageCleaner = it)
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AlienButton(text = "Cancel", onClick = onDismiss, modifier = Modifier.weight(1f))
                    AlienButton(text = "Save Profile", onClick = { onSave(profile) }, modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun ProfileSwitch(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            Text(text = description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = MaterialTheme.colorScheme.background,
                checkedTrackColor = MaterialTheme.colorScheme.secondary
            )
        )
    }
}

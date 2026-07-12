package com.campbell.xgm.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.campbell.xgm.R
import com.campbell.xgm.ui.components.AlienButton

@Composable
fun AboutScreen(onNavigateBack: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(24.dp))

        Image(
            painter = painterResource(id = R.drawable.logo),
            contentDescription = "CampbellXGM Logo",
            modifier = Modifier.size(100.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            text = "CampbellXGM FlowOS",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(4.dp))
        
        Text(
            text = "Version 1.2.0",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.secondary
        )
        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = "Extreme Gaming Mode App for Android",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
        )
        
        Spacer(modifier = Modifier.height(32.dp))

        // Changelog
        Text(
            text = "Changelog",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(12.dp))

        // v1.3.0
        ChangelogEntry(
            version = "v1.3.0",
            changes = listOf(
                "NEW: App Exclusion List — prevent specific apps from being frozen during Game Mode",
                "NEW: Home Screen Widget — toggle Game Mode directly from your home screen",
                "NEW: Per-Game Settings — configure individual engine profiles for each game",
                "NEW: System Stats Overlay — floating HUD showing live RAM, CPU, and Battery usage",
                "NEW: First-Run Tutorial — onboarding walkthrough explaining permissions and features",
                "NEW: Dark/Light Theme Toggle — switch between dark and light themes in Settings",
                "Improved notifications — Game Mode notification now shows the active game name",
                "Fixed app name — display name now shows as 'CampbellXGM' instead of 'campbellxgm'"
            )
        )

        Spacer(modifier = Modifier.height(16.dp))

        // v1.2.0
        ChangelogEntry(
            version = "v1.2.0",
            changes = listOf(
                "Fixed app freezing — now uses layered approach: killBackgroundProcesses + Accessibility force-stop + periodic re-kill",
                "Fixed auto-teardown — now uses queryEvents() for accurate foreground detection when returning to home screen",
                "Fixed FPS overlay — now measures actual game FPS via dumpsys gfxinfo instead of measuring its own UI thread",
                "Fixed keep screen awake — added missing WAKE_LOCK permission",
                "Fixed cache cleaner — no longer deletes the app's own cache on every game launch",
                "Fixed thread safety — all state variables now use @Volatile for cross-thread visibility",
                "Fixed race condition — restoreSystemState() now uses atomic flag to prevent double-restore",
                "Fixed stopForeground — notification is now properly removed when game mode ends",
                "Fixed SafetyInterceptor — added 12 language variants for Force Stop button, handles disabled state, resource ID search",
                "Fixed SafetyInterceptor thread safety — callback uses AtomicReference, pending list uses CopyOnWriteArrayList",
                "Fixed GameLaunchMonitorService — no longer spawns duplicate coroutines, reloads game list dynamically, doesn't stop when no games saved",
                "Fixed navigation — destination check now includes UsageStats permission, auto-navigates to permissions if any are revoked",
                "Fixed usage stats check — now uses AppOpsManager for reliable permission detection",
                "Fixed auto-start toggle — disable now uses stopService() directly",
                "Fixed game removal — removing a game now stops PipelineService if it's running",
                "Fixed DashboardScreen — removed broken raw permission request",
                "Improved VPN keepalive — TUN device now properly routes traffic",
                "Improved localization — SafetyInterceptor handles Chinese, Korean, Japanese, French, German, Spanish, Italian",
                "Removed no-op PID killing that failed silently on Android 8+",
                "Removed Bluetooth and NFC from system exclusion list to prevent breaking accessories",
                "Added release signing keystore for Play Store distribution",
                "Added ProGuard rules for coroutines, enums, and Compose",
                "Updated UI descriptions to accurately reflect feature capabilities",
                "Cleaned up dead code and duplicate system packages"
            )
        )

        Spacer(modifier = Modifier.height(16.dp))

        // v1.0.0
        ChangelogEntry(
            version = "v1.0.0",
            changes = listOf(
                "Initial release",
                "Aggressive app freezing via Device Owner, Device Admin, or Accessibility",
                "Ping stabilizer VPN engine",
                "FPS overlay",
                "DNS provider selection",
                "DND mode",
                "CPU/GPU tuner",
                "Battery profile",
                "Storage cleaner",
                "Network boost",
                "Cooldown monitoring",
                "Settings dashboard with 13 toggles"
            )
        )

        Spacer(modifier = Modifier.height(32.dp))

        Text(
            text = "Built with Kotlin + Jetpack Compose",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        AlienButton(text = "Back to Dashboard", onClick = onNavigateBack)
        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
fun ChangelogEntry(version: String, changes: List<String>) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = version,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.secondary,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            changes.forEach { change ->
                Text(
                    text = "• $change",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                    modifier = Modifier.padding(vertical = 2.dp)
                )
            }
        }
    }
}

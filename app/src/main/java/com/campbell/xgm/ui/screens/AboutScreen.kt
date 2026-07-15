package com.campbell.xgm.ui.screens

import android.content.pm.PackageManager
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.campbell.xgm.R
import com.campbell.xgm.ui.components.AlienButton

@Composable
fun AboutScreen(onNavigateBack: () -> Unit) {
    val context = LocalContext.current
    val versionName = remember {
        try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "Unknown"
        } catch (_: PackageManager.NameNotFoundException) {
            "Unknown"
        }
    }

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
            text = "Version $versionName",
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

        // v1.5.0
        ChangelogEntry(
            version = "v1.5.0",
            changes = listOf(
                "Auto-Update System: app checks GitHub Releases for newer versions on launch",
                "Update screen with release notes, download progress, and one-tap install",
                "Settings → App Updates lets you check and install updates manually",
                "Skipped updates are remembered and won't re-prompt until a newer version ships",
                "Signed release builds are published to GitHub Releases via CI"
            )
        )

        // v1.4.0
        ChangelogEntry(
            version = "v1.4.0",
            changes = listOf(
                "Security: Keystore credentials moved to gitignored properties file",
                "Security: DNS hostname sanitized and permission pre-checks added for Settings.Global writes",
                "Performance: Pipeline initialization parallelized with structured coroutines",
                "Performance: FPS overlay poll interval 1s to 3s, Game Monitor poll 3s to 8s",
                "Performance: App selection dialog loads icons lazily per-row",
                "Reliability: Original system state persisted before mutation — survives process death",
                "Reliability: SafetyInterceptor race condition fixed with synchronized state machine",
                "Reliability: Added 5 OEM-specific Force Stop button resource IDs",
                "Code quality: Deduplicated foreground detection, file utils, and permission checks into shared utilities",
                "Code quality: Removed dead code and unused Room/Navigation3 dependencies",
                "Code quality: Version now read dynamically from PackageManager",
                "Repository: GameRepository and SettingsRepository interfaces for testable data access",
                "UI: Widget shows first game name on toggle button",
                "UI: Replaced emoji with text symbols in stats overlay"
            )
        )

        Spacer(modifier = Modifier.height(16.dp))

        // v1.3.0
        ChangelogEntry(
            version = "v1.3.0",
            changes = listOf(
                "App Exclusion List — prevent specific apps from being frozen during Game Mode",
                "Home Screen Widget — toggle Game Mode directly from your home screen",
                "Per-Game Settings — configure individual engine profiles for each game",
                "System Stats Overlay — floating HUD showing live RAM, CPU, and Battery usage",
                "First-Run Tutorial — onboarding walkthrough explaining permissions and features",
                "Dark/Light Theme Toggle — switch between dark and light themes in Settings"
            )
        )

        Spacer(modifier = Modifier.height(16.dp))

        // v1.2.0
        ChangelogEntry(
            version = "v1.2.0",
            changes = listOf(
                "Layered app freezing — killBackgroundProcesses + Accessibility force-stop + periodic re-kill",
                "Accurate auto-teardown via queryEvents() foreground detection",
                "FPS overlay now measures actual game FPS via dumpsys gfxinfo",
                "Wake lock and cache cleaner fixes",
                "SafetyInterceptor with 12 language variants for Force Stop button",
                "GameLaunchMonitorService — no duplicate coroutines, dynamic game list reload",
                "Usage stats via AppOpsManager for reliable permission detection",
                "ProGuard rules added for coroutines, enums, and Compose"
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

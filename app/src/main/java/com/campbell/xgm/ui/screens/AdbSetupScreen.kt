package com.campbell.xgm.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.campbell.xgm.ui.components.AlienButton
import com.campbell.xgm.ui.components.HeaderBar

@Composable
fun AdbSetupScreen(
    onNavigateBack: () -> Unit,
    viewModel: SettingsViewModel = viewModel()
) {
    val isDeviceOwner by viewModel.isDeviceOwner.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.refreshAdminState()
    }

    Column(modifier = Modifier.fillMaxSize()) {
        HeaderBar()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Ultimate Privilege Setup",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(24.dp))

            if (isDeviceOwner) {
                // Success State
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.weight(1f)
                ) {
                    Spacer(modifier = Modifier.height(48.dp))
                    Text(
                        text = "✓",
                        fontSize = 120.sp,
                        color = MaterialTheme.colorScheme.secondary,
                        fontWeight = FontWeight.Black
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Text(
                        text = "PRIVILEGE GRANTED",
                        color = MaterialTheme.colorScheme.secondary,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 2.sp
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "CampbellXGM now has ultimate system isolation privileges. You can aggressively freeze apps for maximum gaming performance.",
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            } else {
                // Setup Steps
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = "To unlock Aggressive App Freezing, you must grant CampbellXGM Ultimate Device Owner privileges using a PC. This only needs to be done once.",
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f),
                        style = MaterialTheme.typography.bodyMedium
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    SetupStep(step = "1", text = "Enable 'USB Debugging' in your Android Developer Options.")
                    SetupStep(step = "2", text = "Connect your phone to a PC with ADB installed.")
                    SetupStep(step = "3", text = "Run the following command in your PC terminal:")

                    Spacer(modifier = Modifier.height(16.dp))

                    // Code block
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF1A1A1A), RoundedCornerShape(8.dp))
                            .padding(16.dp)
                    ) {
                        Text(
                            text = "adb shell dpm set-device-owner com.campbell.xgm/.domain.services.CampbellAdminReceiver",
                            color = MaterialTheme.colorScheme.secondary,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 13.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    AlienButton(
                        text = "Refresh Status",
                        onClick = { viewModel.refreshAdminState() }
                    )
                }
            }

            AlienButton(
                text = "Back to Settings",
                onClick = onNavigateBack
            )
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
fun SetupStep(step: String, text: String) {
    Row(
        modifier = Modifier.padding(vertical = 8.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = "Step $step:",
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.width(64.dp)
        )
        Text(
            text = text,
            color = MaterialTheme.colorScheme.onBackground
        )
    }
}

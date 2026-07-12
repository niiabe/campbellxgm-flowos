package com.campbell.xgm.ui.screens

import android.content.Context
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.campbell.xgm.ui.components.AlienButton
import kotlinx.coroutines.launch

data class OnboardingPage(
    val title: String,
    val description: String,
    val icon: String
)

private val onboardingPages = listOf(
    OnboardingPage(
        title = "Welcome to CampbellXGM",
        description = "The most advanced Android gaming performance optimizer. Freeze background apps, boost FPS, and stabilize your network — all without root or ADB.",
        icon = "🎮"
    ),
    OnboardingPage(
        title = "How It Works",
        description = "When you launch a game, CampbellXGM automatically detects it and freezes all background apps. When you exit the game, everything is restored exactly as it was.",
        icon = "⚡"
    ),
    OnboardingPage(
        title = "Permissions Required",
        description = "We need a few permissions to work:\n\n• Usage Access — to detect which game you're playing\n• Display Over Other Apps — for the FPS overlay\n• Accessibility — for Greenify-style force-stop\n• Notification — to show Game Mode status",
        icon = "🔒"
    ),
    OnboardingPage(
        title = "You're All Set!",
        description = "Grant the permissions on the next screen, then add your games from the Dashboard. Start gaming and let CampbellXGM handle the rest!",
        icon = "🚀"
    )
)

@Composable
fun OnboardingScreen(onFinished: () -> Unit) {
    val pagerState = rememberPagerState(pageCount = { onboardingPages.size })
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current

    val completeOnboarding: () -> Unit = {
        context.getSharedPreferences("game_mode_prefs", Context.MODE_PRIVATE)
            .edit().putBoolean("onboarding_complete", true).apply()
        onFinished()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(48.dp))

        HorizontalPager(
            state = pagerState,
            modifier = Modifier.weight(1f)
        ) { page ->
            val pageData = onboardingPages[page]
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = pageData.icon,
                    style = MaterialTheme.typography.displayLarge,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = pageData.title,
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = pageData.description,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                    textAlign = TextAlign.Center,
                    lineHeight = MaterialTheme.typography.bodyLarge.lineHeight
                )
            }
        }

        Row(
            modifier = Modifier.padding(vertical = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            repeat(onboardingPages.size) { index ->
                Box(
                    modifier = Modifier
                        .size(if (pagerState.currentPage == index) 12.dp else 8.dp)
                        .clip(CircleShape)
                        .background(
                            if (pagerState.currentPage == index) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                        )
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (pagerState.currentPage == onboardingPages.size - 1) {
            AlienButton(text = "Get Started", onClick = completeOnboarding)
        } else {
            AlienButton(text = "Next", onClick = {
                coroutineScope.launch {
                    pagerState.animateScrollToPage(pagerState.currentPage + 1)
                }
            })
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (pagerState.currentPage < onboardingPages.size - 1) {
            TextButton(onClick = completeOnboarding) {
                Text(
                    text = "Skip",
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

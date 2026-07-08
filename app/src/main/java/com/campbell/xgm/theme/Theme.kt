package com.campbell.xgm.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val GamingColorScheme = darkColorScheme(
  primary = NeonRed,
  secondary = ElectricBlue,
  tertiary = White,
  background = PureBlack,
  surface = DarkGray,
  onPrimary = White,
  onSecondary = White,
  onTertiary = PureBlack,
  onBackground = White,
  onSurface = White
)

@Composable
fun campbellxgmTheme(
  content: @Composable () -> Unit,
) {
  MaterialTheme(
    colorScheme = GamingColorScheme,
    typography = Typography,
    content = content
  )
}

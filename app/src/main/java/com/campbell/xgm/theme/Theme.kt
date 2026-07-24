package com.campbell.xgm.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
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

private val LightColorScheme = lightColorScheme(
  primary = NeonRed,
  secondary = ElectricBlue,
  tertiary = White,
  background = LightBackground,
  surface = LightSurface,
  onPrimary = White,
  onSecondary = White,
  onTertiary = LightOnBackground,
  onBackground = LightOnBackground,
  onSurface = LightOnSurface
)

@Composable
fun CampbellxgmTheme(
  darkTheme: Boolean = true,
  content: @Composable () -> Unit,
) {
  val colorScheme = if (darkTheme) GamingColorScheme else LightColorScheme

  MaterialTheme(
    colorScheme = colorScheme,
    typography = Typography,
    content = content
  )
}

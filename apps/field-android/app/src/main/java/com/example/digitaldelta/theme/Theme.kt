package com.example.digitaldelta.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
  primary = DeltaTealLight,
  onPrimary = Night,
  secondary = RiverBlueLight,
  tertiary = RiskAmber,
  error = AlertCoral,
  background = Night,
  surface = NightSurface,
  onBackground = WarmWhite,
  onSurface = WarmWhite,
)

private val LightColorScheme = lightColorScheme(
  primary = DeltaTeal,
  onPrimary = WarmWhite,
  secondary = RiverBlue,
  tertiary = RiskAmber,
  error = AlertCoral,
  background = Mist,
  surface = WarmWhite,
  onBackground = Ink,
  onSurface = Ink,
  surfaceVariant = Color(0xFFE3ECEC),
  outline = Color(0xFF6D7D80),
)

@Composable
fun DigitalDeltaTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  content: @Composable () -> Unit,
) {
  val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

  MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}

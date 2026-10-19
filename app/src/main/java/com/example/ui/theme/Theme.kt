package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme =
  darkColorScheme(
    primary = NflLightBlue,
    onPrimary = Color.Black,
    primaryContainer = NflRoyalBlue,
    onPrimaryContainer = Color.White,
    secondary = NflCoral,
    tertiary = NflGreenBright,
    background = NflNavyDark,
    surface = TvCardBackground,
    onBackground = Color.White,
    onSurface = Color.White
  )

private val LightColorScheme =
  lightColorScheme(
    primary = NflRoyalBlue,
    onPrimary = Color.White,
    primaryContainer = NflBlueContainer,
    onPrimaryContainer = NflNavyDark,
    secondary = NflPrimaryBlue,
    onSecondary = Color.White,
    tertiary = NflGreen,
    onTertiary = Color.White,
    background = NflBlueSurface,
    surface = NflCardWhite,
    onBackground = NflNavyDark,
    onSurface = NflNavyDark,
    surfaceVariant = Color(0xFFF0F4F8),
    outline = Color(0xFFCFD8DC)
  )

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  dynamicColor: Boolean = false,
  content: @Composable () -> Unit,
) {
  val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

  MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}

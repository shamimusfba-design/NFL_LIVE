package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

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
    onSurface = Color.White,
    onSurfaceVariant = Color(0xFFCBD5E1),
    surfaceVariant = Color(0xFF1E293B),
    outline = Color(0xFF64748B)
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
    onBackground = Color(0xFF0F172A),
    onSurface = Color(0xFF0F172A),
    onSurfaceVariant = Color(0xFF334155),
    surfaceVariant = Color(0xFFF0F4F8),
    outline = Color(0xFF94A3B8),
    outlineVariant = Color(0xFFCBD5E1)
  )

/**
 * Universal high-contrast text field colors ensuring user inputs (target, efficiency, quality, manpower)
 * have a distinct tinted input box (#EFF6FF / #E0F2FE), bold royal blue borders, and pitch-dark text (#0A0F1D)
 * so text and background never blend into white-on-white across any device or theme.
 */
@Composable
fun nflInputColors(
  containerColor: Color = Color(0xFFEFF6FF),
  focusedContainerColor: Color = Color(0xFFE0F2FE),
  textColor: Color = Color(0xFF0A0F1D),
  cursorColor: Color = Color(0xFF1D4ED8),
  borderColor: Color = Color(0xFF3B82F6),
  focusedBorderColor: Color = Color(0xFF1D4ED8),
  labelColor: Color = Color(0xFF1E293B),
  placeholderColor: Color = Color(0xFF475569)
) = OutlinedTextFieldDefaults.colors(
  focusedTextColor = textColor,
  unfocusedTextColor = textColor,
  disabledTextColor = Color(0xFF1E293B),
  errorTextColor = Color(0xFFB91C1C),
  focusedContainerColor = focusedContainerColor,
  unfocusedContainerColor = containerColor,
  disabledContainerColor = Color(0xFFE2E8F0),
  errorContainerColor = Color(0xFFFEF2F2),
  cursorColor = cursorColor,
  focusedBorderColor = focusedBorderColor,
  unfocusedBorderColor = borderColor,
  disabledBorderColor = Color(0xFF64748B),
  errorBorderColor = Color(0xFFDC2626),
  focusedLabelColor = focusedBorderColor,
  unfocusedLabelColor = labelColor,
  disabledLabelColor = Color(0xFF334155),
  focusedPlaceholderColor = placeholderColor,
  unfocusedPlaceholderColor = placeholderColor,
  disabledPlaceholderColor = Color(0xFF64748B),
  focusedSupportingTextColor = Color(0xFF334155),
  unfocusedSupportingTextColor = Color(0xFF334155),
  disabledSupportingTextColor = Color(0xFF475569)
)

val nflInputTextStyle = TextStyle(
  color = Color(0xFF0A0F1D),
  fontWeight = FontWeight.Bold,
  fontSize = 14.sp
)

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = false,
  dynamicColor: Boolean = false,
  content: @Composable () -> Unit,
) {
  val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

  MaterialTheme(colorScheme = colorScheme, typography = Typography) {
    content()
  }
}


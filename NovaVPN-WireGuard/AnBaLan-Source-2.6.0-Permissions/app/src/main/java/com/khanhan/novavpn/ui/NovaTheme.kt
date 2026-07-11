package com.khanhan.novavpn.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val NovaBackground = Color(0xFF08080B)
val NovaSurface = Color(0xFF17171C)
val NovaSurfaceHigh = Color(0xFF202027)
val NovaAccent = Color(0xFFF12B43)
val NovaAccentDark = Color(0xFF4A111B)
val NovaText = Color(0xFFF7F7FA)
val NovaMuted = Color(0xFF8F8F9A)
val NovaWarning = Color(0xFFFFC95C)
val NovaError = Color(0xFFFF6677)

private val NovaColors = darkColorScheme(
    primary = NovaAccent,
    onPrimary = Color.White,
    primaryContainer = NovaAccentDark,
    onPrimaryContainer = Color(0xFFFFD7DC),
    secondary = Color(0xFF79AAFF),
    background = NovaBackground,
    onBackground = NovaText,
    surface = NovaSurface,
    onSurface = NovaText,
    surfaceVariant = NovaSurfaceHigh,
    onSurfaceVariant = NovaMuted,
    error = NovaError,
    onError = Color(0xFF320006),
    outline = Color(0xFF34343E),
)

private val NovaTypography = Typography(
    displaySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 32.sp,
        lineHeight = 38.sp,
        letterSpacing = (-0.8).sp,
    ),
    headlineSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 22.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.2.sp,
    ),
)

@Composable
fun NovaTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = NovaColors,
        typography = NovaTypography,
        content = content,
    )
}

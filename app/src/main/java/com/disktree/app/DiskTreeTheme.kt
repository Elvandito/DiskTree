package com.disktree.app

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val LightColors = lightColorScheme(
    primary = Color(0xFF0A5F58),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFB6EFE6),
    onPrimaryContainer = Color(0xFF00201D),
    secondary = Color(0xFF3E5555),
    onSecondary = Color.White,
    background = Color(0xFFF4F7F6),
    onBackground = Color(0xFF101F1E),
    surface = Color(0xFFF4F7F6),
    onSurface = Color(0xFF101F1E),
    surfaceVariant = Color(0xFFDCE8E6),
    onSurfaceVariant = Color(0xFF3E5555),
    outline = Color(0xFF6F8583),
    outlineVariant = Color(0xFF758886),
    error = Color(0xFFBA1A1A),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    onError = Color.White,
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF7FD8CD),
    onPrimary = Color(0xFF003733),
    primaryContainer = Color(0xFF00504A),
    onPrimaryContainer = Color(0xFF9CF2E6),
    secondary = Color(0xFFB7C9C6),
    onSecondary = Color(0xFF223331),
    background = Color(0xFF0D1615),
    onBackground = Color(0xFFDDE8E6),
    surface = Color(0xFF0D1615),
    onSurface = Color(0xFFDDE8E6),
    surfaceVariant = Color(0xFF293A38),
    onSurfaceVariant = Color(0xFFB7C9C6),
    outline = Color(0xFF829492),
    outlineVariant = Color(0xFF5A6D6B),
    error = Color(0xFFFFB4AB),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    onError = Color(0xFF690005),
)

private val DiskTreeTypography = Typography(
    headlineSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = (-0.2).sp,
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 26.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 22.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontSize = 16.sp,
        lineHeight = 22.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontSize = 12.sp,
        lineHeight = 16.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.4.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 14.sp,
        letterSpacing = 0.4.sp,
    ),
)

private val DiskTreeShapes = Shapes(
    small = RoundedCornerShape(6.dp),
    medium = RoundedCornerShape(10.dp),
    large = RoundedCornerShape(14.dp),
)

@Composable
fun DiskTreeTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        typography = DiskTreeTypography,
        shapes = DiskTreeShapes,
        content = content,
    )
}

package com.disktree.app

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val LightColors = lightColorScheme(
    primary = Color(0xFF075E58),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFB9EFE7),
    onPrimaryContainer = Color(0xFF063D39),
    secondary = Color(0xFF3E5555),
    onSecondary = Color.White,
    background = Color(0xFFF3F7F6),
    onBackground = Color(0xFF102426),
    surface = Color.White,
    onSurface = Color(0xFF102426),
    surfaceVariant = Color(0xFFDCE8E6),
    onSurfaceVariant = Color(0xFF3E5555),
    outline = Color(0xFF708786),
    error = Color(0xFFB3261E),
    onError = Color.White,
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF73D7CC),
    onPrimary = Color(0xFF003733),
    primaryContainer = Color(0xFF075E58),
    onPrimaryContainer = Color(0xFFB9EFE7),
    secondary = Color(0xFFB7C9C6),
    onSecondary = Color(0xFF0D1615),
    background = Color(0xFF0D1615),
    onBackground = Color(0xFFDDE8E6),
    surface = Color(0xFF15201E),
    onSurface = Color(0xFFDDE8E6),
    surfaceVariant = Color(0xFF293A38),
    onSurfaceVariant = Color(0xFFB7C9C6),
    outline = Color(0xFF829492),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
)

private val DiskTreeShapes = Shapes(
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
)

@Composable
fun DiskTreeTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        shapes = DiskTreeShapes,
        content = content,
    )
}

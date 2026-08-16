package com.local.bulksms.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val GreenColorScheme = lightColorScheme(
    primary = Color(0xFF176B4A),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD9F3E4),
    onPrimaryContainer = Color(0xFF072E1F),
    secondary = Color(0xFF4E685A),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD1E8D9),
    onSecondaryContainer = Color(0xFF112B20),
    tertiary = Color(0xFF49676A),
    background = Color(0xFFF3F8F5),
    onBackground = Color(0xFF17211C),
    surface = Color(0xFFFBFDFB),
    onSurface = Color(0xFF17211C),
    surfaceVariant = Color(0xFFE2EAE5),
    onSurfaceVariant = Color(0xFF414943),
    outline = Color(0xFF707972),
    outlineVariant = Color(0xFFC1C9C3),
    error = Color(0xFFB3261E),
)

@Composable
fun BulkSmsTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = GreenColorScheme,
        content = content,
    )
}

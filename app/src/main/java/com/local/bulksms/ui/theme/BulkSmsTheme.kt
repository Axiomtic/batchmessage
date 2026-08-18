package com.local.bulksms.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LocalSendColorScheme = lightColorScheme(
    primary = Color(0xFF006A67),
    onPrimary = Color.White,
    primaryContainer = Color(0xFF9CF2ED),
    onPrimaryContainer = Color(0xFF00201F),
    inversePrimary = Color(0xFF80D5D1),
    secondary = Color(0xFF4A6361),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFCCE8E5),
    onSecondaryContainer = Color(0xFF051F1E),
    tertiary = Color(0xFF56605F),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFD9E4E2),
    onTertiaryContainer = Color(0xFF141D1C),
    background = Color(0xFFF8FAF9),
    onBackground = Color(0xFF191C1C),
    surface = Color(0xFFF8FAF9),
    onSurface = Color(0xFF191C1C),
    surfaceVariant = Color(0xFFDEE4E3),
    onSurfaceVariant = Color(0xFF3F4948),
    surfaceTint = Color(0xFF006A67),
    inverseSurface = Color(0xFF2E3131),
    inverseOnSurface = Color(0xFFEFF1F0),
    outline = Color(0xFF747C7B),
    outlineVariant = Color(0xFFC3C8C7),
    error = Color(0xFFBA1A1A),
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    scrim = Color.Black,
    surfaceBright = Color(0xFFF8FAF9),
    surfaceContainer = Color(0xFFECEFED),
    surfaceContainerHigh = Color(0xFFE6E9E8),
    surfaceContainerHighest = Color(0xFFE0E3E2),
    surfaceContainerLow = Color(0xFFF2F5F4),
    surfaceContainerLowest = Color.White,
    surfaceDim = Color(0xFFD8DBDA),
    primaryFixed = Color(0xFF9CF2ED),
    primaryFixedDim = Color(0xFF80D5D1),
    onPrimaryFixed = Color(0xFF00201F),
    onPrimaryFixedVariant = Color(0xFF00504E),
    secondaryFixed = Color(0xFFCCE8E5),
    secondaryFixedDim = Color(0xFFB0CCC9),
    onSecondaryFixed = Color(0xFF051F1E),
    onSecondaryFixedVariant = Color(0xFF324B49),
    tertiaryFixed = Color(0xFFD9E4E2),
    tertiaryFixedDim = Color(0xFFBDC8C6),
    onTertiaryFixed = Color(0xFF141D1C),
    onTertiaryFixedVariant = Color(0xFF3E4847),
)

@Composable
fun BulkSmsTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LocalSendColorScheme,
        content = content,
    )
}

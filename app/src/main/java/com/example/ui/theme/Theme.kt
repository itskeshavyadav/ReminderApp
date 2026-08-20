package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = CleanPrimaryBlueDark,
    onPrimary = CleanOnPrimaryDark,
    primaryContainer = CleanPrimaryContainerDark,
    onPrimaryContainer = CleanOnPrimaryContainerDark,
    secondary = CleanSecondary,
    onSecondary = CleanOnSecondary,
    secondaryContainer = CleanSecondaryContainer,
    onSecondaryContainer = CleanOnSecondaryContainer,
    tertiary = CleanTertiary,
    onTertiary = CleanOnTertiary,
    background = CleanCanvasDark,
    surface = CleanSurfaceDark,
    surfaceVariant = CleanSurfaceVariantDark,
    onBackground = CleanOnSurfaceDark,
    onSurface = CleanOnSurfaceDark,
    onSurfaceVariant = CleanOnSurfaceMutedDark,
    outline = CleanOutlineLight.copy(alpha = 0.5f)
)

private val LightColorScheme = lightColorScheme(
    primary = CleanPrimaryBlue,
    onPrimary = CleanOnPrimary,
    primaryContainer = CleanPrimaryContainerBlue,
    onPrimaryContainer = CleanOnPrimaryContainerBlue,
    secondary = CleanSecondary,
    onSecondary = CleanOnSecondary,
    secondaryContainer = CleanSecondaryContainer,
    onSecondaryContainer = CleanOnSecondaryContainer,
    tertiary = CleanTertiary,
    onTertiary = CleanOnTertiary,
    tertiaryContainer = CleanTertiaryContainer,
    onTertiaryContainer = CleanOnTertiaryContainer,
    background = CleanCanvasLight,
    surface = CleanSurfaceLight,
    surfaceVariant = CleanSurfaceVariantLight,
    onBackground = CleanOnSurfaceLight,
    onSurface = CleanOnSurfaceLight,
    onSurfaceVariant = CleanOnSurfaceSubtleLight,
    outline = CleanOutlineLight
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false, // Keep clean brand colors crisp
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}

package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = TechBlueDarkPrimary,
    onPrimary = TechBlueDarkOnPrimary,
    primaryContainer = TechBlueDarkContainer,
    onPrimaryContainer = TechBlueDarkOnContainer,
    secondary = TechTealDarkSecondary,
    onSecondary = TechTealDarkOnSecondary,
    secondaryContainer = TechTealDarkContainer,
    onSecondaryContainer = TechTealDarkOnContainer,
    background = TechDarkBackground,
    surface = TechDarkSurface,
    surfaceVariant = TechDarkSurfaceVariant,
    onBackground = TechDarkOnSurface,
    onSurface = TechDarkOnSurface,
    onSurfaceVariant = TechDarkOnSurfaceVariant,
    outline = TechDarkOutline,
    outlineVariant = TechDarkOutlineVariant
)

private val LightColorScheme = lightColorScheme(
    primary = TechBlueLightPrimary,
    onPrimary = TechBlueLightOnPrimary,
    primaryContainer = TechBlueLightContainer,
    onPrimaryContainer = TechBlueLightOnContainer,
    secondary = TechTealLightSecondary,
    onSecondary = TechTealLightOnSecondary,
    secondaryContainer = TechTealLightContainer,
    onSecondaryContainer = TechTealLightOnContainer,
    background = TechLightBackground,
    surface = TechLightSurface,
    surfaceVariant = TechLightSurfaceVariant,
    onBackground = TechLightOnSurface,
    onSurface = TechLightOnSurface,
    onSurfaceVariant = TechLightOnSurfaceVariant,
    outline = TechLightOutline,
    outlineVariant = TechLightOutlineVariant
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}

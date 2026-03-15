package com.jimscope.vendel.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColorScheme = lightColorScheme(
    primary = VendelForeground,
    onPrimary = VendelBackground,
    primaryContainer = VendelBrand,
    onPrimaryContainer = VendelForeground,
    secondary = VendelSecondary,
    onSecondary = VendelSecondaryForeground,
    secondaryContainer = VendelSecondary,
    onSecondaryContainer = VendelForeground,
    tertiary = VendelBrand,
    onTertiary = VendelForeground,
    tertiaryContainer = VendelBrandLight,
    onTertiaryContainer = VendelForeground,
    background = VendelBackground,
    onBackground = VendelForeground,
    surface = VendelSurface,
    onSurface = VendelForeground,
    surfaceVariant = VendelSecondary,
    onSurfaceVariant = VendelSecondaryForeground,
    outline = VendelBorder,
    error = VendelDestructive,
    onError = VendelBackground,
)

private val DarkColorScheme = darkColorScheme(
    primary = VendelDarkForeground,
    onPrimary = VendelDarkBackground,
    primaryContainer = VendelBrandDark,
    onPrimaryContainer = VendelDarkForeground,
    secondary = VendelDarkSecondary,
    onSecondary = VendelDarkForeground,
    secondaryContainer = VendelDarkSecondary,
    onSecondaryContainer = VendelDarkForeground,
    tertiary = VendelBrand,
    onTertiary = VendelDarkBackground,
    tertiaryContainer = VendelBrandDark,
    onTertiaryContainer = VendelDarkForeground,
    background = VendelDarkBackground,
    onBackground = VendelDarkForeground,
    surface = VendelDarkSurface,
    onSurface = VendelDarkForeground,
    surfaceVariant = VendelDarkSecondary,
    onSurfaceVariant = VendelDarkForeground,
    outline = VendelDarkBorder,
    error = VendelDestructive,
    onError = VendelDarkForeground,
)

@Composable
fun VendelTheme(
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

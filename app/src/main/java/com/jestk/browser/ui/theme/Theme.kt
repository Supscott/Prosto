package com.jestk.browser.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import com.jestk.browser.domain.model.ThemeMode

private val DarkColorScheme = darkColorScheme(
    primary = JestkPrimary,
    onPrimary = JestkOnPrimary,
    primaryContainer = JestkPrimaryContainer,
    onPrimaryContainer = JestkOnPrimaryContainer,
    surface = DarkSurface,
    onSurface = DarkOnSurface,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkOnSurfaceVariant,
    background = DarkBackground,
    onBackground = DarkOnSurface
)

private val LightColorScheme = lightColorScheme(
    primary = JestkPrimary,
    onPrimary = JestkOnPrimary,
    primaryContainer = JestkPrimaryContainer,
    onPrimaryContainer = JestkOnPrimaryContainer,
    surface = LightSurface,
    onSurface = LightOnSurface,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightOnSurfaceVariant,
    background = LightBackground,
    onBackground = LightOnSurface
)

@Composable
fun JestkBrowserTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    content: @Composable () -> Unit
) {
    val darkTheme = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
    }

    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}

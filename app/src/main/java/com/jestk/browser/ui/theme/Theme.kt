package com.jestk.browser.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import com.jestk.browser.domain.model.ThemeMode
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = LiquidPrimaryFixedDim,
    onPrimary = Color(0xFF003258),
    primaryContainer = Color(0xFF1B4975),
    onPrimaryContainer = LiquidPrimaryFixed,
    secondary = LiquidSecondaryFixedDim,
    onSecondary = Color(0xFF4A2818),
    secondaryContainer = Color(0xFF663C27),
    onSecondaryContainer = LiquidSecondaryFixed,
    tertiary = LiquidTertiaryFixedDim,
    onTertiary = Color(0xFF502334),
    tertiaryContainer = Color(0xFF6A3748),
    onTertiaryContainer = LiquidTertiaryFixed,
    surface = Color(0xFF1E1B1C),
    onSurface = Color(0xFFF6EFF0),
    surfaceVariant = Color(0xFF2C2829),
    onSurfaceVariant = Color(0xFFD0C5C6),
    background = Color(0xFF161314),
    onBackground = Color(0xFFF6EFF0),
    outline = Color(0xFF988E90),
    outlineVariant = Color(0xFF4D4546),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    inverseSurface = Color(0xFFF6EFF0),
    inverseOnSurface = Color(0xFF1E1B1C),
    inversePrimary = LiquidPrimary
)

private val LightColorScheme = lightColorScheme(
    primary = LiquidPrimary,
    onPrimary = LiquidOnPrimary,
    primaryContainer = LiquidPrimaryContainer,
    onPrimaryContainer = LiquidOnPrimaryContainer,
    secondary = LiquidSecondary,
    onSecondary = LiquidOnSecondary,
    secondaryContainer = LiquidSecondaryContainer,
    onSecondaryContainer = LiquidOnSecondaryContainer,
    tertiary = LiquidTertiary,
    onTertiary = LiquidOnTertiary,
    tertiaryContainer = LiquidTertiaryContainer,
    onTertiaryContainer = LiquidOnTertiaryContainer,
    surface = LiquidSurface,
    onSurface = LiquidOnSurface,
    surfaceVariant = LiquidSurfaceVariant,
    onSurfaceVariant = LiquidOnSurfaceVariant,
    background = LiquidSurface,
    onBackground = LiquidOnSurface,
    outline = LiquidOutline,
    outlineVariant = LiquidOutlineVariant,
    error = LiquidError,
    onError = LiquidOnError,
    errorContainer = LiquidErrorContainer,
    onErrorContainer = LiquidOnErrorContainer,
    inverseSurface = LiquidInverseSurface,
    inverseOnSurface = LiquidInverseOnSurface,
    inversePrimary = LiquidPrimaryFixedDim
)

val LocalIsDarkTheme = compositionLocalOf { false }

@Composable
fun isAppDarkTheme(themeMode: ThemeMode = ThemeMode.SYSTEM): Boolean {
    return when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
    }
}

@Composable
fun JestkBrowserTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    content: @Composable () -> Unit
) {
    val darkTheme = isAppDarkTheme(themeMode)

    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    CompositionLocalProvider(LocalIsDarkTheme provides darkTheme) {
        MaterialTheme(
            colorScheme = colorScheme,
            content = content
        )
    }
}


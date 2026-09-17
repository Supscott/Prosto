package com.jestk.browser.ui.theme

import android.os.Build
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.jestk.browser.domain.model.ThemeMode
import dev.chrisbanes.haze.HazeDefaults
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.haze
import dev.chrisbanes.haze.hazeChild

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
val LocalHazeState = compositionLocalOf<HazeState?> { null }

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
    hazeState: HazeState? = null,
    content: @Composable () -> Unit
) {
    val darkTheme = isAppDarkTheme(themeMode)
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    CompositionLocalProvider(
        LocalIsDarkTheme provides darkTheme,
        LocalHazeState provides hazeState
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            content = content
        )
    }
}

// ==========================================
// Liquid Design System Dimensions & Shapes
// ==========================================
object LiquidShapes {
    /** 18dp for cards, squircles, and popup menus */
    val Card = RoundedCornerShape(18.dp)
    /** 22dp for smaller capsules, active tabs, and dialog buttons */
    val ButtonPill = RoundedCornerShape(22.dp)
    /** 24dp for dialogs / search inputs */
    val Capsule = RoundedCornerShape(24.dp)
    /** 28dp for bottom sheet tops */
    val BottomSheet = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    /** 29dp for full floating capsule dock */
    val Dock = RoundedCornerShape(29.dp)
}

object LiquidBorders {
    fun frostedBorder(isDark: Boolean, alpha: Float? = null, width: Dp = 1.dp): BorderStroke {
        val resolvedAlpha = alpha ?: if (isDark) 0.14f else 0.75f
        val color = Color.White.copy(alpha = resolvedAlpha)
        return BorderStroke(width, color)
    }
}

/**
 * Reusable frosted glass / backdrop blur extension modifier for the Liquid Pastel Sunset design system.
 * When [hazeState] is provided, applies real-time hardware-accelerated backdrop blur to content behind the element.
 * Supports graceful fallback to semi-transparent tinted surface + white/light stroke border when blur is unavailable.
 */
fun Modifier.liquidFrostedGlass(
    shape: Shape = LiquidShapes.Card,
    isDark: Boolean = false,
    blurRadius: Dp = 16.dp,
    tintColor: Color? = null,
    alpha: Float? = null,
    borderColor: Color? = null,
    borderWidth: Dp = 1.dp,
    hazeState: HazeState? = null
): Modifier {
    val defaultAlpha = if (isDark) 0.86f else 0.82f
    val resolvedAlpha = alpha ?: defaultAlpha
    val baseTint = tintColor ?: if (isDark) Color(0xFF1E1B1C) else Color(0xFFFFF8F8)
    val tintedBg = baseTint.copy(alpha = resolvedAlpha)

    val defaultBorderColor = if (isDark) {
        Color.White.copy(alpha = 0.14f)
    } else {
        Color.White.copy(alpha = 0.75f)
    }
    val resolvedBorder = BorderStroke(borderWidth, borderColor ?: defaultBorderColor)

    val baseModifier = this.clip(shape)

    val glassModifier = if (hazeState != null) {
        baseModifier.hazeChild(
            state = hazeState,
            style = HazeStyle(
                backgroundColor = baseTint,
                tint = HazeTint(tintedBg),
                blurRadius = blurRadius
            )
        )
    } else {
        baseModifier.background(color = tintedBg, shape = shape)
    }

    return glassModifier.border(border = resolvedBorder, shape = shape)
}

/**
 * Frosted glass container box composable supporting true backdrop blur via Haze
 * with fallback to semi-transparent surface + border on older APIs.
 */
@Composable
fun LiquidFrostedGlassBox(
    modifier: Modifier = Modifier,
    shape: Shape = LiquidShapes.Card,
    isDark: Boolean = LocalIsDarkTheme.current,
    blurRadius: Dp = 16.dp,
    tintColor: Color? = null,
    alpha: Float? = null,
    borderColor: Color? = null,
    borderWidth: Dp = 1.dp,
    hazeState: HazeState? = LocalHazeState.current,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier.liquidFrostedGlass(
            shape = shape,
            isDark = isDark,
            blurRadius = blurRadius,
            tintColor = tintColor,
            alpha = alpha,
            borderColor = borderColor,
            borderWidth = borderWidth,
            hazeState = hazeState
        )
    ) {
        content()
    }
}



package com.jestk.browser.ui.theme

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TileMode
import kotlin.math.max

// ==========================================
// Serene Canvas Light Tokens (DESIGN.md)
// ==========================================

val SerenePrimary = Color(0xFF005893)
val SereneBrandPrimary = Color(0xFF2271B3)
val SereneOnPrimary = Color(0xFFFFFFFF)
val SerenePrimaryContainer = Color(0xFF2271B3)
val SereneOnPrimaryContainer = Color(0xFFEAF1FF)
val SereneAccent = Color(0xFF60A5FA)

val SereneSurface = Color(0xFFFAF8FF)
val SereneSurfaceDim = Color(0xFFD2D9F4)
val SereneSurfaceBright = Color(0xFFFAF8FF)
val SereneSurfaceContainerLowest = Color(0xFFFFFFFF)
val SereneSurfaceContainerLow = Color(0xFFF2F3FF)
val SereneSurfaceContainer = Color(0xFFEAEDFF)
val SereneSurfaceContainerHigh = Color(0xFFE2E7FF)
val SereneSurfaceContainerHighest = Color(0xFFDAE2FD)

val SereneOnSurface = Color(0xFF131B2E)
val SereneOnSurfaceVariant = Color(0xFF414750)
val SereneOutline = Color(0xFF717781)
val SereneOutlineVariant = Color(0xFFC1C7D2)
val SereneInverseSurface = Color(0xFF283044)
val SereneInverseOnSurface = Color(0xFFEEF0FF)
val SereneSurfaceTint = Color(0xFF0062A2)
val SereneInversePrimary = Color(0xFF9DCAFF)

val SereneSecondary = Color(0xFF0060AC)
val SereneOnSecondary = Color(0xFFFFFFFF)
val SereneSecondaryContainer = Color(0xFF64A8FE)
val SereneOnSecondaryContainer = Color(0xFF003C70)

val SereneTertiary = Color(0xFF4F565A)
val SereneOnTertiary = Color(0xFFFFFFFF)
val SereneTertiaryContainer = Color(0xFF686E73)
val SereneOnTertiaryContainer = Color(0xFFEBF1F6)

val SereneError = Color(0xFFBA1A1A)
val SereneOnError = Color(0xFFFFFFFF)
val SereneErrorContainer = Color(0xFFFFDAD6)
val SereneOnErrorContainer = Color(0xFF93000A)

val SerenePrimaryFixed = Color(0xFFD1E4FF)
val SerenePrimaryFixedDim = Color(0xFF9DCAFF)
val SereneOnPrimaryFixed = Color(0xFF001D35)
val SereneOnPrimaryFixedVariant = Color(0xFF00497C)

val SereneSecondaryFixed = Color(0xFFD4E3FF)
val SereneSecondaryFixedDim = Color(0xFFA4C9FF)
val SereneOnSecondaryFixed = Color(0xFF001C39)
val SereneOnSecondaryFixedVariant = Color(0xFF004883)

// Architectural Layer Tokens
val SereneCanvasFloor = Color(0xFFF1F7FC) // Level 0 Base layer
val SereneCardSurface = Color(0xFFFFFFFF) // Level 1 Card surface
val SereneDarkText = Color(0xFF0F172A)
val SereneMutedSlate = Color(0xFF64748B)

// Atmospheric Cerulean Glows
val SereneSkyGlow = Color(0xFFE0F2FE)
val SerenePaleBlue = Color(0xFFEAF1FF)

// ==========================================
// Serene Canvas Dark Tokens (Deep Midnight Navy & Slate)
// ==========================================

val DarkBackground = Color(0xFF0F172A)
val DarkSurface = Color(0xFF1E293B)
val DarkSurfaceVariant = Color(0xFF334155)
val DarkOnSurface = Color(0xFFF1F5F9)
val DarkOnSurfaceVariant = Color(0xFF94A3B8)
val DarkPrimary = Color(0xFF60A5FA)
val DarkPrimaryContainer = Color(0xFF1E3A8A)
val DarkOutline = Color(0xFF475569)
val DarkOutlineVariant = Color(0xFF334155)
val DarkInverseSurface = Color(0xFFF1F5F9)
val DarkInverseOnSurface = Color(0xFF0F172A)
val DarkError = Color(0xFFFFB4AB)
val DarkOnError = Color(0xFF690005)
val DarkErrorContainer = Color(0xFF93000A)
val DarkOnErrorContainer = Color(0xFFFFDAD6)
val DarkDockBackground = Color(0xFF1E293B)
val DarkDockButton = Color(0xFF94A3B8)

// ==========================================
// Serene Gradients & Brushes (Cool Sky Theme)
// ==========================================

val SereneActiveTabBorderBrush = Brush.linearGradient(
    colors = listOf(
        Color(0xFF60A5FA),
        Color(0xFF2271B3),
    ),
)

val SereneAddTabGradientBrush = Brush.linearGradient(
    colors = listOf(
        Color(0xFF2271B3),
        Color(0xFF60A5FA),
    ),
)

val SereneSkySwitchBrush = Brush.horizontalGradient(
    colors = listOf(
        Color(0xFF2271B3),
        Color(0xFF60A5FA),
        Color(0xFF93C5FD),
    ),
)

val SereneCardBorderBrush = Brush.verticalGradient(
    colors = listOf(
        Color.White.copy(alpha = 0.85f),
        Color(0xFFE2E8F0).copy(alpha = 0.80f),
    ),
)

// Backward-compatible brush aliases
val ActiveTabBorderBrush = SereneActiveTabBorderBrush
val AddTabGradientBrush = SereneAddTabGradientBrush
val SunsetSwitchBrush = SereneSkySwitchBrush

val DuckDuckGoBadgeBrush = Brush.linearGradient(
    colors = listOf(
        Color(0xFFD1E4FF),
        Color(0xFFEAF1FF),
    ),
)

val CardPreviewRoseBrush = Brush.linearGradient(
    colors = listOf(
        Color(0xFFE0F2FE).copy(alpha = 0.80f),
        Color(0xFFBAE6FD).copy(alpha = 0.50f),
    ),
)

val CardPreviewPeachBrush = Brush.linearGradient(
    colors = listOf(
        Color(0xFFD1E4FF).copy(alpha = 0.80f),
        Color(0xFF93C5FD).copy(alpha = 0.50f),
    ),
)

val CardPreviewBlueBrush = Brush.linearGradient(
    colors = listOf(
        Color(0xFFBAE6FD).copy(alpha = 0.80f),
        Color(0xFFFFFFFF).copy(alpha = 0.90f),
    ),
)

/**
 * Extension modifier that renders the ambient Serene Canvas sky-glow multi-point gradients.
 * Uses soft airy sky undertones, serene base (#F1F7FC / #FAF8FF) with delicate pale cerulean
 * ambient glows (#E0F2FE) at top, without warm peach/orange sunset circles.
 */
fun Modifier.sereneSkyBackground(isDark: Boolean = false): Modifier = this.drawBehind {
    val canvasWidth = size.width
    val canvasHeight = size.height
    val maxDim = max(canvasWidth, canvasHeight)
    if (maxDim <= 0f) return@drawBehind

    if (!isDark) {
        // Base canvas: Serene pale blue-grey floor
        drawRect(color = SereneCanvasFloor)

        // Delicate pale cerulean ambient glow at top-left (20%, 10%)
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(SereneSkyGlow.copy(alpha = 0.55f), Color.Transparent),
                center = Offset(canvasWidth * 0.20f, canvasHeight * 0.10f),
                radius = maxDim * 0.70f,
                tileMode = TileMode.Clamp,
            ),
        )

        // Soft sky-wash glow at top-right (85%, 18%)
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(SerenePaleBlue.copy(alpha = 0.45f), Color.Transparent),
                center = Offset(canvasWidth * 0.85f, canvasHeight * 0.18f),
                radius = maxDim * 0.65f,
                tileMode = TileMode.Clamp,
            ),
        )

        // Subtle soft blue illumination at bottom-center (50%, 90%)
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(Color(0xFFE2E7FF).copy(alpha = 0.30f), Color.Transparent),
                center = Offset(canvasWidth * 0.50f, canvasHeight * 0.90f),
                radius = maxDim * 0.70f,
                tileMode = TileMode.Clamp,
            ),
        )
    } else {
        // Dark Base Canvas: Deep Midnight Navy
        drawRect(color = DarkBackground)

        // Deep cerulean glow at top-left
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(Color(0xFF1E3A8A).copy(alpha = 0.20f), Color.Transparent),
                center = Offset(canvasWidth * 0.20f, canvasHeight * 0.10f),
                radius = maxDim * 0.70f,
                tileMode = TileMode.Clamp,
            ),
        )

        // Subtle deep slate-blue glow at top-right
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(Color(0xFF1E293B).copy(alpha = 0.20f), Color.Transparent),
                center = Offset(canvasWidth * 0.85f, canvasHeight * 0.18f),
                radius = maxDim * 0.65f,
                tileMode = TileMode.Clamp,
            ),
        )

        // Subtle bottom midnight illumination
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(Color(0xFF172554).copy(alpha = 0.15f), Color.Transparent),
                center = Offset(canvasWidth * 0.50f, canvasHeight * 0.90f),
                radius = maxDim * 0.70f,
                tileMode = TileMode.Clamp,
            ),
        )
    }
}

/**
 * Backward compatibility alias for [sereneSkyBackground].
 */
fun Modifier.liquidSunsetBackground(isDark: Boolean = false): Modifier = sereneSkyBackground(isDark)

// ==========================================
// Backward-Compatibility Tokens for Liquid System
// ==========================================

val LiquidSurface = SereneSurface
val LiquidSurfaceDim = SereneSurfaceDim
val LiquidSurfaceBright = SereneSurfaceBright
val LiquidSurfaceContainerLowest = SereneSurfaceContainerLowest
val LiquidSurfaceContainerLow = SereneSurfaceContainerLow
val LiquidSurfaceContainer = SereneSurfaceContainer
val LiquidSurfaceContainerHigh = SereneSurfaceContainerHigh
val LiquidSurfaceContainerHighest = SereneSurfaceContainerHighest
val LiquidSurfaceVariant = SereneSurfaceContainerHighest

val LiquidOnSurface = SereneOnSurface
val LiquidOnSurfaceVariant = SereneOnSurfaceVariant
val LiquidOutline = SereneOutline
val LiquidOutlineVariant = SereneOutlineVariant
val LiquidInverseSurface = SereneInverseSurface
val LiquidInverseOnSurface = SereneInverseOnSurface

val LiquidPrimary = SerenePrimary
val LiquidOnPrimary = SereneOnPrimary
val LiquidPrimaryContainer = SerenePrimaryContainer
val LiquidOnPrimaryContainer = SereneOnPrimaryContainer
val LiquidPrimaryFixed = SerenePrimaryFixed
val LiquidPrimaryFixedDim = SerenePrimaryFixedDim
val LiquidOnPrimaryFixed = SereneOnPrimaryFixed
val LiquidOnPrimaryFixedVariant = SereneOnPrimaryFixedVariant

val LiquidSecondary = SereneSecondary
val LiquidOnSecondary = SereneOnSecondary
val LiquidSecondaryContainer = SereneSecondaryContainer
val LiquidOnSecondaryContainer = SereneOnSecondaryContainer
val LiquidSecondaryFixed = SereneSecondaryFixed
val LiquidSecondaryFixedDim = SereneSecondaryFixedDim
val LiquidOnSecondaryFixed = SereneOnSecondaryFixed
val LiquidOnSecondaryFixedVariant = SereneOnSecondaryFixedVariant

val LiquidTertiary = SereneTertiary
val LiquidOnTertiary = SereneOnTertiary
val LiquidTertiaryContainer = SereneTertiaryContainer
val LiquidOnTertiaryContainer = SereneOnTertiaryContainer
val LiquidTertiaryFixed = SerenePrimaryFixed
val LiquidTertiaryFixedDim = SerenePrimaryFixedDim
val LiquidOnTertiaryFixed = SereneOnPrimaryFixed
val LiquidOnTertiaryFixedVariant = SereneOnPrimaryFixedVariant

val LiquidError = SereneError
val LiquidOnError = SereneOnError
val LiquidErrorContainer = SereneErrorContainer
val LiquidOnErrorContainer = SereneOnErrorContainer

// Pastel accents mapped to cool serene sky palette
val PastelPeach = Color(0xFFBAE6FD)
val PastelWarmOrange = Color(0xFFBAE6FD)
val PastelRose = Color(0xFFBFDBFE)
val PastelBlue = Color(0xFF93C5FD)
val PastelBlueContainer = Color(0xFF60A5FA)
val PastelPink = Color(0xFFDBEAFE)
val PastelRaspberry = Color(0xFF3B82F6)

// Old theme tokens backward-compatibility
val JestkPrimary = SerenePrimary
val JestkOnPrimary = SereneOnPrimary
val JestkPrimaryContainer = SerenePrimaryContainer
val JestkOnPrimaryContainer = SereneOnPrimaryContainer

val LightBackground = SereneCanvasFloor
val LightSurface = SereneCardSurface
val LightSurfaceVariant = SereneSurfaceContainerHigh
val LightOnSurface = SereneOnSurface
val LightOnSurfaceVariant = SereneOnSurfaceVariant
val LightDockBackground = SereneCardSurface
val LightDockButton = SereneMutedSlate

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
// Primary #087E78 on a calm mint canvas #E9F6F2
// ==========================================

val SerenePrimary = Color(0xFF087E78)
val SereneBrandPrimary = Color(0xFF087E78)
val SereneOnPrimary = Color(0xFFFFFFFF)
val SerenePrimaryContainer = Color(0xFFA5F2E9)
val SereneOnPrimaryContainer = Color(0xFF00201D)
val SereneAccent = Color(0xFF14A79D)

val SereneSurface = Color(0xFFFFFFFF)
val SereneSurfaceDim = Color(0xFFD2DDDA)
val SereneSurfaceBright = Color(0xFFFFFFFF)
val SereneSurfaceContainerLowest = Color(0xFFFFFFFF)
val SereneSurfaceContainerLow = Color(0xFFF2FAF7)
val SereneSurfaceContainer = Color(0xFFECF5F2)
val SereneSurfaceContainerHigh = Color(0xFFE6EFEC)
val SereneSurfaceContainerHighest = Color(0xFFDEE9E6)

val SereneOnSurface = Color(0xFF171D1B)
val SereneOnSurfaceVariant = Color(0xFF3F4947)
val SereneOutline = Color(0xFF6F7977)
val SereneOutlineVariant = Color(0xFFBEC9C6)
val SereneInverseSurface = Color(0xFF2B3230)
val SereneInverseOnSurface = Color(0xFFEFF1F0)
val SereneSurfaceTint = Color(0xFF087E78)
val SereneInversePrimary = Color(0xFF56DBCF)

val SereneSecondary = Color(0xFF4A6360)
val SereneOnSecondary = Color(0xFFFFFFFF)
val SereneSecondaryContainer = Color(0xFFCCE8E3)
val SereneOnSecondaryContainer = Color(0xFF06201D)

val SereneTertiary = Color(0xFF416277)
val SereneOnTertiary = Color(0xFFFFFFFF)
val SereneTertiaryContainer = Color(0xFFC4E8FF)
val SereneOnTertiaryContainer = Color(0xFF001E31)

val SereneError = Color(0xFFBA1A1A)
val SereneOnError = Color(0xFFFFFFFF)
val SereneErrorContainer = Color(0xFFFFDAD6)
val SereneOnErrorContainer = Color(0xFF93000A)

val SerenePrimaryFixed = Color(0xFFA5F2E9)
val SerenePrimaryFixedDim = Color(0xFF83D5CB)
val SereneOnPrimaryFixed = Color(0xFF00201D)
val SereneOnPrimaryFixedVariant = Color(0xFF003732)

val SereneSecondaryFixed = Color(0xFFCCE8E3)
val SereneSecondaryFixedDim = Color(0xFFB1CCC8)
val SereneOnSecondaryFixed = Color(0xFF06201D)
val SereneOnSecondaryFixedVariant = Color(0xFF1D3532)

// Architectural Layer Tokens
val SereneCanvasFloor = Color(0xFFE9F6F2) // Level 0 Base layer
val SereneCardSurface = Color(0xFFFFFFFF) // Level 1 Card surface
val SereneDarkText = Color(0xFF16201E)
val SereneMutedSlate = Color(0xFF5F6E6C)

// Atmospheric Mint Glows
val SereneSkyGlow = Color(0xFFCFF3EC)
val SerenePaleBlue = Color(0xFFDFF7F3)

// ==========================================
// Serene Canvas Dark Tokens (Deep Teal Night & Slate)
// ==========================================

val DarkBackground = Color(0xFF0E1513)
val DarkSurface = Color(0xFF171D1C)
val DarkSurfaceVariant = Color(0xFF3F4947)
val DarkOnSurface = Color(0xFFDEE4E2)
val DarkOnSurfaceVariant = Color(0xFFBEC9C6)
val DarkPrimary = Color(0xFF56DBCF)
val DarkPrimaryContainer = Color(0xFF00504A)
val DarkOutline = Color(0xFF899391)
val DarkOutlineVariant = Color(0xFF3F4947)
val DarkInverseSurface = Color(0xFFDEE4E2)
val DarkInverseOnSurface = Color(0xFF2B3230)
val DarkError = Color(0xFFFFB4AB)
val DarkOnError = Color(0xFF690005)
val DarkErrorContainer = Color(0xFF93000A)
val DarkOnErrorContainer = Color(0xFFFFDAD6)

// Dark Palette Tokens
val DarkDockSurface = Color(0xFF0C1A18)
val DarkCanvasBase = Color(0xFF08100F)
val DarkTextHighContrast = Color(0xFFF8FAFC)
val DarkDockBackground = DarkDockSurface
val DarkDockButton = Color(0xFF9FB0AD)

// ==========================================
// Serene Gradients & Brushes (Calm Mint Theme)
// ==========================================

val SereneActiveTabBorderBrush =
    Brush.linearGradient(
        colors =
            listOf(
                Color(0xFF56DBCF),
                Color(0xFF087E78),
            ),
    )

val SereneAddTabGradientBrush =
    Brush.linearGradient(
        colors =
            listOf(
                Color(0xFF087E78),
                Color(0xFF56DBCF),
            ),
    )

val SereneSkySwitchBrush =
    Brush.horizontalGradient(
        colors =
            listOf(
                Color(0xFF087E78),
                Color(0xFF2BB3A8),
                Color(0xFFA5F2E9),
            ),
    )

val SereneCardBorderBrush =
    Brush.verticalGradient(
        colors =
            listOf(
                Color.White.copy(alpha = 0.85f),
                Color(0xFFDCE6E3).copy(alpha = 0.80f),
            ),
    )

// Backward-compatible brush aliases
val ActiveTabBorderBrush = SereneActiveTabBorderBrush
val AddTabGradientBrush = SereneAddTabGradientBrush
val SunsetSwitchBrush = SereneSkySwitchBrush

val DuckDuckGoBadgeBrush =
    Brush.linearGradient(
        colors =
            listOf(
                Color(0xFFA5F2E9),
                Color(0xFFDFF7F3),
            ),
    )

val CardPreviewRoseBrush =
    Brush.linearGradient(
        colors =
            listOf(
                Color(0xFFDFF7F3).copy(alpha = 0.80f),
                Color(0xFFB9EDE6).copy(alpha = 0.50f),
            ),
    )

val CardPreviewPeachBrush =
    Brush.linearGradient(
        colors =
            listOf(
                Color(0xFFA5F2E9).copy(alpha = 0.80f),
                Color(0xFF6FD9CE).copy(alpha = 0.50f),
            ),
    )

val CardPreviewBlueBrush =
    Brush.linearGradient(
        colors =
            listOf(
                Color(0xFF6FD9CE).copy(alpha = 0.80f),
                Color(0xFFFFFFFF).copy(alpha = 0.90f),
            ),
    )

/**
 * Extension modifier that renders the ambient Serene Canvas mint-glow multi-point gradients.
 * Uses a soft mint base (#E9F6F2) with delicate teal ambient glows (#CFF3EC) at the top.
 */
fun Modifier.sereneSkyBackground(isDark: Boolean = false): Modifier =
    this.drawBehind {
        val canvasWidth = size.width
        val canvasHeight = size.height
        val maxDim = max(canvasWidth, canvasHeight)
        if (maxDim <= 0f) return@drawBehind

        if (!isDark) {
            // Base canvas: calm mint floor
            drawRect(color = SereneCanvasFloor)

            // Delicate mint ambient glow at top-left (20%, 10%)
            drawRect(
                brush =
                    Brush.radialGradient(
                        colors = listOf(SereneSkyGlow.copy(alpha = 0.55f), Color.Transparent),
                        center = Offset(canvasWidth * 0.20f, canvasHeight * 0.10f),
                        radius = maxDim * 0.70f,
                        tileMode = TileMode.Clamp,
                    ),
            )

            // Soft glow at top-right (85%, 18%)
            drawRect(
                brush =
                    Brush.radialGradient(
                        colors = listOf(SerenePaleBlue.copy(alpha = 0.45f), Color.Transparent),
                        center = Offset(canvasWidth * 0.85f, canvasHeight * 0.18f),
                        radius = maxDim * 0.65f,
                        tileMode = TileMode.Clamp,
                    ),
            )

            // Subtle soft illumination at bottom-center (50%, 90%)
            drawRect(
                brush =
                    Brush.radialGradient(
                        colors = listOf(Color(0xFFDFF7F3).copy(alpha = 0.30f), Color.Transparent),
                        center = Offset(canvasWidth * 0.50f, canvasHeight * 0.90f),
                        radius = maxDim * 0.70f,
                        tileMode = TileMode.Clamp,
                    ),
            )
        } else {
            // Dark Base Canvas: Deep Teal Night
            drawRect(color = DarkBackground)

            // Deep teal glow at top-left
            drawRect(
                brush =
                    Brush.radialGradient(
                        colors = listOf(Color(0xFF00504A).copy(alpha = 0.25f), Color.Transparent),
                        center = Offset(canvasWidth * 0.20f, canvasHeight * 0.10f),
                        radius = maxDim * 0.70f,
                        tileMode = TileMode.Clamp,
                    ),
            )

            // Subtle deep slate-teal glow at top-right
            drawRect(
                brush =
                    Brush.radialGradient(
                        colors = listOf(Color(0xFF1E2B29).copy(alpha = 0.30f), Color.Transparent),
                        center = Offset(canvasWidth * 0.85f, canvasHeight * 0.18f),
                        radius = maxDim * 0.65f,
                        tileMode = TileMode.Clamp,
                    ),
            )

            // Subtle bottom illumination
            drawRect(
                brush =
                    Brush.radialGradient(
                        colors = listOf(Color(0xFF0A2B28).copy(alpha = 0.25f), Color.Transparent),
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

// Pastel accents mapped to the calm mint palette
val PastelPeach = Color(0xFFB9EDE6)
val PastelWarmOrange = Color(0xFFB9EDE6)
val PastelRose = Color(0xFFA5F2E9)
val PastelBlue = Color(0xFF6FD9CE)
val PastelBlueContainer = Color(0xFF14A79D)
val PastelPink = Color(0xFFDFF7F3)
val PastelRaspberry = Color(0xFF087E78)

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

package com.jestk.browser.ui.theme

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TileMode
import kotlin.math.max

// ==========================================
// Prosto Canvas Light Tokens (DESIGN.md)
// Primary #087E78 on a calm mint canvas #E9F6F2
// ==========================================

val ProstoPrimary = Color(0xFF087E78)
val ProstoBrandPrimary = Color(0xFF087E78)
val ProstoOnPrimary = Color(0xFFFFFFFF)
val ProstoPrimaryContainer = Color(0xFFA5F2E9)
val ProstoOnPrimaryContainer = Color(0xFF00201D)
val ProstoAccent = Color(0xFF14A79D)

val ProstoSurface = Color(0xFFFFFFFF)
val ProstoSurfaceDim = Color(0xFFD2DDDA)
val ProstoSurfaceBright = Color(0xFFFFFFFF)
val ProstoSurfaceContainerLowest = Color(0xFFFFFFFF)
val ProstoSurfaceContainerLow = Color(0xFFF2FAF7)
val ProstoSurfaceContainer = Color(0xFFECF5F2)
val ProstoSurfaceContainerHigh = Color(0xFFE6EFEC)
val ProstoSurfaceContainerHighest = Color(0xFFDEE9E6)

val ProstoOnSurface = Color(0xFF171D1B)
val ProstoOnSurfaceVariant = Color(0xFF3F4947)
val ProstoOutline = Color(0xFF6F7977)
val ProstoOutlineVariant = Color(0xFFBEC9C6)
val ProstoInverseSurface = Color(0xFF2B3230)
val ProstoInverseOnSurface = Color(0xFFEFF1F0)
val ProstoSurfaceTint = Color(0xFF087E78)
val ProstoInversePrimary = Color(0xFF56DBCF)

val ProstoSecondary = Color(0xFF4A6360)
val ProstoOnSecondary = Color(0xFFFFFFFF)
val ProstoSecondaryContainer = Color(0xFFCCE8E3)
val ProstoOnSecondaryContainer = Color(0xFF06201D)

val ProstoTertiary = Color(0xFF416277)
val ProstoOnTertiary = Color(0xFFFFFFFF)
val ProstoTertiaryContainer = Color(0xFFC4E8FF)
val ProstoOnTertiaryContainer = Color(0xFF001E31)

val ProstoError = Color(0xFFBA1A1A)
val ProstoOnError = Color(0xFFFFFFFF)
val ProstoErrorContainer = Color(0xFFFFDAD6)
val ProstoOnErrorContainer = Color(0xFF93000A)

val ProstoPrimaryFixed = Color(0xFFA5F2E9)
val ProstoPrimaryFixedDim = Color(0xFF83D5CB)
val ProstoOnPrimaryFixed = Color(0xFF00201D)
val ProstoOnPrimaryFixedVariant = Color(0xFF003732)

val ProstoSecondaryFixed = Color(0xFFCCE8E3)
val ProstoSecondaryFixedDim = Color(0xFFB1CCC8)
val ProstoOnSecondaryFixed = Color(0xFF06201D)
val ProstoOnSecondaryFixedVariant = Color(0xFF1D3532)

// Architectural Layer Tokens
val ProstoCanvasFloor = Color(0xFFE9F6F2) // Level 0 Base layer
val ProstoCardSurface = Color(0xFFFFFFFF) // Level 1 Card surface
val ProstoDarkText = Color(0xFF16201E)
val ProstoMutedSlate = Color(0xFF5F6E6C)

// Atmospheric Mint Glows
val ProstoSkyGlow = Color(0xFFCFF3EC)
val ProstoPaleBlue = Color(0xFFDFF7F3)

// ==========================================
// Prosto Canvas Dark Tokens (Deep Teal Night & Slate)
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
// Prosto Gradients & Brushes (Calm Mint Theme)
// ==========================================

val ProstoActiveTabBorderBrush =
    Brush.linearGradient(
        colors =
            listOf(
                Color(0xFF56DBCF),
                Color(0xFF087E78),
            ),
    )

val ProstoAddTabGradientBrush =
    Brush.linearGradient(
        colors =
            listOf(
                Color(0xFF087E78),
                Color(0xFF56DBCF),
            ),
    )

val ProstoSkySwitchBrush =
    Brush.horizontalGradient(
        colors =
            listOf(
                Color(0xFF087E78),
                Color(0xFF2BB3A8),
                Color(0xFFA5F2E9),
            ),
    )

val ProstoCardBorderBrush =
    Brush.verticalGradient(
        colors =
            listOf(
                Color.White.copy(alpha = 0.85f),
                Color(0xFFDCE6E3).copy(alpha = 0.80f),
            ),
    )

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
 * Extension modifier that renders the ambient Prosto Canvas mint-glow multi-point gradients.
 * Uses a soft mint base (#E9F6F2) with delicate teal ambient glows (#CFF3EC) at the top.
 */
fun Modifier.prostoSkyBackground(isDark: Boolean = false): Modifier =
    this.drawBehind {
        val canvasWidth = size.width
        val canvasHeight = size.height
        val maxDim = max(canvasWidth, canvasHeight)
        if (maxDim <= 0f) return@drawBehind

        if (!isDark) {
            // Base canvas: calm mint floor
            drawRect(color = ProstoCanvasFloor)

            // Delicate mint ambient glow at top-left (20%, 10%)
            drawRect(
                brush =
                    Brush.radialGradient(
                        colors = listOf(ProstoSkyGlow.copy(alpha = 0.55f), Color.Transparent),
                        center = Offset(canvasWidth * 0.20f, canvasHeight * 0.10f),
                        radius = maxDim * 0.70f,
                        tileMode = TileMode.Clamp,
                    ),
            )

            // Soft glow at top-right (85%, 18%)
            drawRect(
                brush =
                    Brush.radialGradient(
                        colors = listOf(ProstoPaleBlue.copy(alpha = 0.45f), Color.Transparent),
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

// Pastel accents mapped to the calm mint palette
val PastelPeach = Color(0xFFB9EDE6)
val PastelWarmOrange = Color(0xFFB9EDE6)
val PastelRose = Color(0xFFA5F2E9)
val PastelBlue = Color(0xFF6FD9CE)
val PastelBlueContainer = Color(0xFF14A79D)
val PastelPink = Color(0xFFDFF7F3)
val PastelRaspberry = Color(0xFF087E78)

// Old theme tokens backward-compatibility
val JestkPrimary = ProstoPrimary
val JestkOnPrimary = ProstoOnPrimary
val JestkPrimaryContainer = ProstoPrimaryContainer
val JestkOnPrimaryContainer = ProstoOnPrimaryContainer

val LightBackground = ProstoCanvasFloor
val LightSurface = ProstoCardSurface
val LightSurfaceVariant = ProstoSurfaceContainerHigh
val LightOnSurface = ProstoOnSurface
val LightOnSurfaceVariant = ProstoOnSurfaceVariant
val LightDockBackground = ProstoCardSurface
val LightDockButton = ProstoMutedSlate

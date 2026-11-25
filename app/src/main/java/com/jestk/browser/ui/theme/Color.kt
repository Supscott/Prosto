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

// Active tab title and close icon on the tinted tab header (PRO-125). The plain brand teal gives
// 4.39:1 on the light header and 2.86:1 on the dark one; these give 4.89:1 and 7.41:1.
val ProstoActiveTabContentLight = Color(0xFF077670)
val ProstoActiveTabContentDark = Color(0xFF3FD0C6)

val ProstoOnPrimary = Color(0xFFFFFFFF)
val ProstoPrimaryContainer = Color(0xFFA5F2E9)
val ProstoOnPrimaryContainer = Color(0xFF00201D)
val ProstoAccent = Color(0xFF14A79D)

val ProstoSurface = Color(0xFFFFFFFF)
val ProstoSurfaceContainerHigh = Color(0xFFE6EFEC)
val ProstoSurfaceContainerHighest = Color(0xFFDEE9E6)

val ProstoOnSurface = Color(0xFF171D1B)
val ProstoOnSurfaceVariant = Color(0xFF3F4947)
val ProstoOutline = Color(0xFF6F7977)
val ProstoOutlineVariant = Color(0xFFBEC9C6)
val ProstoInverseSurface = Color(0xFF2B3230)
val ProstoInverseOnSurface = Color(0xFFEFF1F0)
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
val DarkTextHighContrast = Color(0xFFF8FAFC)

// Muted icon on the dark Tabs Overview header (~#2B3332): 3.5:1, where ProstoMutedSlate gives 2.4:1 (PRO-143).
val DarkMutedIcon = Color(0xFF7A8886)

// Destructive prompt buttons, delete rows and failure text, same in light and dark themes (PRO-145).
val ProstoDestructive = Color(0xFFEF4444)

// Incognito marks: deep violet dock and accent ring/badge, same in light and dark themes
val IncognitoDockSurface = Color(0xFF1E1631)
val IncognitoActivePill = Color(0xFF2A2040)
val IncognitoAccent = Color(0xFFA78BFA)

// ==========================================
// Prosto Gradients & Brushes (Calm Mint Theme)
// ==========================================

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


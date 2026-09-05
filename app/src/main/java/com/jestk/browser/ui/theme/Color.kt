package com.jestk.browser.ui.theme

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TileMode
import kotlin.math.max

// ==========================================
// Liquid Pastel Sunset Color Tokens (Stitch)
// ==========================================

val LiquidSurface = Color(0xFFFFF8F8)
val LiquidSurfaceDim = Color(0xFFDFD8D9)
val LiquidSurfaceBright = Color(0xFFFFF8F8)
val LiquidSurfaceContainerLowest = Color(0xFFFFFFFF)
val LiquidSurfaceContainerLow = Color(0xFFF9F2F3)
val LiquidSurfaceContainer = Color(0xFFF3ECED)
val LiquidSurfaceContainerHigh = Color(0xFFEEE6E7)
val LiquidSurfaceContainerHighest = Color(0xFFE8E1E2)
val LiquidSurfaceVariant = Color(0xFFE8E1E2)

val LiquidOnSurface = Color(0xFF1E1B1C)
val LiquidOnSurfaceVariant = Color(0xFF42474E)
val LiquidOutline = Color(0xFF72777F)
val LiquidOutlineVariant = Color(0xFFC2C7D0)
val LiquidInverseSurface = Color(0xFF332F30)
val LiquidInverseOnSurface = Color(0xFFF6EFF0)

val LiquidPrimary = Color(0xFF33618D)
val LiquidOnPrimary = Color(0xFFFFFFFF)
val LiquidPrimaryContainer = Color(0xFF7BA7D7)
val LiquidOnPrimaryContainer = Color(0xFF003C65)
val LiquidPrimaryFixed = Color(0xFFD0E4FF)
val LiquidPrimaryFixedDim = Color(0xFF9ECAFC)
val LiquidOnPrimaryFixed = Color(0xFF001D35)
val LiquidOnPrimaryFixedVariant = Color(0xFF154974)

val LiquidSecondary = Color(0xFF82533D)
val LiquidOnSecondary = Color(0xFFFFFFFF)
val LiquidSecondaryContainer = Color(0xFFFFC1A5)
val LiquidOnSecondaryContainer = Color(0xFF7A4D37)
val LiquidSecondaryFixed = Color(0xFFFFDBCC)
val LiquidSecondaryFixedDim = Color(0xFFF6B99D)
val LiquidOnSecondaryFixed = Color(0xFF321203)
val LiquidOnSecondaryFixedVariant = Color(0xFF663C27)

val LiquidTertiary = Color(0xFF854E60)
val LiquidOnTertiary = Color(0xFFFFFFFF)
val LiquidTertiaryContainer = Color(0xFFD391A5)
val LiquidOnTertiaryContainer = Color(0xFF5A2A3B)
val LiquidTertiaryFixed = Color(0xFFFFD9E2)
val LiquidTertiaryFixedDim = Color(0xFFF9B3C8)
val LiquidOnTertiaryFixed = Color(0xFF360C1D)
val LiquidOnTertiaryFixedVariant = Color(0xFF6A3748)

val LiquidError = Color(0xFFBA1A1A)
val LiquidOnError = Color(0xFFFFFFFF)
val LiquidErrorContainer = Color(0xFFFFDAD6)
val LiquidOnErrorContainer = Color(0xFF93000A)

// Pastel Accents
val PastelPeach = Color(0xFFF6B99D)
val PastelWarmOrange = Color(0xFFFED7AA)
val PastelRose = Color(0xFFE8A4B8)
val PastelBlue = Color(0xFF9ECBFC)
val PastelBlueContainer = Color(0xFF7BA7D7)
val PastelPink = Color(0xFFFFD9E2)
val PastelRaspberry = Color(0xFFD391A5)

// ==========================================
// Gradients & Brushes
// ==========================================

val ActiveTabBorderBrush = Brush.linearGradient(
    colors = listOf(
        Color(0xFF7BA7D7),
        Color(0xFFF6B99D),
        Color(0xFFD391A5)
    )
)

val AddTabGradientBrush = Brush.linearGradient(
    colors = listOf(
        Color(0xFF7BA7D7),
        Color(0xFFF6B99D)
    )
)

val SunsetSwitchBrush = Brush.horizontalGradient(
    colors = listOf(
        Color(0xFF7BA7D7),
        Color(0xFF33618D),
        Color(0xFFF6B99D)
    )
)

val DuckDuckGoBadgeBrush = Brush.linearGradient(
    colors = listOf(
        Color(0xFFFFDBCC),
        Color(0xFFFFD9E2)
    )
)

val CardPreviewRoseBrush = Brush.linearGradient(
    colors = listOf(
        Color(0xFFFFD9E2).copy(alpha = 0.70f),
        Color(0xFFF9B3C8).copy(alpha = 0.40f)
    )
)

val CardPreviewPeachBrush = Brush.linearGradient(
    colors = listOf(
        Color(0xFFFFDBCC).copy(alpha = 0.70f),
        Color(0xFFFED7AA).copy(alpha = 0.50f)
    )
)

val CardPreviewBlueBrush = Brush.linearGradient(
    colors = listOf(
        Color(0xFFD0E4FF).copy(alpha = 0.70f),
        Color(0xFFFFFFFF).copy(alpha = 0.90f)
    )
)

/**
 * Extension modifier that renders the ambient Liquid Pastel Sunset multi-point radial gradients.
 */
fun Modifier.liquidSunsetBackground(isDark: Boolean = false): Modifier = this.drawBehind {
    val canvasWidth = size.width
    val canvasHeight = size.height
    val maxDim = max(canvasWidth, canvasHeight)
    if (maxDim <= 0f) return@drawBehind

    if (!isDark) {
        // Base canvas
        drawRect(color = LiquidSurface)

        // Warm Apricot glow at top-left (10%, 15%)
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(PastelWarmOrange.copy(alpha = 0.45f), Color.Transparent),
                center = Offset(canvasWidth * 0.10f, canvasHeight * 0.15f),
                radius = maxDim * 0.70f,
                tileMode = TileMode.Clamp
            )
        )

        // Soft Rose glow at top-right (90%, 25%)
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(PastelRose.copy(alpha = 0.35f), Color.Transparent),
                center = Offset(canvasWidth * 0.90f, canvasHeight * 0.25f),
                radius = maxDim * 0.65f,
                tileMode = TileMode.Clamp
            )
        )

        // Pastel Blue glow at bottom-center (50%, 85%)
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(PastelBlue.copy(alpha = 0.35f), Color.Transparent),
                center = Offset(canvasWidth * 0.50f, canvasHeight * 0.85f),
                radius = maxDim * 0.75f,
                tileMode = TileMode.Clamp
            )
        )
    } else {
        // Dark Base Canvas: clean, deep, elegant
        drawRect(color = Color(0xFF141213))

        // Clean, subtle pastel twilight glows without muddy dark brown/maroon circles
        // Soft peach glow at top-left (10%, 15%) with wide smooth falloff
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(PastelPeach.copy(alpha = 0.09f), Color.Transparent),
                center = Offset(canvasWidth * 0.10f, canvasHeight * 0.15f),
                radius = maxDim * 0.70f,
                tileMode = TileMode.Clamp
            )
        )

        // Delicate rose glow at top-right (90%, 25%)
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(PastelRose.copy(alpha = 0.08f), Color.Transparent),
                center = Offset(canvasWidth * 0.90f, canvasHeight * 0.25f),
                radius = maxDim * 0.65f,
                tileMode = TileMode.Clamp
            )
        )

        // Soft powder blue glow at bottom-center (50%, 85%)
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(PastelBlue.copy(alpha = 0.09f), Color.Transparent),
                center = Offset(canvasWidth * 0.50f, canvasHeight * 0.85f),
                radius = maxDim * 0.75f,
                tileMode = TileMode.Clamp
            )
        )
    }
}

// ==========================================
// Backward-Compatibility Tokens
// ==========================================

val JestkPrimary = LiquidPrimary
val JestkOnPrimary = LiquidOnPrimary
val JestkPrimaryContainer = LiquidPrimaryContainer
val JestkOnPrimaryContainer = LiquidOnPrimaryContainer

val LightBackground = LiquidSurface
val LightSurface = LiquidSurfaceContainerLowest
val LightSurfaceVariant = LiquidSurfaceContainerHigh
val LightOnSurface = LiquidOnSurface
val LightOnSurfaceVariant = LiquidOnSurfaceVariant
val LightDockBackground = LiquidSurfaceContainerHigh
val LightDockButton = LiquidOutline

val DarkBackground = Color(0xFF161314)
val DarkSurface = Color(0xFF1E1B1C)
val DarkSurfaceVariant = Color(0xFF2C2829)
val DarkOnSurface = Color(0xFFF6EFF0)
val DarkOnSurfaceVariant = Color(0xFFD0C5C6)
val DarkDockBackground = Color(0xFF2C2829)
val DarkDockButton = Color(0xFF988E90)

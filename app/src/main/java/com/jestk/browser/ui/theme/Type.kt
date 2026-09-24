package com.jestk.browser.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.jestk.browser.R

/**
 * Plus Jakarta Sans font family with fallbacks to system SansSerif.
 */
val PlusJakartaSans: FontFamily = try {
    FontFamily(
        Font(R.font.plus_jakarta_sans, FontWeight.Normal),
        Font(R.font.plus_jakarta_sans, FontWeight.Medium),
        Font(R.font.plus_jakarta_sans, FontWeight.SemiBold),
        Font(R.font.plus_jakarta_sans, FontWeight.Bold),
    )
} catch (_: Throwable) {
    FontFamily.SansSerif
}

/**
 * Dedicated Serene Canvas Typography Tokens matching DESIGN.md scale.
 */
@Immutable
data class SereneTypographyTokens(
    val headlineXl: TextStyle = TextStyle(
        fontFamily = PlusJakartaSans,
        fontSize = 32.sp,
        fontWeight = FontWeight.Bold,
        lineHeight = 40.sp,
        letterSpacing = (-0.02).em,
    ),
    val headlineLg: TextStyle = TextStyle(
        fontFamily = PlusJakartaSans,
        fontSize = 26.sp,
        fontWeight = FontWeight.Bold,
        lineHeight = 34.sp,
        letterSpacing = (-0.015).em,
    ),
    val headlineMd: TextStyle = TextStyle(
        fontFamily = PlusJakartaSans,
        fontSize = 20.sp,
        fontWeight = FontWeight.SemiBold,
        lineHeight = 28.sp,
        letterSpacing = (-0.01).em,
    ),
    val headlineSm: TextStyle = TextStyle(
        fontFamily = PlusJakartaSans,
        fontSize = 17.sp,
        fontWeight = FontWeight.SemiBold,
        lineHeight = 24.sp,
    ),
    val bodyLg: TextStyle = TextStyle(
        fontFamily = PlusJakartaSans,
        fontSize = 16.sp,
        fontWeight = FontWeight.Normal,
        lineHeight = 24.sp,
    ),
    val bodyMd: TextStyle = TextStyle(
        fontFamily = PlusJakartaSans,
        fontSize = 14.sp,
        fontWeight = FontWeight.Normal,
        lineHeight = 20.sp,
    ),
    val bodySm: TextStyle = TextStyle(
        fontFamily = PlusJakartaSans,
        fontSize = 13.sp,
        fontWeight = FontWeight.Normal,
        lineHeight = 18.sp,
    ),
    val labelMd: TextStyle = TextStyle(
        fontFamily = PlusJakartaSans,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        lineHeight = 16.sp,
        letterSpacing = 0.04.em,
    ),
    val labelSm: TextStyle = TextStyle(
        fontFamily = PlusJakartaSans,
        fontSize = 11.sp,
        fontWeight = FontWeight.Medium,
        lineHeight = 14.sp,
    ),
)

val SereneTextStyles = SereneTypographyTokens()
val LocalSereneTypography = staticCompositionLocalOf { SereneTextStyles }

/**
 * Material 3 Typography configured with Plus Jakarta Sans and Serene Canvas scales.
 */
val SereneTypography = Typography(
    displayLarge = SereneTextStyles.headlineXl,
    displayMedium = SereneTextStyles.headlineLg,
    displaySmall = SereneTextStyles.headlineMd,
    headlineLarge = SereneTextStyles.headlineXl,
    headlineMedium = SereneTextStyles.headlineLg,
    headlineSmall = SereneTextStyles.headlineMd,
    titleLarge = SereneTextStyles.headlineSm,
    titleMedium = SereneTextStyles.headlineSm,
    titleSmall = SereneTextStyles.bodyLg,
    bodyLarge = SereneTextStyles.bodyLg,
    bodyMedium = SereneTextStyles.bodyMd,
    bodySmall = SereneTextStyles.bodySm,
    labelLarge = SereneTextStyles.labelMd,
    labelMedium = SereneTextStyles.labelMd,
    labelSmall = SereneTextStyles.labelSm,
)

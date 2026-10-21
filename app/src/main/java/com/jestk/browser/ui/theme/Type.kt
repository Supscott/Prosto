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
 * Manrope - display and title font (new-design.md: title-text).
 */
val Manrope: FontFamily =
    try {
        FontFamily(
            Font(R.font.manrope_regular, FontWeight.Normal),
            Font(R.font.manrope_medium, FontWeight.Medium),
            Font(R.font.manrope_semibold, FontWeight.SemiBold),
            Font(R.font.manrope_bold, FontWeight.Bold),
        )
    } catch (_: Throwable) {
        FontFamily.SansSerif
    }

/**
 * Inter - body and label font (new-design.md: body-text).
 */
val Inter: FontFamily =
    try {
        FontFamily(
            Font(R.font.inter_regular, FontWeight.Normal),
            Font(R.font.inter_medium, FontWeight.Medium),
            Font(R.font.inter_semibold, FontWeight.SemiBold),
            Font(R.font.inter_bold, FontWeight.Bold),
        )
    } catch (_: Throwable) {
        FontFamily.SansSerif
    }

/**
 * Dedicated typography tokens matching the new-design.md type scale.
 */
@Immutable
data class SereneTypographyTokens(
    val headlineXl: TextStyle =
        TextStyle(
            fontFamily = Manrope,
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold,
            lineHeight = 40.sp,
            letterSpacing = (-0.02).em,
        ),
    val headlineLg: TextStyle =
        TextStyle(
            fontFamily = Manrope,
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
            lineHeight = 34.sp,
            letterSpacing = (-0.015).em,
        ),
    val headlineMd: TextStyle =
        TextStyle(
            fontFamily = Manrope,
            fontSize = 20.sp,
            fontWeight = FontWeight.SemiBold,
            lineHeight = 28.sp,
            letterSpacing = (-0.01).em,
        ),
    val headlineSm: TextStyle =
        TextStyle(
            fontFamily = Manrope,
            fontSize = 17.sp,
            fontWeight = FontWeight.SemiBold,
            lineHeight = 24.sp,
        ),
    val bodyLg: TextStyle =
        TextStyle(
            fontFamily = Inter,
            fontSize = 16.sp,
            fontWeight = FontWeight.Normal,
            lineHeight = 24.sp,
        ),
    val bodyMd: TextStyle =
        TextStyle(
            fontFamily = Inter,
            fontSize = 14.sp,
            fontWeight = FontWeight.Normal,
            lineHeight = 20.sp,
        ),
    val bodySm: TextStyle =
        TextStyle(
            fontFamily = Inter,
            fontSize = 13.sp,
            fontWeight = FontWeight.Normal,
            lineHeight = 18.sp,
        ),
    val labelMd: TextStyle =
        TextStyle(
            fontFamily = Inter,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            lineHeight = 16.sp,
            letterSpacing = 0.04.em,
        ),
    val labelSm: TextStyle =
        TextStyle(
            fontFamily = Inter,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            lineHeight = 14.sp,
        ),
)

val SereneTextStyles = SereneTypographyTokens()
val LocalSereneTypography = staticCompositionLocalOf { SereneTextStyles }

/**
 * Material 3 Typography configured with Manrope titles and Inter body text.
 */
val SereneTypography =
    Typography(
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

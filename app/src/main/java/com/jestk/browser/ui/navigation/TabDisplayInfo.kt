package com.jestk.browser.ui.navigation

import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jestk.browser.domain.model.Tab
import com.jestk.browser.ui.theme.LocalProstoTypography
import com.jestk.browser.ui.viewmodel.BrowserUiState
import java.net.URI
import kotlinx.coroutines.isActive

internal data class TabDisplayInfo(
    val title: String,
    val url: String,
    val domain: String,
)

internal fun resolveTabDisplayInfo(
    tab: Tab,
    isActive: Boolean,
    isHome: Boolean,
    uiState: BrowserUiState,
    homeTitle: String,
    fallbackTitle: String,
): TabDisplayInfo {
    val currentTitle =
        when {
            isHome -> homeTitle
            isActive && uiState.pageTitle.isNotEmpty() -> uiState.pageTitle
            tab.title.isNotEmpty() -> tab.title
            else -> fallbackTitle
        }
    val currentUrl = if (isActive && uiState.currentUrl.isNotEmpty()) uiState.currentUrl else tab.url
    val currentDomain = if (isHome) "" else extractDomain(currentUrl)
    return TabDisplayInfo(title = currentTitle, url = currentUrl, domain = currentDomain)
}

private fun extractDomain(url: String): String {
    if (url.isEmpty() || url == "about:blank") return ""
    return try {
        URI(url).host?.removePrefix("www.") ?: url.removePrefix("https://").removePrefix("http://")
    } catch (_: Exception) {
        url
    }
}

/**
 * Measures the active tab's title and domain and returns the wider of the two as a width in dp.
 */
@Composable
internal fun rememberActiveTabTextWidth(
    activeDisplayInfo: TabDisplayInfo,
    density: Density,
): Dp {
    val textMeasurer = rememberTextMeasurer()
    val typography = LocalProstoTypography.current

    val titleStyle = remember(typography, activeDisplayInfo.domain.isEmpty()) {
        typography.labelMd.copy(
            fontSize = if (activeDisplayInfo.domain.isEmpty()) ActiveTabPillDefaults.TitleFontSizeStandard else ActiveTabPillDefaults.TitleFontSizeWithDomain,
            fontWeight = FontWeight.Bold,
        )
    }
    val domainStyle = remember(typography) {
        typography.bodySm.copy(
            fontSize = ActiveTabPillDefaults.DomainFontSize,
            fontWeight = FontWeight.Normal,
        )
    }

    val measuredTextWidthDp = remember(
        textMeasurer,
        activeDisplayInfo.title,
        activeDisplayInfo.domain,
        titleStyle,
        domainStyle,
        density,
    ) {
        val measuredTitlePx = textMeasurer.measure(
            text = activeDisplayInfo.title,
            style = titleStyle,
            maxLines = 1,
        ).size.width.toFloat()
        val minRealisticTitlePx = with(density) { (activeDisplayInfo.title.length * 3.5.sp.value).sp.toPx() }
        val titleWidthPx = if (measuredTitlePx >= minRealisticTitlePx) {
            measuredTitlePx
        } else {
            with(density) { (activeDisplayInfo.title.length * ActiveTabPillDefaults.FallbackTitleCharWidthSp.value).sp.toPx() }
        }

        val domainWidthPx = if (activeDisplayInfo.domain.isNotEmpty()) {
            val measuredDomainPx = textMeasurer.measure(
                text = activeDisplayInfo.domain,
                style = domainStyle,
                maxLines = 1,
            ).size.width.toFloat()
            val minRealisticDomainPx = with(density) { (activeDisplayInfo.domain.length * 3.0.sp.value).sp.toPx() }
            if (measuredDomainPx >= minRealisticDomainPx) {
                measuredDomainPx
            } else {
                with(density) { (activeDisplayInfo.domain.length * ActiveTabPillDefaults.FallbackDomainCharWidthSp.value).sp.toPx() }
            }
        } else {
            0f
        }

        val maxTextWidthPx = maxOf(titleWidthPx, domainWidthPx)
        with(density) { maxTextWidthPx.toDp() }
    }
    return measuredTextWidthDp
}

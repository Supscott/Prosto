package com.jestk.browser.ui.overlays

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FilterNone
import androidx.compose.material.icons.filled.Language
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jestk.browser.R
import com.jestk.browser.domain.model.Tab
import com.jestk.browser.engine.FaviconManager
import com.jestk.browser.engine.SearchInputAutocompleteMatcher
import com.jestk.browser.ui.components.SiteFavicon
import com.jestk.browser.ui.theme.DarkOnSurface
import com.jestk.browser.ui.theme.DarkSurface
import com.jestk.browser.ui.theme.LocalProstoTypography
import com.jestk.browser.ui.theme.ProstoBorders
import com.jestk.browser.ui.theme.ProstoBrandPrimary
import com.jestk.browser.ui.theme.ProstoDarkText
import com.jestk.browser.ui.theme.ProstoMutedSlate

/**
 * Horizontal carousel displayed directly above the search input field when the typed query
 * matches one or more currently open tabs.
 * Allows 1-tap switching to existing tabs, following the Prosto Canvas NO BLUR specification.
 */
@Composable
fun OpenTabSwitchCarousel(
    tabs: List<Tab>,
    isDark: Boolean,
    onTabClick: (Tab) -> Unit,
    modifier: Modifier = Modifier,
) {
    val matcher = remember { SearchInputAutocompleteMatcher() }

    LazyRow(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items(tabs, key = { it.id }) { tab ->
            OpenTabSwitchCapsule(
                tab = tab,
                cleanDomain = matcher.extractDomain(tab.url),
                isDark = isDark,
                onClick = { onTabClick(tab) },
            )
        }
    }
}

@Composable
private fun OpenTabSwitchCapsule(
    tab: Tab,
    cleanDomain: String,
    isDark: Boolean,
    onClick: () -> Unit,
) {
    val bg = if (isDark) DarkSurface else Color.White
    val border = ProstoBorders.cardBorder(isDark)
    val textColor = if (isDark) DarkOnSurface else ProstoDarkText
    val switchLabel = stringResource(R.string.switch_to_tab)
    val actionDesc = stringResource(R.string.switch_to_open_tab)

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = bg,
        border = border,
        shadowElevation = 3.dp,
        modifier = Modifier
            .height(44.dp)
            .widthIn(min = 160.dp, max = 260.dp)
            .clickable(onClick = onClick)
            .semantics { contentDescription = "$actionDesc: ${tab.title.ifEmpty { cleanDomain }}" },
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Favicon
            val faviconUrl = tab.faviconUrl ?: FaviconManager.extractFallbackFaviconUrl(tab.url)
            SiteFavicon(
                faviconUrl = faviconUrl,
                imageSize = 20.dp,
                shape = CircleShape,
                placeholder = {
                    Icon(
                        imageVector = Icons.Default.Language,
                        contentDescription = null,
                        tint = ProstoMutedSlate,
                        modifier = Modifier.size(16.dp),
                    )
                },
            )

            // Title & Domain
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = tab.title.ifBlank { cleanDomain },
                    style = LocalProstoTypography.current.labelMd,
                    color = textColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (cleanDomain.isNotBlank()) {
                    Text(
                        text = cleanDomain,
                        style = LocalProstoTypography.current.labelSm,
                        color = ProstoMutedSlate,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            // Accent "Switch to tab" Badge
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = ProstoBrandPrimary.copy(alpha = if (isDark) 0.25f else 0.12f),
                border = BorderStroke(1.dp, ProstoBrandPrimary.copy(alpha = 0.35f)),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.FilterNone,
                        contentDescription = null,
                        tint = ProstoBrandPrimary,
                        modifier = Modifier.size(11.dp),
                    )
                    Text(
                        text = switchLabel,
                        style = LocalProstoTypography.current.labelSm,
                        color = ProstoBrandPrimary,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}

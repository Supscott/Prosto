package com.jestk.browser.ui.overlays

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jestk.browser.ui.navigation.ActiveTabPillDefaults
import com.jestk.browser.ui.navigation.getContrastingFolderContentColor
import com.jestk.browser.ui.theme.DarkOnSurfaceVariant
import com.jestk.browser.ui.theme.DarkSurface
import com.jestk.browser.ui.theme.DarkTextHighContrast
import com.jestk.browser.ui.theme.LocalProstoTypography
import com.jestk.browser.ui.theme.ProstoAccent
import com.jestk.browser.ui.theme.ProstoBorders
import com.jestk.browser.ui.theme.ProstoBrandPrimary
import com.jestk.browser.ui.theme.ProstoDarkText
import com.jestk.browser.ui.theme.ProstoMutedSlate
import com.jestk.browser.ui.overlays.MockDockMetrics as M

private val PillShape = RoundedCornerShape(21.dp)

@Composable
private fun pillTitleStyle(hasDomain: Boolean): TextStyle =
    LocalProstoTypography.current.labelMd.copy(
        fontWeight = FontWeight.Bold,
        fontSize = if (hasDomain) ActiveTabPillDefaults.TitleFontSizeWithDomain else ActiveTabPillDefaults.TitleFontSizeStandard,
    )

@Composable
private fun pillDomainStyle(): TextStyle =
    LocalProstoTypography.current.bodySm.copy(fontSize = ActiveTabPillDefaults.DomainFontSize, fontWeight = FontWeight.Normal)

@Composable
private fun folderTitleStyle(): TextStyle =
    LocalProstoTypography.current.labelMd.copy(fontWeight = FontWeight.Bold, fontSize = 13.sp)

/** Active pill width for each site, measured like TabStripRow does for the real active tab. */
@Composable
internal fun rememberPillWidths(sites: List<MockSite>, capsuleWidth: Float): List<Float> {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val domainStyle = pillDomainStyle()
    val styles = sites.map { pillTitleStyle(hasDomain = it.domain.isNotEmpty()) }
    return remember(sites, capsuleWidth, density) {
        sites.mapIndexed { i, site ->
            val title = measurer.measure(site.title, styles[i]).size.width
            val domain = if (site.domain.isEmpty()) 0 else measurer.measure(site.domain, domainStyle).size.width
            M.pillWidth(with(density) { maxOf(title, domain).toDp().value }, capsuleWidth)
        }
    }
}

/** Folder chip width: padding 12 + title + 4 + arrow 16 + padding 8, or the 38dp circle without a title. */
@Composable
internal fun rememberFolderChipWidth(title: String): Float {
    if (title.isBlank()) return M.TAB
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val style = folderTitleStyle()
    return remember(title, density) {
        val text = with(density) { measurer.measure(title, style).size.width.toDp().value }
        12f + text + 4f + 16f + 8f
    }
}

/**
 * A tab morphing between the inactive circle and the active pill. The favicon sits 10dp from the
 * start in both shapes, so only the pill surface, title and close button grow in.
 */
@Composable
internal fun MockTab(entry: StripEntry.TabEntry, isDark: Boolean, pillBg: Color, pillBorderColors: List<Color>) {
    val e = entry.expansion
    val site = entry.site
    Box(
        modifier =
            Modifier
                .requiredSize(width = entry.width.dp, height = lerp(M.TAB, M.PILL_HEIGHT, e).dp)
                .shadow(elevation = (2f * e).dp, shape = PillShape, clip = false)
                .background(pillBg.copy(alpha = pillBg.alpha * e), PillShape)
                .border(1.dp, Brush.verticalGradient(pillBorderColors.map { it.copy(alpha = it.alpha * e) }), PillShape)
                .clip(PillShape),
        contentAlignment = Alignment.CenterStart,
    ) {
        Row(
            modifier =
                Modifier
                    .wrapContentWidth(Alignment.Start, unbounded = true)
                    .width(maxOf(entry.pillWidth, M.TAB).dp)
                    .fillMaxHeight()
                    .padding(start = 10.dp, end = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MockFavicon(site, isDark)
            if (e > 0f) {
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f).graphicsLayer { alpha = e }) {
                    Text(
                        text = site.title,
                        style = pillTitleStyle(hasDomain = site.domain.isNotEmpty()),
                        color = if (isDark) DarkTextHighContrast else ProstoDarkText,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Clip,
                    )
                    if (site.domain.isNotEmpty()) {
                        Text(
                            text = site.domain,
                            style = pillDomainStyle(),
                            color = if (isDark) DarkOnSurfaceVariant else ProstoMutedSlate,
                            maxLines = 1,
                            softWrap = false,
                        )
                    }
                }
                Spacer(modifier = Modifier.width(4.dp))
                Box(modifier = Modifier.size(22.dp).graphicsLayer { alpha = e }, contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = null,
                        tint = if (isDark) DarkOnSurfaceVariant else ProstoMutedSlate,
                        modifier = Modifier.size(14.dp),
                    )
                }
            }
        }
    }
}

/**
 * The tab lifted off the strip: a 38dp floating circle that shrinks and turns into a cross as it is
 * pulled up past the remove threshold, matching InactiveTabButton inside TabStripDragOverlays.
 */
@Composable
internal fun MockDraggedTab(dragged: DraggedTab, isDark: Boolean) {
    val cross = ((dragged.remove - 0.15f) / 0.70f).coerceIn(0f, 1f)
    Box(
        modifier =
            Modifier
                .offset((dragged.x - M.TAB / 2).dp, (dragged.y - M.TAB / 2).dp)
                .graphicsLayer {
                    scaleX = dragged.scale
                    scaleY = dragged.scale
                    alpha = dragged.alpha
                }
                .size(M.TAB.dp)
                .shadow(elevation = 6.dp, shape = CircleShape, clip = false)
                .background(if (isDark) DarkSurface else Color.White, CircleShape)
                .border(ProstoBorders.floatingBorder(isDark), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Box(modifier = Modifier.graphicsLayer { alpha = 1f - cross }) { MockFavicon(dragged.site, isDark) }
        Icon(
            imageVector = Icons.Default.Close,
            contentDescription = null,
            tint = if (isDark) DarkOnSurfaceVariant else ProstoMutedSlate,
            modifier = Modifier.size(16.dp).graphicsLayer { alpha = cross },
        )
    }
}

/** Stand-in favicon: the site's initial on its brand colour, or the home glyph for a new tab. */
@Composable
internal fun MockFavicon(site: MockSite, isDark: Boolean) {
    if (site.isHome) {
        Box(modifier = Modifier.size(18.dp), contentAlignment = Alignment.Center) {
            Icon(
                imageVector = Icons.Default.Home,
                contentDescription = null,
                tint = if (isDark) ProstoAccent else ProstoBrandPrimary,
                modifier = Modifier.size(17.dp),
            )
        }
        return
    }
    Box(
        modifier = Modifier.size(18.dp).clip(RoundedCornerShape(4.dp)).background(site.color),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = site.letter,
            style = LocalProstoTypography.current.labelMd.copy(fontWeight = FontWeight.Bold, fontSize = 11.sp, lineHeight = 11.sp),
            color = Color.White,
        )
    }
}

/** Chrome-style folder chip: title and an arrow that turns from right (collapsed) to left (expanded). */
@Composable
internal fun MockFolderChip(entry: StripEntry.FolderEntry) {
    val content = getContrastingFolderContentColor(entry.color)
    val arrowRotation = 180f * entry.expanded
    Box(
        modifier = Modifier.requiredSize(width = entry.chipWidth.dp, height = M.TAB.dp).clip(CircleShape).background(entry.color),
        contentAlignment = Alignment.Center,
    ) {
        if (entry.title.isBlank()) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                tint = content,
                modifier = Modifier.size(18.dp).graphicsLayer { rotationZ = arrowRotation },
            )
        } else {
            Row(
                modifier = Modifier.padding(start = 12.dp, end = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(text = entry.title, style = folderTitleStyle(), color = content, maxLines = 1, softWrap = false)
                Spacer(modifier = Modifier.width(4.dp))
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    tint = content,
                    modifier = Modifier.size(16.dp).graphicsLayer { rotationZ = arrowRotation },
                )
            }
        }
    }
}

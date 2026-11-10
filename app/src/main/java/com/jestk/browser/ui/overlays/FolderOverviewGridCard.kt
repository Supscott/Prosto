package com.jestk.browser.ui.overlays

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Public
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.jestk.browser.R
import com.jestk.browser.domain.model.Tab
import com.jestk.browser.domain.model.TabFolder
import com.jestk.browser.domain.model.ThemeMode
import com.jestk.browser.ui.components.SiteFavicon
import com.jestk.browser.ui.navigation.getContrastingFolderContentColor
import com.jestk.browser.ui.theme.DarkBackground
import com.jestk.browser.ui.theme.DarkSurface
import com.jestk.browser.ui.theme.DarkSurfaceVariant
import com.jestk.browser.ui.theme.JestkBrowserTheme
import com.jestk.browser.ui.theme.LocalProstoTypography
import com.jestk.browser.ui.theme.ProstoBorders
import com.jestk.browser.ui.theme.ProstoMutedSlate
import com.jestk.browser.ui.theme.ProstoShapes
import java.io.File

/**
 * A collapsed folder preview card in the tab overview grid.
 *
 * Conforms to Prosto Canvas design system (matching open folder style):
 * - Solid folder color header strip with contrasting text color
 * - Translucent folder color body (alpha = 0.15f / 0.22f) under 2x2 tab previews
 * - 9:16 aspect ratio
 * - When tabs > 4: 4th slot displays "+N" overflow badge
 * - When tabs < 4: remaining slots display subtly muted empty placeholders
 */
@Composable
internal fun FolderOverviewGridCard(
    folder: TabFolder,
    tabs: List<Tab>,
    isDark: Boolean,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    isHovered: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val folderColor = Color(folder.color)
    val contentColor = getContrastingFolderContentColor(folderColor)
    val borderStroke = if (isHovered) {
        BorderStroke(2.5.dp, contentColor)
    } else {
        BorderStroke(1.dp, contentColor.copy(alpha = 0.25f))
    }
    val elevation = if (isHovered) 8.dp else 2.dp

    val displayTitle = folder.title.ifBlank {
        pluralStringResource(R.plurals.folder_tabs_count, tabs.size, tabs.size)
    }

    Surface(
        shape = ProstoShapes.Card,
        color = folderColor,
        border = borderStroke,
        shadowElevation = elevation,
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(9f / 16f)
            .clip(ProstoShapes.Card)
            .clickable(onClick = onClick)
            .testTag("folder_card_${folder.id}"),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header Strip (Folder Color with subtle shade + Contrasting Title)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(38.dp)
                    .background(Color.Black.copy(alpha = 0.08f))
                    .padding(horizontal = 10.dp)
                    .testTag("folder_header_${folder.id}"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = displayTitle,
                    style = LocalProstoTypography.current.labelMd.copy(
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                    ),
                    color = contentColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("folder_title_${folder.id}"),
                )
            }

            // 2x2 Grid of Previews on top of the solid folder color surface
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(6.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    FolderSlotPreview(
                        slotIndex = 0,
                        folder = folder,
                        tabs = tabs,
                        isDark = isDark,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxSize(),
                    )
                    FolderSlotPreview(
                        slotIndex = 1,
                        folder = folder,
                        tabs = tabs,
                        isDark = isDark,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxSize(),
                    )
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    FolderSlotPreview(
                        slotIndex = 2,
                        folder = folder,
                        tabs = tabs,
                        isDark = isDark,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxSize(),
                    )
                    FolderSlotPreview(
                        slotIndex = 3,
                        folder = folder,
                        tabs = tabs,
                        isDark = isDark,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxSize(),
                    )
                }
            }
        }
    }
}

@Composable
private fun FolderSlotPreview(
    slotIndex: Int,
    folder: TabFolder,
    tabs: List<Tab>,
    isDark: Boolean,
    modifier: Modifier = Modifier,
) {
    val slotShape = RoundedCornerShape(6.dp)
    val folderColor = Color(folder.color)
    val contentColor = getContrastingFolderContentColor(folderColor)
    val slotBorder = BorderStroke(1.dp, contentColor.copy(alpha = 0.20f))

    if (slotIndex < tabs.size) {
        val tab = tabs[slotIndex]
        val isOverflowSlot = slotIndex == 3 && tabs.size > 4

        Box(
            modifier = modifier
                .clip(slotShape)
                .background(if (isDark) DarkSurface else Color.White)
                .border(slotBorder, slotShape)
                .testTag("folder_slot_${folder.id}_$slotIndex"),
            contentAlignment = Alignment.Center,
        ) {
            val file = tab.snapshotPath?.let { File(it) }
            if (tab.isHomeTab) {
                // Home tabs render the live Home page elsewhere (workspace cards & tab grid);
                // in the compact folder slot we show a quiet Home icon instead of a stale screenshot.
                Icon(
                    imageVector = Icons.Default.Home,
                    contentDescription = null,
                    tint = ProstoMutedSlate.copy(alpha = 0.45f),
                    modifier = Modifier.size(18.dp),
                )
            } else if (file != null && file.exists()) {
                AsyncImage(
                    model = file,
                    contentDescription = tab.title,
                    contentScale = ContentScale.Crop,
                    alignment = Alignment.TopCenter,
                    modifier = Modifier.fillMaxSize(),
                )
            } else if (tab.faviconUrl != null) {
                SiteFavicon(
                    faviconUrl = tab.faviconUrl,
                    imageSize = 14.dp,
                    containerSize = 18.dp,
                    shape = CircleShape,
                    placeholder = {
                        Icon(
                            imageVector = Icons.Default.Public,
                            contentDescription = null,
                            tint = ProstoMutedSlate.copy(alpha = 0.40f),
                            modifier = Modifier.size(12.dp),
                        )
                    },
                )
            } else {
                Icon(
                    imageVector = Icons.Default.Public,
                    contentDescription = null,
                    tint = ProstoMutedSlate.copy(alpha = 0.40f),
                    modifier = Modifier.size(14.dp),
                )
            }

            if (isOverflowSlot) {
                val overflowCount = tabs.size - 3
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.50f))
                        .testTag("folder_overflow_${folder.id}"),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(R.string.folder_overflow_count, overflowCount),
                        style = LocalProstoTypography.current.labelMd.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                        ),
                        color = Color.White,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    } else {
        // Empty slot placeholder - subtle translucent plaque on folder color
        val emptySlotBg = if (contentColor == Color.White) {
            Color.White.copy(alpha = 0.24f)
        } else {
            Color.Black.copy(alpha = 0.12f)
        }
        Box(
            modifier = modifier
                .clip(slotShape)
                .background(emptySlotBg)
                .border(slotBorder, slotShape)
                .testTag("folder_empty_slot_${folder.id}_$slotIndex"),
        )
    }
}

@Preview(name = "FolderOverviewGridCard Light", showBackground = true, widthDp = 120, heightDp = 213)
@Composable
fun FolderOverviewGridCardLightPreview() {
    JestkBrowserTheme(themeMode = ThemeMode.LIGHT) {
        Box(modifier = Modifier.padding(8.dp)) {
            FolderOverviewGridCard(
                folder = TabFolder(
                    id = "f1",
                    title = "Work",
                    color = 0xFF2196F3.toInt(),
                ),
                tabs = listOf(
                    Tab(id = "1", url = "https://kotlinlang.org", title = "Kotlin"),
                    Tab(id = "2", url = "https://github.com", title = "GitHub"),
                    Tab(id = "3", url = "https://android.com", title = "Android"),
                    Tab(id = "4", url = "https://google.com", title = "Google"),
                    Tab(id = "5", url = "https://news.ycombinator.com", title = "HN"),
                ),
                isDark = false,
                onClick = {},
            )
        }
    }
}

@Preview(name = "FolderOverviewGridCard Dark", showBackground = true, widthDp = 120, heightDp = 213)
@Composable
fun FolderOverviewGridCardDarkPreview() {
    JestkBrowserTheme(themeMode = ThemeMode.DARK) {
        Box(modifier = Modifier.padding(8.dp)) {
            FolderOverviewGridCard(
                folder = TabFolder(
                    id = "f1",
                    title = "Work",
                    color = 0xFF2196F3.toInt(),
                ),
                tabs = listOf(
                    Tab(id = "1", url = "https://kotlinlang.org", title = "Kotlin"),
                    Tab(id = "2", url = "https://github.com", title = "GitHub"),
                    Tab(id = "3", url = "https://android.com", title = "Android"),
                    Tab(id = "4", url = "https://google.com", title = "Google"),
                    Tab(id = "5", url = "https://news.ycombinator.com", title = "HN"),
                ),
                isDark = true,
                onClick = {},
            )
        }
    }
}

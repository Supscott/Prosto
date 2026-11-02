package com.jestk.browser.ui.overlays

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
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
import com.jestk.browser.ui.theme.DarkBackground
import com.jestk.browser.ui.theme.DarkOnSurface
import com.jestk.browser.ui.theme.DarkSurface
import com.jestk.browser.ui.theme.DarkSurfaceVariant
import com.jestk.browser.ui.theme.JestkBrowserTheme
import com.jestk.browser.ui.theme.LocalProstoTypography
import com.jestk.browser.ui.theme.ProstoBorders
import com.jestk.browser.ui.theme.ProstoCanvasFloor
import com.jestk.browser.ui.theme.ProstoDarkText
import com.jestk.browser.ui.theme.ProstoMutedSlate
import com.jestk.browser.ui.theme.ProstoShapes
import java.io.File

/**
 * A collapsed folder preview card in the tab overview grid.
 *
 * Conforms to Prosto Canvas design system (solid surface, elevation shadow, rim borders, NO blur).
 * Features:
 * - 9:16 aspect ratio
 * - Header with folder color badge and title / tab count
 * - Internal 2x2 grid with mini-previews of up to 4 member tabs
 * - When tabs > 4: 4th slot displays "+N" overflow badge (where N = totalTabs - 3)
 * - When tabs < 4: remaining slots display subtly muted empty placeholders
 */
@OptIn(ExperimentalFoundationApi::class)
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
    val baseCardBg = if (isDark) DarkSurface else Color.White
    val folderColor = Color(folder.color)
    val borderStroke = if (isHovered) {
        BorderStroke(2.5.dp, folderColor)
    } else {
        ProstoBorders.cardBorder(isDark)
    }
    val elevation = if (isHovered) 8.dp else 2.dp

    val displayTitle = folder.title.ifBlank {
        stringResource(R.string.folder_tabs_count, tabs.size)
    }

    Surface(
        shape = ProstoShapes.Card,
        color = baseCardBg,
        border = borderStroke,
        shadowElevation = elevation,
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(9f / 16f)
            .clip(ProstoShapes.Card)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
            )
            .testTag("folder_card_${folder.id}"),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header Strip (Folder Color Badge + Title)
            val headerBg = if (isDark) {
                DarkSurfaceVariant.copy(alpha = 0.50f)
            } else {
                ProstoCanvasFloor
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(38.dp)
                    .background(headerBg)
                    .padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // Folder Color Badge
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(folderColor)
                        .testTag("folder_badge_${folder.id}"),
                )

                // Title
                Text(
                    text = displayTitle,
                    style = LocalProstoTypography.current.labelMd.copy(
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                    ),
                    color = if (isDark) DarkOnSurface else ProstoDarkText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("folder_title_${folder.id}"),
                )
            }

            // 2x2 Grid of Previews
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
                        folderId = folder.id,
                        tabs = tabs,
                        isDark = isDark,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxSize(),
                    )
                    FolderSlotPreview(
                        slotIndex = 1,
                        folderId = folder.id,
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
                        folderId = folder.id,
                        tabs = tabs,
                        isDark = isDark,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxSize(),
                    )
                    FolderSlotPreview(
                        slotIndex = 3,
                        folderId = folder.id,
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
    folderId: String,
    tabs: List<Tab>,
    isDark: Boolean,
    modifier: Modifier = Modifier,
) {
    val slotShape = RoundedCornerShape(6.dp)
    val slotBorder = ProstoBorders.cardBorder(isDark)

    if (slotIndex < tabs.size) {
        val tab = tabs[slotIndex]
        val isOverflowSlot = slotIndex == 3 && tabs.size > 4

        Box(
            modifier = modifier
                .clip(slotShape)
                .background(if (isDark) DarkBackground else ProstoCanvasFloor)
                .border(slotBorder, slotShape)
                .testTag("folder_slot_${folderId}_$slotIndex"),
            contentAlignment = Alignment.Center,
        ) {
            val file = tab.snapshotPath?.let { File(it) }
            if (file != null && file.exists()) {
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
                            tint = ProstoMutedSlate.copy(alpha = 0.5f),
                            modifier = Modifier.size(14.dp),
                        )
                    },
                )
            } else {
                Icon(
                    imageVector = Icons.Default.Public,
                    contentDescription = null,
                    tint = ProstoMutedSlate.copy(alpha = 0.40f),
                    modifier = Modifier.size(16.dp),
                )
            }

            if (isOverflowSlot) {
                val overflowCount = tabs.size - 3
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.55f))
                        .testTag("folder_overflow_${folderId}"),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "+$overflowCount",
                        color = Color.White,
                        style = LocalProstoTypography.current.labelMd.copy(
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                        ),
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    } else {
        Box(
            modifier = modifier
                .clip(slotShape)
                .background(if (isDark) DarkSurfaceVariant.copy(alpha = 0.20f) else ProstoCanvasFloor.copy(alpha = 0.50f))
                .border(slotBorder, slotShape)
                .testTag("folder_empty_slot_${folderId}_$slotIndex"),
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
                onLongClick = {},
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
                    id = "f2",
                    title = "Design",
                    color = 0xFF10B981.toInt(),
                ),
                tabs = listOf(
                    Tab(id = "1", url = "https://figma.com", title = "Figma"),
                    Tab(id = "2", url = "https://dribbble.com", title = "Dribbble"),
                ),
                isDark = true,
                onClick = {},
                onLongClick = {},
            )
        }
    }
}

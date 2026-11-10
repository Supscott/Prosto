package com.jestk.browser.ui.overlays

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Public
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.jestk.browser.R
import com.jestk.browser.domain.model.Tab
import com.jestk.browser.domain.model.Workspace
import com.jestk.browser.ui.components.SiteFavicon
import com.jestk.browser.ui.theme.DarkBackground
import com.jestk.browser.ui.theme.DarkOnSurface
import com.jestk.browser.ui.theme.DarkSurface
import com.jestk.browser.ui.theme.DarkSurfaceVariant
import com.jestk.browser.ui.theme.LocalProstoTypography
import com.jestk.browser.ui.theme.ProstoBorders
import com.jestk.browser.ui.theme.ProstoBrandPrimary
import com.jestk.browser.ui.theme.ProstoCanvasFloor
import com.jestk.browser.ui.theme.ProstoCardSurface
import com.jestk.browser.ui.theme.ProstoDarkText
import com.jestk.browser.ui.theme.ProstoMutedSlate
import com.jestk.browser.ui.theme.ProstoPaleBlue
import com.jestk.browser.ui.theme.ProstoShapes
import java.io.File

/**
 * Android Recents-style workspace overview card displaying workspace header, active tab
 * snapshot preview, tab title/favicon bar, and open tab count.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun WorkspaceOverviewCard(
    workspace: Workspace,
    isActive: Boolean,
    isDark: Boolean,
    tabCount: Int,
    activeTab: Tab?,
    onSelect: () -> Unit,
    onEdit: () -> Unit,
    modifier: Modifier = Modifier,
    cornerRadiusDp: androidx.compose.ui.unit.Dp = 24.dp,
) {
    val typography = LocalProstoTypography.current
    val cardBg = if (isDark) DarkSurface else Color.White
    val workspaceColor = Color(workspace.color)

    val border = if (isActive) {
        androidx.compose.foundation.BorderStroke(2.dp, workspaceColor)
    } else {
        ProstoBorders.cardBorder(isDark)
    }

    val cardShape = androidx.compose.foundation.shape.RoundedCornerShape(cornerRadiusDp)

    Surface(
        shape = cardShape,
        color = cardBg,
        shadowElevation = if (isActive) 8.dp else 3.dp,
        border = border,
        modifier = modifier
            .clip(cardShape)
            .combinedClickable(
                onClick = onSelect,
                onLongClick = onEdit,
            )
            .testTag("workspace_card_${workspace.id}"),
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
        ) {
            // Workspace Header Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .background(if (isDark) DarkSurfaceVariant.copy(alpha = 0.4f) else ProstoCanvasFloor)
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    // Color Pill Indicator
                    Box(
                        modifier = Modifier
                            .width(8.dp)
                            .height(20.dp)
                            .background(workspaceColor, ProstoShapes.Pill),
                    )

                    Text(
                        text = workspace.name,
                        style = typography.headlineSm.copy(
                            fontWeight = if (isActive) FontWeight.Bold else FontWeight.SemiBold,
                        ),
                        color = if (isDark) DarkOnSurface else ProstoDarkText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )

                    if (isActive) {
                        Surface(
                            shape = ProstoShapes.Pill,
                            color = workspaceColor.copy(alpha = 0.15f),
                            modifier = Modifier.padding(start = 2.dp),
                        ) {
                            Text(
                                text = stringResource(R.string.workspace_active_badge),
                                style = typography.labelSm.copy(
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                ),
                                color = workspaceColor,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                            )
                        }
                    }
                }

                // Options (Edit / Delete)
                IconButton(
                    onClick = onEdit,
                    modifier = Modifier
                        .size(32.dp)
                        .testTag("workspace_options_${workspace.id}"),
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = stringResource(R.string.workspace_options),
                        tint = ProstoMutedSlate,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }

            // Tab Preview Section
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(if (isDark) DarkBackground else ProstoCanvasFloor),
            ) {
                val file = activeTab?.snapshotPath?.let { File(it) }
                if (file != null && file.exists()) {
                    AsyncImage(
                        model = file,
                        contentDescription = activeTab.title,
                        contentScale = ContentScale.Crop,
                        alignment = Alignment.TopCenter,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Default.Public,
                            contentDescription = null,
                            tint = ProstoMutedSlate.copy(alpha = 0.35f),
                            modifier = Modifier.size(48.dp),
                        )
                    }
                }
            }

            // Tab Title / Bottom Info Footer
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .background(if (isDark) DarkSurfaceVariant.copy(alpha = 0.25f) else Color.White)
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (activeTab != null && activeTab.url.isNotBlank()) {
                    SiteFavicon(
                        faviconUrl = activeTab.faviconUrl,
                        imageSize = 16.dp,
                        containerSize = 18.dp,
                        shape = CircleShape,
                        placeholder = {
                            Box(
                                modifier = Modifier
                                    .size(18.dp)
                                    .clip(CircleShape)
                                    .background(if (isDark) DarkSurfaceVariant else ProstoPaleBlue),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Language,
                                    contentDescription = null,
                                    tint = workspaceColor,
                                    modifier = Modifier.size(12.dp),
                                )
                            }
                        },
                    )

                    Text(
                        text = activeTab.title.ifEmpty { activeTab.url },
                        style = typography.bodySm.copy(fontWeight = FontWeight.Medium),
                        color = if (isDark) DarkOnSurface else ProstoDarkText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                } else {
                    Text(
                        text = stringResource(R.string.workspace_no_tabs),
                        style = typography.bodySm,
                        color = ProstoMutedSlate,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                }

                // Tab Count Pill
                Surface(
                    shape = ProstoShapes.Pill,
                    color = if (isDark) DarkSurfaceVariant else ProstoCanvasFloor,
                    border = ProstoBorders.hairlineBorder(isDark),
                ) {
                    Text(
                        text = pluralStringResource(R.plurals.workspace_tabs_count, tabCount, tabCount),
                        style = typography.labelSm.copy(fontSize = 11.sp),
                        color = ProstoMutedSlate,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                    )
                }
            }
        }
    }
}

/**
 * Trailing '+' card at the end of the carousel for creating a new workspace.
 */
@Composable
fun AddWorkspaceCard(
    isDark: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val typography = LocalProstoTypography.current
    val cardBg = if (isDark) DarkSurface.copy(alpha = 0.5f) else ProstoCanvasFloor.copy(alpha = 0.6f)

    Surface(
        shape = ProstoShapes.Card,
        color = cardBg,
        shadowElevation = 0.dp,
        border = ProstoBorders.cardBorder(isDark),
        modifier = modifier
            .clip(ProstoShapes.Card)
            .clickable(onClick = onClick)
            .testTag("add_workspace_card"),
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .size(54.dp)
                    .clip(CircleShape)
                    .background(ProstoBrandPrimary.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = stringResource(R.string.workspace_add),
                    tint = ProstoBrandPrimary,
                    modifier = Modifier.size(28.dp),
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = stringResource(R.string.workspace_add),
                style = typography.labelMd.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold),
                color = if (isDark) DarkOnSurface else ProstoDarkText,
            )
        }
    }
}

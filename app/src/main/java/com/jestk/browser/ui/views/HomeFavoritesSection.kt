package com.jestk.browser.ui.views

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Public
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jestk.browser.R
import com.jestk.browser.domain.model.FavoriteItem
import com.jestk.browser.domain.model.Workspace
import com.jestk.browser.engine.FaviconManager
import com.jestk.browser.ui.theme.DarkOnSurface
import com.jestk.browser.ui.theme.DarkSurface
import com.jestk.browser.ui.theme.DarkSurfaceVariant
import com.jestk.browser.ui.theme.LocalProstoTypography
import com.jestk.browser.ui.theme.ProstoBorders
import com.jestk.browser.ui.theme.ProstoBrandPrimary
import com.jestk.browser.ui.theme.ProstoCanvasFloor
import com.jestk.browser.ui.theme.ProstoDarkText
import com.jestk.browser.ui.theme.ProstoMutedSlate
import com.jestk.browser.ui.theme.ProstoShapes

// Favorite item model for 4-column layout
internal data class PresetFavorite(
    val id: Long? = null,
    val title: String,
    val url: String,
    val icon: ImageVector = Icons.Default.Public,
    val iconTint: Color = ProstoBrandPrimary,
    val bgBrush: Brush? = null,
    val iconUrl: String? = null,
)

@Composable
internal fun FavoritesSection(
    favorites: List<FavoriteItem>,
    isExpanded: Boolean,
    canBookmarkCurrent: Boolean,
    isDark: Boolean,
    workspaces: List<Workspace> = emptyList(),
    activeWorkspaceId: String = "",
    onSelectUrl: (String) -> Unit,
    onDeleteFavorite: (Long) -> Unit,
    onMoveFavoriteToWorkspace: ((Long, String) -> Unit)? = null,
    onBookmarkCurrent: () -> Unit,
    onToggleExpand: () -> Unit,
    onAddClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        // Section Header
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.favorites_title),
                style = LocalProstoTypography.current.headlineSm,
                color = if (isDark) DarkOnSurface else ProstoDarkText,
            )

            if (canBookmarkCurrent) {
                TextButton(onClick = onBookmarkCurrent) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = ProstoBrandPrimary,
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = stringResource(R.string.home_bookmark_tab),
                        style = LocalProstoTypography.current.bodySm.copy(fontWeight = FontWeight.SemiBold),
                        color = ProstoBrandPrimary,
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Level 1 Solid Card enclosing the favorites grid
        Surface(
            shape = ProstoShapes.Card,
            color = if (isDark) DarkSurface else Color.White,
            border = ProstoBorders.cardBorder(isDark),
            shadowElevation = 2.dp,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
            ) {
                val itemsToDisplay: List<PresetFavorite> =
                    favorites
                        .map { fav ->
                            val fallbackFavicon = fav.iconUrl ?: FaviconManager.extractFallbackFaviconUrl(fav.url)
                            PresetFavorite(
                                id = fav.id,
                                title = fav.title.ifEmpty { fav.url },
                                url = fav.url,
                                icon = Icons.Default.Public,
                                iconTint = ProstoBrandPrimary,
                                iconUrl = fallbackFavicon,
                            )
                        }.let { list ->
                            if (isExpanded) list else list.take(7)
                        }

                if (itemsToDisplay.isEmpty()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        AddFavoriteButton(
                            isDark = isDark,
                            onClick = onAddClick,
                            modifier = Modifier.weight(1f),
                        )
                        repeat(3) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                } else {
                    val rows = itemsToDisplay.chunked(4)

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        rows.forEachIndexed { rowIndex, rowItems ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                rowItems.forEach { item ->
                                    FavoriteSquircleCard(
                                        item = item,
                                        isDark = isDark,
                                        workspaces = workspaces,
                                        activeWorkspaceId = activeWorkspaceId,
                                        onClick = { onSelectUrl(item.url) },
                                        onDelete = item.id?.let { id -> { onDeleteFavorite(id) } },
                                        onMoveToWorkspace = item.id?.let { favId -> { wsId -> onMoveFavoriteToWorkspace?.invoke(favId, wsId) } },
                                        modifier = Modifier.weight(1f),
                                    )
                                }

                                val emptySlots = 4 - rowItems.size
                                if (emptySlots > 0 && rowIndex == rows.lastIndex) {
                                    AddFavoriteButton(
                                        isDark = isDark,
                                        onClick = onAddClick,
                                        modifier = Modifier.weight(1f),
                                    )
                                    repeat(emptySlots - 1) {
                                        Spacer(modifier = Modifier.weight(1f))
                                    }
                                }
                            }
                        }

                        if (itemsToDisplay.size % 4 == 0) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                AddFavoriteButton(
                                    isDark = isDark,
                                    onClick = onAddClick,
                                    modifier = Modifier.weight(1f),
                                )
                                repeat(3) {
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }

                if (favorites.size > 7) {
                    Spacer(modifier = Modifier.height(14.dp))
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center,
                    ) {
                        OutlinedButton(
                            onClick = onToggleExpand,
                            shape = ProstoShapes.Pill,
                            border = BorderStroke(1.dp, ProstoBrandPrimary.copy(alpha = 0.35f)),
                            colors =
                                ButtonDefaults.outlinedButtonColors(
                                    contentColor = ProstoBrandPrimary,
                                ),
                        ) {
                            Text(
                                text = if (isExpanded) stringResource(R.string.favorites_show_less) else stringResource(R.string.favorites_show_all, favorites.size),
                                style = LocalProstoTypography.current.labelMd,
                                color = ProstoBrandPrimary,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AddFavoriteButton(
    isDark: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val squircleBg = if (isDark) DarkSurfaceVariant.copy(alpha = 0.50f) else ProstoCanvasFloor
    val border = BorderStroke(1.dp, ProstoBrandPrimary.copy(alpha = 0.35f))

    Column(
        modifier = modifier.clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = squircleBg,
            border = border,
            shadowElevation = 0.dp,
            modifier = Modifier.size(44.dp),
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = stringResource(R.string.a11y_add_favorite),
                    tint = ProstoBrandPrimary,
                    modifier = Modifier.size(20.dp),
                )
            }
        }

        Text(
            text = stringResource(R.string.add_action),
            style =
                LocalProstoTypography.current.bodySm.copy(
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                ),
            color = ProstoMutedSlate,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

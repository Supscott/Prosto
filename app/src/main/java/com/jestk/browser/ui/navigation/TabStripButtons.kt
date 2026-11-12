package com.jestk.browser.ui.navigation

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Language
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jestk.browser.R
import com.jestk.browser.domain.model.Tab
import com.jestk.browser.domain.model.TabFolder
import com.jestk.browser.domain.model.Workspace
import com.jestk.browser.ui.components.SiteFavicon
import com.jestk.browser.ui.theme.DarkOnSurfaceVariant
import com.jestk.browser.ui.theme.LocalProstoTypography
import com.jestk.browser.ui.theme.ProstoMutedSlate

/**
 * Circular inactive tab button with favicon or site initial glyph.
 * Transparent background, ProstoMutedSlate icons and text, label-md typography.
 */
@Composable
fun InactiveTabButton(
    tab: Tab,
    isDark: Boolean,
    backgroundColor: Color = Color.Transparent,
    border: BorderStroke? = null,
    isLoaded: Boolean = true,
    grayUnloadedTabs: Boolean = true,
    isRemovePending: Boolean = false,
    removeProgress: Float = 0f,
    onClick: () -> Unit,
    onClose: () -> Unit,
    onAddFavorite: (String, String) -> Unit,
    modifier: Modifier = Modifier,
    menuExpanded: Boolean = false,
    onMenuDismiss: () -> Unit = {},
    folderId: String? = tab.folderId,
    availableFolders: List<TabFolder> = emptyList(),
    onCreateFolder: (() -> Unit)? = null,
    onAddToFolder: ((String) -> Unit)? = null,
    onRemoveFromFolder: (() -> Unit)? = null,
    availableWorkspaces: List<Workspace> = emptyList(),
    onMoveToWorkspace: ((String) -> Unit)? = null,
) {
    val context = LocalContext.current
    val typography = LocalProstoTypography.current
    val isGray = grayUnloadedTabs && !isLoaded
    val contentAlpha = if (isGray) 0.45f else 1.0f
    val normalTint = if (isDark) {
        if (isGray) DarkOnSurfaceVariant.copy(alpha = 0.6f) else DarkOnSurfaceVariant
    } else {
        if (isGray) ProstoMutedSlate.copy(alpha = 0.6f) else ProstoMutedSlate
    }
    val grayscaleFilter = remember(isGray) {
        if (isGray) ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) }) else null
    }
    val crossAlpha = if (isRemovePending) 1f else ((removeProgress - 0.15f) / 0.70f).coerceIn(0f, 1f)

    Surface(
        shape = CircleShape,
        color = backgroundColor,
        border = border,
        shadowElevation = 0.dp,
        modifier =
            modifier
                .size(38.dp)
                .clip(CircleShape)
                .clickable(onClick = onClick),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = contentAlpha },
            contentAlignment = Alignment.Center,
        ) {
            val isHome = tab.isHomeTab
            if (crossAlpha > 0f) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = stringResource(R.string.a11y_remove_tab),
                    tint = normalTint,
                    modifier = Modifier
                        .size(16.dp)
                        .graphicsLayer { alpha = crossAlpha },
                )
            }
            if (crossAlpha < 1f) {
                Box(
                    modifier = Modifier.graphicsLayer { alpha = 1f - crossAlpha },
                    contentAlignment = Alignment.Center,
                ) {
                    if (isHome) {
                        Icon(
                            imageVector = Icons.Default.Home,
                            contentDescription = stringResource(R.string.tab_home_title),
                            tint = normalTint,
                            modifier = Modifier.size(18.dp),
                        )
                    } else {
                        SiteFavicon(
                            faviconUrl = tab.faviconUrl,
                            imageSize = 18.dp,
                            colorFilter = grayscaleFilter,
                            placeholder = {
                                val initial =
                                    tab.title
                                        .trim()
                                        .firstOrNull()
                                        ?.uppercaseChar()
                                if (initial != null && initial.isLetterOrDigit()) {
                                    Text(
                                        text = initial.toString(),
                                        style =
                                            typography.labelMd.copy(
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 12.sp,
                                            ),
                                        color = normalTint,
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.Language,
                                        contentDescription = tab.title,
                                        tint = normalTint,
                                        modifier = Modifier.size(18.dp),
                                    )
                                }
                            },
                        )
                    }
                }
            }

            TabContextMenu(
                expanded = menuExpanded,
                onDismissRequest = onMenuDismiss,
                isDark = isDark,
                onCloseTab = onClose,
                onCopyUrl = {
                    copyUrlToClipboard(context, tab.url)
                },
                onAddFavorite = {
                    if (tab.url.isNotBlank() && !tab.url.startsWith("about:")) {
                        onAddFavorite(tab.title, tab.url)
                        Toast.makeText(context, R.string.toast_added_to_favorites, Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, R.string.toast_cannot_add_home_to_favorites, Toast.LENGTH_SHORT).show()
                    }
                },
                onShareLink = {
                    shareWebpageUrl(context, tab.url)
                },
                folderId = folderId,
                availableFolders = availableFolders,
                onCreateFolder = onCreateFolder,
                onAddToFolder = onAddToFolder,
                onRemoveFromFolder = onRemoveFromFolder,
                availableWorkspaces = availableWorkspaces,
                onMoveToWorkspace = onMoveToWorkspace,
            )
        }
    }
}

/**
 * Pinned persistent circular '+' new tab button on the right edge of the dock.
 */
@Composable
fun AddTabDockButton(
    isDark: Boolean,
    backgroundColor: Color = Color.Transparent,
    border: BorderStroke? = null,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val normalTint = if (isDark) DarkOnSurfaceVariant else ProstoMutedSlate

    Surface(
        shape = CircleShape,
        color = backgroundColor,
        border = border,
        shadowElevation = 0.dp,
        modifier =
            modifier
                .size(38.dp)
                .clip(CircleShape)
                .clickable(onClick = onClick),
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = stringResource(R.string.action_new_tab),
                tint = normalTint,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

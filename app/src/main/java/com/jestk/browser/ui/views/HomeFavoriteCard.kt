package com.jestk.browser.ui.views

import android.content.ClipData
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.toClipEntry
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.jestk.browser.R
import com.jestk.browser.domain.model.Workspace
import com.jestk.browser.engine.FaviconManager
import com.jestk.browser.ui.components.workspaceDisplayName
import com.jestk.browser.ui.theme.DarkOnSurface
import com.jestk.browser.ui.theme.DarkSurfaceVariant
import com.jestk.browser.ui.theme.LocalProstoTypography
import com.jestk.browser.ui.theme.ProstoBrandPrimary
import com.jestk.browser.ui.theme.ProstoCanvasFloor
import com.jestk.browser.ui.theme.ProstoDarkText
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun FavoriteSquircleCard(
    item: PresetFavorite,
    isDark: Boolean,
    workspaces: List<Workspace> = emptyList(),
    activeWorkspaceId: String = "",
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onDelete: (() -> Unit)? = null,
    onMoveToWorkspace: ((String) -> Unit)? = null,
) {
    var showMenu by remember { mutableStateOf(false) }
    var showWorkspacePicker by remember { mutableStateOf(false) }
    val clipboard = LocalClipboard.current
    val coroutineScope = rememberCoroutineScope()
    val squircleBg = if (isDark) DarkSurfaceVariant else ProstoCanvasFloor
    val availableWorkspaces = remember(workspaces, activeWorkspaceId) {
        workspaces.filter { it.id != activeWorkspaceId }
    }

    Box(modifier = modifier) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .combinedClickable(
                        onClick = onClick,
                        onLongClick = { showMenu = true },
                    ),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = squircleBg,
                border = null,
                shadowElevation = 0.dp,
                modifier = Modifier.size(44.dp),
            ) {
                Box(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .then(
                                if (item.bgBrush != null) {
                                    Modifier.background(item.bgBrush)
                                } else {
                                    Modifier
                                },
                            ),
                    contentAlignment = Alignment.Center,
                ) {
                    var isFaviconSuccess by remember(item.iconUrl) { mutableStateOf(false) }
                    val imageModel =
                        remember(item.iconUrl) {
                            FaviconManager.toImageModel(item.iconUrl)
                        }

                    if (!isFaviconSuccess) {
                        Icon(
                            imageVector = item.icon,
                            contentDescription = item.title,
                            tint = ProstoBrandPrimary,
                            modifier = Modifier.size(20.dp),
                        )
                    }

                    if (imageModel != null) {
                        AsyncImage(
                            model = imageModel,
                            contentDescription = item.title,
                            contentScale = ContentScale.Fit,
                            onSuccess = { isFaviconSuccess = true },
                            onError = { isFaviconSuccess = false },
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }

            Text(
                text = item.title,
                style =
                    LocalProstoTypography.current.bodySm.copy(
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                    ),
                color = if (isDark) DarkOnSurface else ProstoDarkText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        DropdownMenu(
            expanded = showMenu,
            onDismissRequest = {
                showMenu = false
                showWorkspacePicker = false
            },
        ) {
            if (showWorkspacePicker) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.tab_context_move_to_workspace_title)) },
                    leadingIcon = {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                    },
                    onClick = { showWorkspacePicker = false },
                )
                availableWorkspaces.forEach { ws ->
                    DropdownMenuItem(
                        text = { Text(workspaceDisplayName(ws)) },
                        leadingIcon = {
                            Box(
                                modifier = Modifier
                                    .size(12.dp)
                                    .clip(CircleShape)
                                    .background(Color(ws.color)),
                            )
                        },
                        onClick = {
                            showMenu = false
                            showWorkspacePicker = false
                            onMoveToWorkspace?.invoke(ws.id)
                        },
                    )
                }
            } else {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.copy_url_action)) },
                    leadingIcon = {
                        Icon(
                            Icons.Default.ContentCopy,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                    },
                    onClick = {
                        showMenu = false
                        coroutineScope.launch {
                            val clipData = ClipData.newPlainText("URL", item.url)
                            clipboard.setClipEntry(clipData.toClipEntry())
                        }
                    },
                )
                if (item.id != null && availableWorkspaces.isNotEmpty() && onMoveToWorkspace != null) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.move_to_workspace)) },
                        leadingIcon = {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                        },
                        onClick = {
                            showWorkspacePicker = true
                        },
                    )
                }
                if (item.id != null && onDelete != null) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.delete_bookmark_action), color = MaterialTheme.colorScheme.error) },
                        leadingIcon = {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(18.dp),
                            )
                        },
                        onClick = {
                            showMenu = false
                            onDelete()
                        },
                    )
                }
            }
        }
    }
}

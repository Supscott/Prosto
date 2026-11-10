package com.jestk.browser.ui.overlays

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jestk.browser.R
import com.jestk.browser.domain.model.Workspace
import com.jestk.browser.ui.theme.DarkBackground
import com.jestk.browser.ui.theme.DarkOnSurface
import com.jestk.browser.ui.theme.DarkSurface
import com.jestk.browser.ui.theme.LocalIsDarkTheme
import com.jestk.browser.ui.theme.LocalProstoTypography
import com.jestk.browser.ui.theme.ProstoBorders
import com.jestk.browser.ui.theme.ProstoCanvasFloor
import com.jestk.browser.ui.theme.ProstoDarkText
import com.jestk.browser.ui.theme.ProstoMutedSlate
import com.jestk.browser.ui.theme.ProstoShapes
import com.jestk.browser.ui.viewmodel.BrowserUiEvent
import com.jestk.browser.ui.viewmodel.BrowserUiState

/**
 * Android Recents-style Workspaces Overview Screen:
 * Displays a horizontal carousel of workspace cards with their accent colors, titles, tab previews,
 * and open tab counts. Includes a trailing '+' card to add workspaces, and an options sheet to edit or delete.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun WorkspacesOverviewScreen(
    uiState: BrowserUiState,
    onEvent: (BrowserUiEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val isDark = LocalIsDarkTheme.current
    val typography = LocalProstoTypography.current

    // Back button dismisses overview
    BackHandler {
        onEvent(BrowserUiEvent.CloseWorkspacesOverview)
    }

    var editingWorkspace by remember { mutableStateOf<Workspace?>(null) }
    var isNewWorkspaceSheetVisible by remember { mutableStateOf(false) }
    var workspaceToDelete by remember { mutableStateOf<Workspace?>(null) }

    val workspaces = uiState.workspaces
    val activeWorkspaceIndex = remember(workspaces, uiState.activeWorkspaceId) {
        val idx = workspaces.indexOfFirst { it.id == uiState.activeWorkspaceId }
        if (idx >= 0) idx else 0
    }

    // Pager item count = workspaces.size + 1 (trailing '+' card)
    val pageCount = workspaces.size + 1
    val pagerState = rememberPagerState(
        initialPage = activeWorkspaceIndex,
        pageCount = { pageCount },
    )

    // Keep pager centered on active workspace when it changes
    LaunchedEffect(activeWorkspaceIndex) {
        if (activeWorkspaceIndex in 0 until pageCount && pagerState.currentPage != activeWorkspaceIndex) {
            pagerState.scrollToPage(activeWorkspaceIndex)
        }
    }

    val screenBg = if (isDark) DarkBackground else ProstoCanvasFloor

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(screenBg)
            .statusBarsPadding()
            .navigationBarsPadding()
            .testTag("workspaces_overview_screen"),
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
        ) {
            // Header Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.workspaces_title),
                    style = typography.headlineMd,
                    color = if (isDark) DarkOnSurface else ProstoDarkText,
                    fontWeight = FontWeight.Bold,
                )

                // Close Button Pill
                Surface(
                    shape = CircleShape,
                    color = if (isDark) DarkSurface else Color.White,
                    border = ProstoBorders.floatingBorder(isDark),
                    shadowElevation = 2.dp,
                    modifier = Modifier.size(36.dp),
                ) {
                    IconButton(
                        onClick = { onEvent(BrowserUiEvent.CloseWorkspacesOverview) },
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("workspaces_overview_close_button"),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = stringResource(R.string.folder_close),
                            tint = if (isDark) DarkOnSurface else ProstoDarkText,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }

            // Recents-style Horizontal Carousel
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                HorizontalPager(
                    state = pagerState,
                    beyondViewportPageCount = 1,
                    contentPadding = PaddingValues(horizontal = 48.dp),
                    pageSpacing = 16.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(),
                ) { page ->
                    if (page < workspaces.size) {
                        val workspace = workspaces[page]
                        val isActive = workspace.id == uiState.activeWorkspaceId

                        // Tab count and active tab for this workspace
                        val allTabs = if (uiState.allTabs.isNotEmpty()) uiState.allTabs else uiState.tabs
                        val wsTabs = allTabs.filter { it.workspaceId == workspace.id }
                        val activeTab = if (isActive) {
                            uiState.activeTab ?: wsTabs.find { it.id == uiState.activeTabId } ?: wsTabs.firstOrNull()
                        } else {
                            wsTabs.find { it.isActive }
                                ?: wsTabs.maxByOrNull { it.lastActiveTimestamp }
                                ?: wsTabs.firstOrNull()
                        }

                        WorkspaceOverviewCard(
                            workspace = workspace,
                            isActive = isActive,
                            isDark = isDark,
                            tabCount = wsTabs.size,
                            activeTab = activeTab,
                            onSelect = {
                                if (workspace.id != uiState.activeWorkspaceId) {
                                    onEvent(BrowserUiEvent.SwitchWorkspace(workspace.id))
                                }
                                onEvent(BrowserUiEvent.CloseWorkspacesOverview)
                            },
                            onEdit = {
                                editingWorkspace = workspace
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .fillMaxHeight(0.86f)
                                .padding(vertical = 12.dp),
                        )
                    } else {
                        // Trailing '+' Add Workspace Card
                        AddWorkspaceCard(
                            isDark = isDark,
                            onClick = { isNewWorkspaceSheetVisible = true },
                            modifier = Modifier
                                .fillMaxWidth()
                                .fillMaxHeight(0.86f)
                                .padding(vertical = 12.dp),
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }

        // Edit Workspace BottomSheet
        val currentEditing = editingWorkspace
        if (currentEditing != null) {
            WorkspaceEditBottomSheet(
                isDark = isDark,
                workspace = currentEditing,
                isLastWorkspace = workspaces.size <= 1,
                onDismiss = { editingWorkspace = null },
                onSave = { newName, newColor ->
                    onEvent(BrowserUiEvent.UpdateWorkspace(currentEditing.copy(name = newName, color = newColor)))
                    editingWorkspace = null
                },
                onDelete = {
                    workspaceToDelete = currentEditing
                    editingWorkspace = null
                },
            )
        }

        // New Workspace BottomSheet
        if (isNewWorkspaceSheetVisible) {
            WorkspaceEditBottomSheet(
                isDark = isDark,
                workspace = null,
                isLastWorkspace = false,
                onDismiss = { isNewWorkspaceSheetVisible = false },
                onSave = { name, color ->
                    onEvent(BrowserUiEvent.CreateWorkspace(name, color))
                    isNewWorkspaceSheetVisible = false
                },
            )
        }

        // Delete Confirmation Dialog
        val toDelete = workspaceToDelete
        if (toDelete != null) {
            WorkspaceDeleteConfirmDialog(
                workspaceName = toDelete.name,
                isDark = isDark,
                onConfirm = {
                    onEvent(BrowserUiEvent.DeleteWorkspace(toDelete.id))
                    workspaceToDelete = null
                },
                onDismiss = {
                    workspaceToDelete = null
                },
            )
        }
    }
}

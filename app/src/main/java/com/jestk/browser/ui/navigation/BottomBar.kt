package com.jestk.browser.ui.navigation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.systemGestureExclusion
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Search
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.jestk.browser.R
import com.jestk.browser.domain.model.Tab
import com.jestk.browser.domain.model.ThemeMode
import com.jestk.browser.ui.theme.DarkDockSurface
import com.jestk.browser.ui.theme.JestkBrowserTheme
import com.jestk.browser.ui.theme.LocalActiveWorkspaceColor
import com.jestk.browser.ui.theme.LocalIsDarkTheme
import com.jestk.browser.ui.theme.ProstoBorders
import com.jestk.browser.ui.theme.ProstoShapes
import com.jestk.browser.ui.theme.prostoFloatingSurface
import com.jestk.browser.ui.theme.prostoSkyBackground
import com.jestk.browser.ui.overlays.WorkspaceSwitcherState
import com.jestk.browser.ui.overlays.rememberWorkspaceSwitcherState
import com.jestk.browser.ui.viewmodel.BrowserUiEvent
import com.jestk.browser.ui.viewmodel.BrowserUiState
import kotlinx.coroutines.launch

/**
 * Top-level orchestrator composable for bottom navigation bar.
 * Adheres to Prosto Canvas design system:
 * - Level 2 floating capsule ([ProstoShapes.Dock] = 32.dp).
 * - Dock surface: #FFFFFF 80% opacity in Light mode, #0D1824 80% opacity in Dark mode.
 * - Active tab capsule: solid 100% #FFFFFF in Light mode, #0D1824 in Dark mode with light elevation shadow (2.dp).
 * - Elevation & Depth: Level 2 ambient elevation shadow (10.dp cerulean-tinted shadow) and crisp rim border. NO BLUR.
 * - Assembles [TabStripRow], [ActionPopupMenu], [SwipeUpHint], and the search / menu [DockSideButton]s.
 * - Swiping up on the dock drives [workspaceSwitcher]: the whole screen follows the finger into
 *   the workspaces carousel.
 */
@Composable
fun UnifiedBottomBar(
    uiState: BrowserUiState,
    onEvent: (BrowserUiEvent) -> Unit,
    modifier: Modifier = Modifier,
    workspaceSwitcher: WorkspaceSwitcherState = rememberWorkspaceSwitcherState(),
) {
    val currentOnEvent by rememberUpdatedState(onEvent)
    val currentUiState by rememberUpdatedState(uiState)
    val switcherScope = rememberCoroutineScope()
    val isDark = LocalIsDarkTheme.current
    val isHome = uiState.isHome

    val workspaceColor = uiState.activeWorkspace?.let { Color(it.color) }
        ?: LocalActiveWorkspaceColor.current

    // Dock container: 80% opacity in both light (white) and dark (#0D1824) with workspace color tint
    val baseDockBg = if (isDark) DarkDockSurface.copy(alpha = 0.80f) else Color.White.copy(alpha = 0.80f)
    val dockBg = if (workspaceColor != null) {
        val tintAlpha = if (isDark) 0.14f else 0.10f
        workspaceColor.copy(alpha = tintAlpha).compositeOver(baseDockBg)
    } else {
        baseDockBg
    }

    // Active tab capsule: 100% solid with subtle workspace tint for crisp shadow casting and Arc style
    val baseActivePillBg = if (isDark) DarkDockSurface else Color.White
    val activePillBg = if (workspaceColor != null) {
        val pillTintAlpha = if (isDark) 0.08f else 0.04f
        workspaceColor.copy(alpha = pillTintAlpha).compositeOver(baseActivePillBg)
    } else {
        baseActivePillBg
    }

    val activePillBorder: BorderStroke? = if (workspaceColor != null) {
        BorderStroke(
            1.dp,
            Brush.verticalGradient(
                colors = if (isDark) {
                    listOf(
                        workspaceColor.copy(alpha = 0.45f),
                        workspaceColor.copy(alpha = 0.15f),
                    )
                } else {
                    listOf(
                        workspaceColor.copy(alpha = 0.35f),
                        workspaceColor.copy(alpha = 0.10f),
                    )
                }
            )
        )
    } else {
        null
    }

    val dockBorder: BorderStroke? = if (workspaceColor != null) {
        ProstoBorders.floatingBorder(isDark = isDark, workspaceColor = workspaceColor)
    } else {
        null
    }

    val circleBtnBg = Color.Transparent

    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.BottomCenter
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 1. "Swipe to change" workspace hint (visible when active tab is Home)
            AnimatedVisibility(
                visible = isHome && !uiState.isMenuExpanded,
                enter = fadeIn(animationSpec = tween(200)) + slideInVertically(animationSpec = tween(200)) { it / 2 },
                exit = fadeOut(animationSpec = tween(150)) + slideOutVertically(animationSpec = tween(150)) { it / 2 },
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(bottom = 6.dp)
            ) {
                SwipeUpHint(
                    isDark = isDark,
                    onClick = {
                        onEvent(BrowserUiEvent.OpenWorkspacesOverview)
                    }
                )
            }

            // 2. Quick Search Engine Switcher (visible on search results page)
            val searchMatch = uiState.searchPageMatch
            val isEditExpanded = uiState.isSearchEngineSwitcherEditExpanded
            val hasVisibleEngines = uiState.visibleSearchPageEngines.isNotEmpty()
            AnimatedVisibility(
                visible = searchMatch != null && !isHome && !uiState.isMenuExpanded && (hasVisibleEngines || isEditExpanded),
                enter = fadeIn(animationSpec = tween(200)) + slideInVertically(animationSpec = tween(200)) { it / 2 },
                exit = fadeOut(animationSpec = tween(150)) + slideOutVertically(animationSpec = tween(150)) { it / 2 },
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(bottom = 8.dp)
            ) {
                if (searchMatch != null) {
                    val (selectedEngine, query) = searchMatch
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.End
                    ) {
                        SearchEngineSwitcherEditPopup(
                            expanded = isEditExpanded,
                            engines = uiState.searchEngines,
                            hiddenEngineIds = uiState.settings.hiddenSearchPageEngineIds,
                            isDark = isDark,
                            onToggleEngine = { engineId, isVisible ->
                                onEvent(BrowserUiEvent.ToggleSearchPageEngineVisibility(engineId, isVisible))
                            },
                            onDismiss = {
                                onEvent(BrowserUiEvent.SetSearchEngineSwitcherEditExpanded(false))
                            },
                            modifier = Modifier.padding(bottom = 8.dp, end = 12.dp)
                        )

                        SearchEngineSwitcherRow(
                            selectedEngine = selectedEngine,
                            engines = uiState.visibleSearchPageEngines,
                            onSelectEngine = { engine ->
                                onEvent(BrowserUiEvent.SwitchSearchEngineForQuery(engine, query))
                            },
                            isDark = isDark,
                            elevation = 3.dp,
                            isEditActive = isEditExpanded,
                            onEditClick = {
                                onEvent(BrowserUiEvent.SetSearchEngineSwitcherEditExpanded(!isEditExpanded))
                            }
                        )
                    }
                }
            }

            // 4. Floating Dock Row Container
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(top = 0.dp, bottom = 2.dp),
                contentAlignment = Alignment.BottomCenter
            ) {
                // Floating Dock Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .padding(horizontal = 16.dp)
                        .systemGestureExclusion(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Tactile Floating Capsule Dock & Tab Strip
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp)
                            .testTag("dock_capsule")
                            .dockSwipeUpGesture(
                                onDragStart = {
                                    workspaceSwitcher.startDrag()
                                    if (!currentUiState.isWorkspacesOverviewVisible) {
                                        currentOnEvent(BrowserUiEvent.OpenWorkspacesOverview)
                                    }
                                },
                                onDrag = { upwardPx ->
                                    switcherScope.launch { workspaceSwitcher.dragTo(upwardPx) }
                                },
                                onDragEnd = { upwardVelocity, cancelled ->
                                    switcherScope.launch {
                                        val opened = workspaceSwitcher.endDrag(
                                            if (cancelled) Float.NEGATIVE_INFINITY else upwardVelocity,
                                        )
                                        if (!opened) currentOnEvent(BrowserUiEvent.CloseWorkspacesOverview)
                                    }
                                },
                            )
                    ) {
                        // Tactile Floating Capsule Dock Surface (clipped cleanly to stadium capsule)
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .prostoFloatingSurface(
                                    shape = ProstoShapes.Dock,
                                    isDark = isDark,
                                    backgroundColor = dockBg,
                                    border = dockBorder,
                                    elevation = 10.dp,
                                    clip = true,
                                )
                        )

                        // Tab Strip Row (tabs rest on the dock surface and clip cleanly to the dock capsule during horizontal scroll)
                        TabStripRow(
                            uiState = uiState,
                            onEvent = onEvent,
                            isDark = isDark,
                            centerPillBg = activePillBg,
                            centerPillBorder = activePillBorder,
                            circleBtnBg = circleBtnBg
                        )
                    }

                    // Dedicated Floating Search Button
                    DockSideButton(
                        icon = Icons.Default.Search,
                        contentDescription = stringResource(R.string.dock_search),
                        isActive = false,
                        isDark = isDark,
                        backgroundColor = dockBg,
                        border = dockBorder,
                        onClick = {
                            onEvent(BrowserUiEvent.OpenSearch(prefilledUrl = null, openInNewTab = true))
                        },
                        modifier = Modifier.testTag("dock_search_button"),
                    )

                    // Dedicated Floating Menu Button
                    DockSideButton(
                        icon = Icons.Default.Menu,
                        contentDescription = stringResource(R.string.dock_menu),
                        isActive = uiState.isMenuExpanded,
                        isDark = isDark,
                        backgroundColor = dockBg,
                        border = dockBorder,
                        onClick = {
                            onEvent(BrowserUiEvent.ToggleMenu())
                        },
                        modifier = Modifier.testTag("dock_menu_button"),
                    )
                }
            }
        }

        // 4. Animated Popup Menu Card anchored independently above the Menu Button
        ActionPopupMenu(
            expanded = uiState.isMenuExpanded,
            uiState = uiState,
            isDark = isDark,
            onEvent = onEvent,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .navigationBarsPadding()
                .padding(bottom = 56.dp)
        )
    }
}

@Preview(name = "BottomBar Light", showBackground = true, backgroundColor = 0xFFE9F6F2)
@Composable
fun UnifiedBottomBarLightPreview() {
    JestkBrowserTheme(themeMode = ThemeMode.LIGHT) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
                .prostoSkyBackground(isDark = false),
            contentAlignment = Alignment.BottomCenter
        ) {
            UnifiedBottomBar(
                uiState = BrowserUiState(
                    currentUrl = "https://example.com",
                    pageTitle = "Example Domain",
                    tabs = listOf(
                        Tab(id = "1", url = "https://example.com", title = "Example Domain", isActive = true),
                        Tab(id = "2", url = "https://duckduckgo.com", title = "DuckDuckGo", isActive = false)
                    ),
                    activeTabId = "1"
                ),
                onEvent = {}
            )
        }
    }
}

@Preview(name = "BottomBar Dark", showBackground = true, backgroundColor = 0xFF0E1513)
@Composable
fun UnifiedBottomBarDarkPreview() {
    JestkBrowserTheme(themeMode = ThemeMode.DARK) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
                .prostoSkyBackground(isDark = true),
            contentAlignment = Alignment.BottomCenter
        ) {
            UnifiedBottomBar(
                uiState = BrowserUiState(
                    currentUrl = "https://example.com",
                    pageTitle = "Example Domain",
                    tabs = listOf(
                        Tab(id = "1", url = "https://example.com", title = "Example Domain", isActive = true),
                        Tab(id = "2", url = "https://duckduckgo.com", title = "DuckDuckGo", isActive = false)
                    ),
                    activeTabId = "1"
                ),
                onEvent = {}
            )
        }
    }
}

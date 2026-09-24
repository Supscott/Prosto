package com.jestk.browser.ui.navigation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.jestk.browser.domain.model.Tab
import com.jestk.browser.domain.model.ThemeMode
import com.jestk.browser.ui.theme.DarkSurface
import com.jestk.browser.ui.theme.JestkBrowserTheme
import com.jestk.browser.ui.theme.LocalIsDarkTheme
import com.jestk.browser.ui.theme.SereneAccent
import com.jestk.browser.ui.theme.SereneBrandPrimary
import com.jestk.browser.ui.theme.SereneCardSurface
import com.jestk.browser.ui.theme.SereneShapes
import com.jestk.browser.ui.theme.sereneFloatingSurface
import com.jestk.browser.ui.theme.sereneSkyBackground
import com.jestk.browser.ui.viewmodel.BrowserUiEvent
import com.jestk.browser.ui.viewmodel.BrowserUiState

/**
 * Top-level orchestrator composable for bottom navigation bar.
 * Adheres to Serene Canvas design system:
 * - Level 2 floating capsule ([SereneShapes.Dock] = 32.dp).
 * - Solid surface: pure #FFFFFF in Light mode ([SereneCardSurface]), #1E293B in Dark mode ([DarkSurface]).
 * - Elevation & Depth: Level 2 ambient elevation shadow (10.dp cerulean-tinted shadow) and crisp rim border. NO BLUR.
 * - Scroll-driven auto-hide offset translation via [BrowserUiState.bottomBarOffsetY].
 * - Assembles [TabStripRow], [ActionPopupMenu], [SwipeUpHint], and [ActionDockMenuButton].
 */
@Composable
fun UnifiedBottomBar(
    uiState: BrowserUiState,
    onEvent: (BrowserUiEvent) -> Unit,
    modifier: Modifier = Modifier,
    hazeState: Any? = null,
) {
    val density = LocalDensity.current
    val currentOnEvent by rememberUpdatedState(onEvent)
    val isDark = LocalIsDarkTheme.current
    val isHome = uiState.isHome

    // Solid surfaces for Serene Canvas floating dock
    val dockBg = if (isDark) DarkSurface else SereneCardSurface
    val activePillBg = if (isDark) Color(0x2660A5FA) else SereneBrandPrimary.copy(alpha = 0.10f)
    val activePillBorder = if (isDark) {
        androidx.compose.foundation.BorderStroke(1.dp, SereneAccent.copy(alpha = 0.40f))
    } else {
        androidx.compose.foundation.BorderStroke(1.dp, SereneBrandPrimary.copy(alpha = 0.25f))
    }
    val circleBtnBg = Color.Transparent

    Box(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                translationY = uiState.bottomBarOffsetY
            },
        contentAlignment = Alignment.BottomCenter
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.End
        ) {
            // 1. Animated Popup Menu Card anchored above the Menu Button
            ActionPopupMenu(
                expanded = uiState.isMenuExpanded,
                uiState = uiState,
                isDark = isDark,
                hazeState = hazeState,
                onEvent = onEvent
            )

            // 2. "Swipe up to search" Hint (visible when active tab is Home)
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
                        onEvent(BrowserUiEvent.OpenSearch(prefilledUrl = null, openInNewTab = true))
                    }
                )
            }

            // 3. Floating Dock Row Container
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(top = 0.dp, bottom = 4.dp),
                contentAlignment = Alignment.BottomCenter
            ) {
                // Floating Dock Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(62.dp)
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Tactile Floating Capsule Dock
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(58.dp)
                            .sereneFloatingSurface(
                                shape = SereneShapes.Dock,
                                isDark = isDark,
                                backgroundColor = dockBg,
                                elevation = 10.dp
                            )
                            .swipeUpToSearch(density) {
                                currentOnEvent(BrowserUiEvent.OpenSearch(prefilledUrl = null, openInNewTab = true))
                            }
                    ) {
                        TabStripRow(
                            uiState = uiState,
                            onEvent = onEvent,
                            isDark = isDark,
                            dockBg = dockBg,
                            centerPillBg = activePillBg,
                            centerPillBorder = activePillBorder,
                            circleBtnBg = circleBtnBg
                        )
                    }

                    // Dedicated Floating Menu Button
                    ActionDockMenuButton(
                        isExpanded = uiState.isMenuExpanded,
                        isDark = isDark,
                        backgroundColor = dockBg,
                        hazeState = hazeState,
                        onClick = {
                            onEvent(BrowserUiEvent.ToggleMenu())
                        }
                    )
                }
            }
        }
    }
}

/**
 * Top-level orchestrator composable for bottom bar navigation.
 */
@Composable
fun BottomBar(
    uiState: BrowserUiState,
    onEvent: (BrowserUiEvent) -> Unit,
    modifier: Modifier = Modifier,
    hazeState: Any? = null,
) {
    UnifiedBottomBar(
        uiState = uiState,
        onEvent = onEvent,
        modifier = modifier,
        hazeState = hazeState,
    )
}

/**
 * Backward compatibility alias for DualTierBottomBar.
 */
@Composable
fun DualTierBottomBar(
    uiState: BrowserUiState,
    onEvent: (BrowserUiEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    UnifiedBottomBar(
        uiState = uiState,
        onEvent = onEvent,
        modifier = modifier
    )
}

@Preview(name = "BottomBar Light", showBackground = true, backgroundColor = 0xFFF1F7FC)
@Composable
fun UnifiedBottomBarLightPreview() {
    JestkBrowserTheme(themeMode = ThemeMode.LIGHT) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
                .sereneSkyBackground(isDark = false),
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

@Preview(name = "BottomBar Dark", showBackground = true, backgroundColor = 0xFF0F172A)
@Composable
fun UnifiedBottomBarDarkPreview() {
    JestkBrowserTheme(themeMode = ThemeMode.DARK) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
                .sereneSkyBackground(isDark = true),
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

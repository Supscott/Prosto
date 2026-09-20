package com.jestk.browser.ui.navigation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.jestk.browser.domain.model.OverlayType
import com.jestk.browser.domain.model.Tab
import com.jestk.browser.domain.model.ThemeMode
import com.jestk.browser.ui.theme.JestkBrowserTheme
import com.jestk.browser.ui.theme.LiquidBorders
import com.jestk.browser.ui.theme.LiquidShapes
import com.jestk.browser.ui.theme.LiquidSurface
import com.jestk.browser.ui.theme.LiquidSurfaceContainerHigh
import com.jestk.browser.ui.theme.LocalHazeState
import com.jestk.browser.ui.theme.LocalIsDarkTheme
import com.jestk.browser.ui.theme.liquidFrostedGlass
import com.jestk.browser.ui.viewmodel.BrowserUiEvent
import com.jestk.browser.ui.viewmodel.BrowserUiState
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.haze

/**
 * Top-level orchestrator composable for bottom navigation bar.
 * Assembles:
 * - [TabStripRow] (horizontal scrollable tab strip with [ActiveTabPill], circular tabs, and pinned add tab button)
 * - [ActionPopupMenu] (animated popup actions card)
 * - [SwipeUpHint] (contextual gesture guidance above dock)
 * - [ActionDockMenuButton] (floating menu button)
 * - Scroll-driven auto-hide offset translation via [BrowserUiState.bottomBarOffsetY]
 */
@Composable
fun UnifiedBottomBar(
    uiState: BrowserUiState,
    onEvent: (BrowserUiEvent) -> Unit,
    modifier: Modifier = Modifier,
    hazeState: HazeState? = LocalHazeState.current
) {
    val density = LocalDensity.current
    val currentOnEvent by rememberUpdatedState(onEvent)
    val isDark = LocalIsDarkTheme.current
    val isHome = uiState.isHome

    // Colors & Styling for Tactile Liquid Floating Dock
    val dockBg = if (isDark) Color(0xFF1E1B1C) else LiquidSurface
    val centerPillBg = if (isDark) Color(0xFF2B2728) else Color.White
    val centerPillBorder = LiquidBorders.specularBorder(isDark)
    val circleBtnBg = if (isDark) Color(0xFF2C2829) else LiquidSurfaceContainerHigh

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
                    Surface(
                        shape = LiquidShapes.Dock,
                        color = dockBg,
                        border = LiquidBorders.specularBorder(isDark),
                        shadowElevation = 8.dp,
                        modifier = Modifier
                            .weight(1f)
                            .height(58.dp)
                            .swipeUpToSearch(density) {
                                currentOnEvent(BrowserUiEvent.OpenSearch(prefilledUrl = null, openInNewTab = true))
                            }
                    ) {
                        TabStripRow(
                            uiState = uiState,
                            onEvent = onEvent,
                            isDark = isDark,
                            dockBg = dockBg,
                            centerPillBg = centerPillBg,
                            centerPillBorder = centerPillBorder,
                            circleBtnBg = circleBtnBg
                        )
                    }

                    // Dedicated Floating Menu Button (rounded rectangle 38x58dp)
                    ActionDockMenuButton(
                        isExpanded = uiState.isMenuExpanded,
                        isDark = isDark,
                        backgroundColor = circleBtnBg,
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
    hazeState: HazeState? = LocalHazeState.current
) {
    UnifiedBottomBar(
        uiState = uiState,
        onEvent = onEvent,
        modifier = modifier,
        hazeState = hazeState
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

@Preview(name = "BottomBar Light", showBackground = true, backgroundColor = 0xFFC5CED9)
@Composable
fun UnifiedBottomBarLightPreview() {
    val hazeState = remember { dev.chrisbanes.haze.HazeState() }
    JestkBrowserTheme(themeMode = ThemeMode.LIGHT, hazeState = hazeState) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            Color(0xFFFFB4A2), // Coral peach
                            Color(0xFFE5989B), // Rose
                            Color(0xFFB5E2FA), // Sky blue
                            Color(0xFFEDDCD2)  // Sand
                        )
                    )
                )
                .haze(hazeState),
            contentAlignment = Alignment.BottomCenter
        ) {
            // Background colorful circles to demonstrate blur through glass
            Box(
                modifier = Modifier
                    .size(90.dp)
                    .align(Alignment.CenterStart)
                    .padding(start = 24.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFE63946))
            )
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .align(Alignment.Center)
                    .clip(CircleShape)
                    .background(Color(0xFF457B9D))
            )

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
                hazeState = hazeState,
                onEvent = {}
            )
        }
    }
}

@Preview(name = "BottomBar Dark", showBackground = true, backgroundColor = 0xFF100E0F)
@Composable
fun UnifiedBottomBarDarkPreview() {
    val hazeState = remember { dev.chrisbanes.haze.HazeState() }
    JestkBrowserTheme(themeMode = ThemeMode.DARK, hazeState = hazeState) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            Color(0xFF2B193D),
                            Color(0xFF1B263B),
                            Color(0xFF4A0E2E)
                        )
                    )
                )
                .haze(hazeState),
            contentAlignment = Alignment.BottomCenter
        ) {
            // Bright background geometric shapes to showcase blur through dark glass dock
            Box(
                modifier = Modifier
                    .size(90.dp)
                    .align(Alignment.CenterStart)
                    .padding(start = 24.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFFF758F))
            )
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .align(Alignment.Center)
                    .clip(CircleShape)
                    .background(Color(0xFF48CAE4))
            )

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
                hazeState = hazeState,
                onEvent = {}
            )
        }
    }
}

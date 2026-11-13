package com.jestk.browser.ui.views

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jestk.browser.R
import com.jestk.browser.ui.components.workspaceDisplayName
import com.jestk.browser.ui.theme.DarkOnSurface
import com.jestk.browser.ui.theme.LocalActiveWorkspaceColor
import com.jestk.browser.ui.theme.LocalIsDarkTheme
import com.jestk.browser.ui.theme.LocalProstoTypography
import com.jestk.browser.ui.theme.ProstoDarkText
import com.jestk.browser.ui.theme.prostoSkyBackground
import com.jestk.browser.ui.viewmodel.BrowserUiEvent
import com.jestk.browser.ui.viewmodel.HomeUiEvent
import com.jestk.browser.ui.viewmodel.BrowserUiState

private val ScreenGutter = 20.dp
private val SectionGap = 28.dp
private const val MAX_RECENT_TABS = 4

/**
 * Home Screen: deliberately quiet. A fresh install shows the workspace title, one tutorial card
 * and the (empty) favorites row; everything else appears only once there is something to show.
 * Search lives in the dock, so Home carries no input of its own.
 */
@Composable
fun HomeScreen(
    uiState: BrowserUiState,
    onEvent: (BrowserUiEvent) -> Unit,
    modifier: Modifier = Modifier,
    isPreview: Boolean = false,
) {
    val scrollState = rememberScrollState()
    val isDark = LocalIsDarkTheme.current
    val activeWorkspace = uiState.activeWorkspace
    val workspaceColor = activeWorkspace?.let { Color(it.color) } ?: LocalActiveWorkspaceColor.current
    val settings = uiState.settings
    val showPrivacyCard =
        settings.isHomePrivacyCardEnabled && uiState.blockedAdsCount + uiState.blockedTrackersCount > 0
    val cardModifier = Modifier.fillMaxWidth().padding(horizontal = ScreenGutter)

    Box(modifier = modifier.fillMaxSize()) {
        // Layer 1: Ambient background
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .prostoSkyBackground(isDark = isDark),
        )

        // Subtle ambient workspace tint gradient (Arc mobile styling, NO BLUR)
        if (workspaceColor != null) {
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    workspaceColor.copy(alpha = if (isDark) 0.10f else 0.06f),
                                    Color.Transparent,
                                ),
                                startY = 0f,
                                endY = 1200f,
                            ),
                        ),
            )
        }

        // Layer 2: Scrolling foreground content
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .then(if (isPreview) Modifier else Modifier.verticalScroll(scrollState))
                    .padding(top = 16.dp, bottom = 120.dp),
        ) {
            val workspaceTitle = activeWorkspace?.let { workspaceDisplayName(it) } ?: stringResource(R.string.home_greeting)
            val titleColor = workspaceColor ?: (if (isDark) DarkOnSurface else ProstoDarkText)

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier =
                    Modifier
                        .testTag("workspace_title")
                        .clickable(
                            onClickLabel = stringResource(R.string.a11y_open_workspaces),
                            onClick = { onEvent(BrowserUiEvent.OpenWorkspacesOverview) },
                        )
                        .padding(horizontal = ScreenGutter, vertical = 6.dp),
            ) {
                Text(
                    text = workspaceTitle,
                    style = LocalProstoTypography.current.headlineXl,
                    color = titleColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            if (!settings.isTutorialCompleted) {
                Spacer(modifier = Modifier.height(SectionGap))
                TutorialCard(
                    isDark = isDark,
                    onStartClick = { onEvent(HomeUiEvent.OpenTutorial) },
                    onDismissClick = { onEvent(HomeUiEvent.DismissTutorialCard) },
                    modifier = cardModifier,
                )
            }

            if (showPrivacyCard) {
                Spacer(modifier = Modifier.height(SectionGap))
                PrivacyProtectionCard(
                    blockedAdsCount = uiState.blockedAdsCount,
                    blockedTrackersCount = uiState.blockedTrackersCount,
                    isDark = isDark,
                    modifier = cardModifier,
                )
            }

            Spacer(modifier = Modifier.height(SectionGap + 8.dp))

            FavoritesSection(
                favorites = uiState.favorites,
                isExpanded = uiState.isFavoritesExpanded,
                canBookmarkCurrent = uiState.currentUrl.isNotEmpty() && !uiState.currentUrl.startsWith("about:"),
                isDark = isDark,
                workspaces = uiState.workspaces,
                activeWorkspaceId = uiState.activeWorkspaceId,
                onSelectUrl = { url -> onEvent(BrowserUiEvent.LoadUrl(url)) },
                onDeleteFavorite = { id -> onEvent(BrowserUiEvent.RemoveFavorite(id)) },
                onMoveFavoriteToWorkspace = { favId, targetWsId ->
                    onEvent(BrowserUiEvent.MoveFavoriteToWorkspace(favId, targetWsId))
                },
                onBookmarkCurrent = { onEvent(BrowserUiEvent.AddCurrentTabToFavorites) },
                onToggleExpand = { onEvent(BrowserUiEvent.ToggleFavoritesExpanded) },
                onAddClick = { onEvent(BrowserUiEvent.ShowAddFavoriteDialog) },
                modifier = cardModifier,
            )

            val recentTabs = uiState.recentTabs.take(MAX_RECENT_TABS)
            if (recentTabs.isNotEmpty()) {
                Spacer(modifier = Modifier.height(SectionGap + 8.dp))
                RecentTabsSection(
                    recentTabs = recentTabs,
                    isDark = isDark,
                    onOpenRecent = { item -> onEvent(BrowserUiEvent.OpenRecentTab(item)) },
                    onMoreClick = {
                        onEvent(BrowserUiEvent.OpenSearch(prefilledUrl = null, openInNewTab = true))
                    },
                )
            }

            // The rating prompt waits until the tour is done so a fresh Home stays calm.
            if (settings.isTutorialCompleted && uiState.isFeedbackCardVisible) {
                Spacer(modifier = Modifier.height(SectionGap))
                FeedbackPromptCard(
                    isDark = isDark,
                    onRateClick = { onEvent(BrowserUiEvent.OpenFeedbackSheet) },
                    onSnoozeClick = { onEvent(BrowserUiEvent.SnoozeFeedbackPrompt()) },
                    onDismissClick = { onEvent(BrowserUiEvent.DismissFeedbackPrompt) },
                    modifier = cardModifier,
                )
            }
        }
    }
}

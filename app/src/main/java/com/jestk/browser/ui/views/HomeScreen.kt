package com.jestk.browser.ui.views

import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jestk.browser.R
import com.jestk.browser.domain.model.Workspace
import com.jestk.browser.ui.components.workspaceDisplayName
import com.jestk.browser.ui.theme.DarkOnSurface
import com.jestk.browser.ui.theme.DarkSurface
import com.jestk.browser.ui.theme.LocalActiveWorkspaceColor
import com.jestk.browser.ui.theme.LocalIsDarkTheme
import com.jestk.browser.ui.theme.LocalProstoTypography
import com.jestk.browser.ui.theme.ProstoBorders
import com.jestk.browser.ui.theme.ProstoDarkText
import com.jestk.browser.ui.theme.ProstoMutedSlate
import com.jestk.browser.ui.theme.prostoSkyBackground
import com.jestk.browser.ui.viewmodel.BrowserUiEvent
import com.jestk.browser.ui.viewmodel.BrowserUiState
import com.jestk.browser.util.DefaultBrowserHelper
import kotlinx.coroutines.launch

/**
 * Home Screen - Prosto Canvas design system.
 * Features:
 * - Ambient multi-point mint gradient background
 * - Hero search capsule with search icon, mic, and QR scanner
 * - 4-column squircle favorites grid with mint gradients
 * - 136x184dp snap recent tabs carousel with header preview
 */
@Composable
fun HomeScreen(
    uiState: BrowserUiState,
    onEvent: (BrowserUiEvent) -> Unit,
    modifier: Modifier = Modifier,
    isPreview: Boolean = false,
) {
    val context = LocalContext.current
    val speechLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.StartActivityForResult(),
        ) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                val spokenText = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
                if (!spokenText.isNullOrBlank()) {
                    onEvent(BrowserUiEvent.SubmitSearch(spokenText))
                }
            }
        }

    val defaultBrowserLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.StartActivityForResult(),
        ) {
            onEvent(BrowserUiEvent.UpdateDefaultBrowserStatus(DefaultBrowserHelper.isDefaultBrowser(context)))
        }

    val scrollState = rememberScrollState()
    val isDark = LocalIsDarkTheme.current
    val activeWorkspace = uiState.activeWorkspace
    val workspaceColor = activeWorkspace?.let { Color(it.color) } ?: LocalActiveWorkspaceColor.current
    var isDefaultBrowserDismissed by rememberSaveable { mutableStateOf(false) }

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
            // Active Workspace Title / Hero Greeting
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
                        .padding(horizontal = 20.dp, vertical = 6.dp),
            ) {
                Text(
                    text = workspaceTitle,
                    style = LocalProstoTypography.current.headlineXl,
                    color = titleColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 1. Hero Search Capsule
            HeroSearchCapsule(
                searchEngineName = uiState.settings.searchEngine.displayName,
                isDark = isDark,
                onClick = {
                    onEvent(BrowserUiEvent.OpenSearch(prefilledUrl = null, openInNewTab = false))
                },
                onVoiceClick = {
                    try {
                        val intent =
                            Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_WEB_SEARCH)
                            }
                        speechLauncher.launch(intent)
                    } catch (_: Exception) {
                        Toast.makeText(context, R.string.toast_voice_input_unavailable, Toast.LENGTH_SHORT).show()
                    }
                },
                onQrScanClick = {
                    Toast.makeText(context, R.string.toast_qr_scanner_coming_soon, Toast.LENGTH_SHORT).show()
                },
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Privacy & Protection Card
            PrivacyProtectionCard(
                blockedAdsCount = uiState.blockedAdsCount,
                blockedTrackersCount = uiState.blockedTrackersCount,
                isDark = isDark,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
            )

            // Default Browser Recommendation Card
            if (!uiState.isDefaultBrowser && !isDefaultBrowserDismissed) {
                Spacer(modifier = Modifier.height(20.dp))
                DefaultBrowserPromptCard(
                    isDark = isDark,
                    onSetDefaultClick = {
                        DefaultBrowserHelper.openDefaultBrowserSettings(context, defaultBrowserLauncher)
                    },
                    onDismissClick = { isDefaultBrowserDismissed = true },
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp),
                )
            }

            // Feedback Prompt Card
            if (uiState.isFeedbackCardVisible) {
                Spacer(modifier = Modifier.height(20.dp))
                FeedbackPromptCard(
                    isDark = isDark,
                    onRateClick = { onEvent(BrowserUiEvent.OpenFeedbackSheet) },
                    onSnoozeClick = { onEvent(BrowserUiEvent.SnoozeFeedbackPrompt()) },
                    onDismissClick = { onEvent(BrowserUiEvent.DismissFeedbackPrompt) },
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp),
                )
            }

            Spacer(modifier = Modifier.height(28.dp))

            // 2. Favorites Section ("Favorites")
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
                onAddClick = {
                    onEvent(BrowserUiEvent.ShowAddFavoriteDialog)
                },
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
            )

            Spacer(modifier = Modifier.height(32.dp))

            // 3. Recent Tabs Section ("Recent Tabs")
            val recentTabs = uiState.recentTabs.take(8)
            if (recentTabs.isNotEmpty()) {
                RecentTabsSection(
                    recentTabs = recentTabs,
                    isDark = isDark,
                    onOpenRecent = { item -> onEvent(BrowserUiEvent.OpenRecentTab(item)) },
                    onMoreClick = {
                        onEvent(BrowserUiEvent.OpenSearch(prefilledUrl = null, openInNewTab = true))
                    },
                )
            }
        }
    }
}

@Composable
private fun HeroSearchCapsule(
    searchEngineName: String,
    isDark: Boolean,
    onClick: () -> Unit,
    onVoiceClick: () -> Unit,
    onQrScanClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val bg = if (isDark) DarkSurface else Color.White
    val border = ProstoBorders.cardBorder(isDark)

    Surface(
        shape = CircleShape,
        color = bg,
        border = border,
        shadowElevation = 4.dp,
        modifier =
            modifier
                .height(52.dp)
                .clickable(onClick = onClick),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // Search Glyph
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = null,
                tint = ProstoMutedSlate,
                modifier = Modifier.size(20.dp),
            )

            // Placeholder Text
            Text(
                text = stringResource(R.string.home_search_hint, searchEngineName),
                style = LocalProstoTypography.current.bodyMd,
                color = ProstoMutedSlate,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )

            // Trailing Actions (Voice & Scanner)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                IconButton(
                    onClick = onVoiceClick,
                    modifier = Modifier.size(36.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.Mic,
                        contentDescription = stringResource(R.string.a11y_voice_search),
                        tint = ProstoMutedSlate,
                        modifier = Modifier.size(20.dp),
                    )
                }

                IconButton(
                    onClick = onQrScanClick,
                    modifier = Modifier.size(36.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.QrCodeScanner,
                        contentDescription = stringResource(R.string.a11y_scan_qr),
                        tint = ProstoMutedSlate,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
    }
}

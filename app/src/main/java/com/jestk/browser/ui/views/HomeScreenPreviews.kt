package com.jestk.browser.ui.views

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.jestk.browser.domain.model.FavoriteItem
import com.jestk.browser.domain.model.RecentTabItem
import com.jestk.browser.domain.model.ThemeMode
import com.jestk.browser.ui.theme.JestkBrowserTheme
import com.jestk.browser.ui.theme.prostoSkyBackground
import com.jestk.browser.ui.viewmodel.BrowserUiState

@Preview(name = "RecentTabsSection Light", showBackground = true, widthDp = 412, heightDp = 260)
@Composable
fun RecentTabsSectionLightPreview() {
    JestkBrowserTheme(themeMode = ThemeMode.LIGHT) {
        Box(modifier = Modifier.fillMaxWidth().height(260.dp)) {
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .prostoSkyBackground(isDark = false),
            )
            Box(modifier = Modifier.fillMaxSize().padding(vertical = 16.dp)) {
                RecentTabsSection(
                    recentTabs =
                        listOf(
                            RecentTabItem(title = "Kotlin Programming", url = "https://kotlinlang.org"),
                            RecentTabItem(title = "Android Jetpack Compose", url = "https://developer.android.com/compose"),
                            RecentTabItem(title = "GitHub", url = "https://github.com"),
                        ),
                    isDark = false,
                    onOpenRecent = {},
                    onMoreClick = {},
                )
            }
        }
    }
}

@Preview(name = "RecentTabsSection Dark", showBackground = true, widthDp = 412, heightDp = 260)
@Composable
fun RecentTabsSectionDarkPreview() {
    JestkBrowserTheme(themeMode = ThemeMode.DARK) {
        Box(modifier = Modifier.fillMaxWidth().height(260.dp)) {
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .prostoSkyBackground(isDark = true),
            )
            Box(modifier = Modifier.fillMaxSize().padding(vertical = 16.dp)) {
                RecentTabsSection(
                    recentTabs =
                        listOf(
                            RecentTabItem(title = "Kotlin Programming", url = "https://kotlinlang.org"),
                            RecentTabItem(title = "Android Jetpack Compose", url = "https://developer.android.com/compose"),
                            RecentTabItem(title = "GitHub", url = "https://github.com"),
                        ),
                    isDark = true,
                    onOpenRecent = {},
                    onMoreClick = {},
                )
            }
        }
    }
}

@Preview(name = "HomeScreen Light", showBackground = true, widthDp = 412, heightDp = 915)
@Composable
fun HomeScreenLightPreview() {
    JestkBrowserTheme(themeMode = ThemeMode.LIGHT) {
        HomeScreen(
            uiState =
                BrowserUiState(
                    blockedAdsCount = 14L,
                    blockedTrackersCount = 1200L,
                    favorites =
                        listOf(
                            FavoriteItem(1, "Google", "https://google.com"),
                            FavoriteItem(2, "GitHub", "https://github.com"),
                            FavoriteItem(3, "Reddit", "https://reddit.com"),
                            FavoriteItem(4, "Wikipedia", "https://wikipedia.org"),
                        ),
                    recentTabs =
                        listOf(
                            RecentTabItem(title = "Kotlin Language", url = "https://kotlinlang.org"),
                            RecentTabItem(title = "Jetpack Compose", url = "https://developer.android.com/compose"),
                            RecentTabItem(title = "Android Documentation", url = "https://developer.android.com"),
                        ),
                ),
            onEvent = {},
        )
    }
}

@Preview(name = "HomeScreen Dark", showBackground = true, widthDp = 412, heightDp = 915)
@Composable
fun HomeScreenDarkPreview() {
    JestkBrowserTheme(themeMode = ThemeMode.DARK) {
        HomeScreen(
            uiState =
                BrowserUiState(
                    blockedAdsCount = 42L,
                    blockedTrackersCount = 380L,
                    favorites =
                        listOf(
                            FavoriteItem(1, "Google", "https://google.com"),
                            FavoriteItem(2, "GitHub", "https://github.com"),
                            FavoriteItem(3, "Reddit", "https://reddit.com"),
                            FavoriteItem(4, "Wikipedia", "https://wikipedia.org"),
                        ),
                    recentTabs =
                        listOf(
                            RecentTabItem(title = "Kotlin Language", url = "https://kotlinlang.org"),
                            RecentTabItem(title = "Jetpack Compose", url = "https://developer.android.com/compose"),
                            RecentTabItem(title = "Android Documentation", url = "https://developer.android.com"),
                        ),
                ),
            onEvent = {},
        )
    }
}

@Preview(name = "FeedbackPromptCard Light", showBackground = true)
@Composable
fun FeedbackPromptCardLightPreview() {
    JestkBrowserTheme(themeMode = ThemeMode.LIGHT) {
        Box(modifier = Modifier.padding(16.dp)) {
            FeedbackPromptCard(
                isDark = false,
                onRateClick = {},
                onSnoozeClick = {},
                onDismissClick = {},
            )
        }
    }
}

@Preview(name = "FeedbackPromptCard Dark", showBackground = true)
@Composable
fun FeedbackPromptCardDarkPreview() {
    JestkBrowserTheme(themeMode = ThemeMode.DARK) {
        Box(modifier = Modifier.padding(16.dp)) {
            FeedbackPromptCard(
                isDark = true,
                onRateClick = {},
                onSnoozeClick = {},
                onDismissClick = {},
            )
        }
    }
}

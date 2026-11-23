package com.jestk.browser.ui.overlays

import androidx.compose.runtime.Composable

@androidx.compose.ui.tooling.preview.Preview(name = "TabOverviewGrid Light", showBackground = true, widthDp = 412, heightDp = 800)
@Composable
fun TabOverviewGridLightPreview() {
    com.jestk.browser.ui.theme.JestkBrowserTheme(themeMode = com.jestk.browser.domain.model.ThemeMode.LIGHT) {
        TabOverviewGrid(
            uiState =
                com.jestk.browser.ui.viewmodel.BrowserUiState(
                    tabs =
                        listOf(
                            com.jestk.browser.domain.model.Tab(
                                id = "1",
                                url = "https://kotlinlang.org",
                                title = "Kotlin Programming",
                                isActive = true,
                            ),
                            com.jestk.browser.domain.model.Tab(
                                id = "2",
                                url = "https://developer.android.com",
                                title = "Android Developers",
                                isActive = false,
                            ),
                            com.jestk.browser.domain.model.Tab(
                                id = "3",
                                url = "https://github.com",
                                title = "GitHub",
                                isActive = false,
                                folderId = "f1",
                            ),
                            com.jestk.browser.domain.model.Tab(
                                id = "4",
                                url = "https://duckduckgo.com",
                                title = "DuckDuckGo",
                                isActive = false,
                                folderId = "f1",
                            ),
                        ),
                    folders =
                        listOf(
                            com.jestk.browser.domain.model.TabFolder(
                                id = "f1",
                                title = "Dev",
                                color = 0xFF8B5CF6.toInt(),
                            ),
                        ),
                    activeTabId = "1",
                ),
            onEvent = {},
        )
    }
}

@androidx.compose.ui.tooling.preview.Preview(name = "TabOverviewGrid Dark", showBackground = true, widthDp = 412, heightDp = 800)
@Composable
fun TabOverviewGridDarkPreview() {
    com.jestk.browser.ui.theme.JestkBrowserTheme(themeMode = com.jestk.browser.domain.model.ThemeMode.DARK) {
        TabOverviewGrid(
            uiState =
                com.jestk.browser.ui.viewmodel.BrowserUiState(
                    tabs =
                        listOf(
                            com.jestk.browser.domain.model.Tab(
                                id = "1",
                                url = "https://kotlinlang.org",
                                title = "Kotlin Programming",
                                isActive = true,
                            ),
                            com.jestk.browser.domain.model.Tab(
                                id = "2",
                                url = "https://developer.android.com",
                                title = "Android Developers",
                                isActive = false,
                            ),
                            com.jestk.browser.domain.model.Tab(
                                id = "3",
                                url = "https://github.com",
                                title = "GitHub",
                                isActive = false,
                                folderId = "f1",
                            ),
                            com.jestk.browser.domain.model.Tab(
                                id = "4",
                                url = "https://duckduckgo.com",
                                title = "DuckDuckGo",
                                isActive = false,
                                folderId = "f1",
                            ),
                        ),
                    folders =
                        listOf(
                            com.jestk.browser.domain.model.TabFolder(
                                id = "f1",
                                title = "Dev",
                                color = 0xFF8B5CF6.toInt(),
                            ),
                        ),
                    activeTabId = "1",
                ),
            onEvent = {},
        )
    }
}

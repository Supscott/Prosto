package com.jestk.browser.domain.model

data class Tab @JvmOverloads constructor(
    val id: String,
    val url: String = "",
    val title: String = "",
    val lastActiveTimestamp: Long = System.currentTimeMillis(),
    val snapshotPath: String? = null,
    val position: Int = 0,
    val faviconUrl: String? = null,
    val isIncognito: Boolean = false,
    val isActive: Boolean = false,
    val canGoBack: Boolean = false,
    val canGoForward: Boolean = false,
    val themeColor: Int? = null,
    val bottomThemeColor: Int? = null,
    val folderId: String? = null,
    val workspaceId: String = Workspace.DEFAULT_PERSONAL_ID,
) {
    val topThemeColor: Int? get() = themeColor
    /** A home tab is identified by its blank/about:blank URL. Page titles are user-visible content and never identify a tab type. */
    val isHomeTab: Boolean get() = url.isBlank() || url == "about:blank"
}

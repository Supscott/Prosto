package com.jestk.browser.domain.model

data class Tab @JvmOverloads constructor(
    val id: String,
    val url: String = "",
    val title: String = "New Tab",
    val lastActiveTimestamp: Long = System.currentTimeMillis(),
    val snapshotPath: String? = null,
    val position: Int = 0,
    val faviconUrl: String? = null,
    val isIncognito: Boolean = false,
    val isActive: Boolean = false,
    val canGoBack: Boolean = false,
    val canGoForward: Boolean = false
)

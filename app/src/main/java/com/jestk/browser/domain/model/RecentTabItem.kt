package com.jestk.browser.domain.model

data class RecentTabItem(
    val url: String,
    val title: String = "",
    val snapshotPath: String? = null,
    val faviconUrl: String? = null,
    val lastVisitedTimestamp: Long = System.currentTimeMillis()
)

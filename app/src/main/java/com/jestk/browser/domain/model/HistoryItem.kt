package com.jestk.browser.domain.model

data class HistoryItem @JvmOverloads constructor(
    val id: Long = 0,
    val url: String = "",
    val title: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val visitCount: Int = 1,
    val faviconUrl: String? = null
)

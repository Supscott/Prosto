package com.jestk.browser.domain.model

data class FavoriteItem @JvmOverloads constructor(
    val id: Long = 0,
    val title: String = "",
    val url: String = "",
    val position: Int = 0,
    val iconUrl: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)

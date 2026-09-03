package com.jestk.browser.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "tabs",
    indices = [
        Index(value = ["position"]),
        Index(value = ["lastActiveTimestamp"])
    ]
)
data class TabEntity(
    @PrimaryKey
    val id: String,
    val url: String = "",
    val title: String = "New Tab",
    val snapshotPath: String? = null,
    val faviconUrl: String? = null,
    val lastActiveTimestamp: Long = System.currentTimeMillis(),
    val position: Int = 0,
    val isIncognito: Boolean = false,
    val isActive: Boolean = false,
    val canGoBack: Boolean = false,
    val canGoForward: Boolean = false
)

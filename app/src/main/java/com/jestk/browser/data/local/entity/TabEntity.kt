package com.jestk.browser.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "tabs",
    indices = [
        Index(value = ["position"]),
        Index(value = ["lastActiveTimestamp"]),
        Index(value = ["folderId"]),
        Index(value = ["workspaceId"])
    ]
)
data class TabEntity(
    @PrimaryKey
    val id: String,
    val url: String = "",
    val title: String = "",
    val snapshotPath: String? = null,
    val faviconUrl: String? = null,
    val themeColor: Int? = null,
    val lastActiveTimestamp: Long = System.currentTimeMillis(),
    val position: Int = 0,
    val isIncognito: Boolean = false,
    val isActive: Boolean = false,
    val canGoBack: Boolean = false,
    val canGoForward: Boolean = false,
    val folderId: String? = null,
    @ColumnInfo(defaultValue = "'default_personal'")
    val workspaceId: String = "default_personal"
)

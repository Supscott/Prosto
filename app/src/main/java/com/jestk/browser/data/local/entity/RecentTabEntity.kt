package com.jestk.browser.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "recent_tabs",
    indices = [
        Index(value = ["lastVisitedTimestamp"])
    ]
)
data class RecentTabEntity(
    @PrimaryKey
    val url: String,
    val title: String,
    val snapshotPath: String? = null,
    val faviconUrl: String? = null,
    val lastVisitedTimestamp: Long = System.currentTimeMillis()
)

package com.jestk.browser.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import com.jestk.browser.domain.model.Workspace

@Entity(
    tableName = "recent_tabs",
    primaryKeys = ["url", "workspaceId"],
    indices = [
        Index(value = ["lastVisitedTimestamp"]),
        Index(value = ["workspaceId", "lastVisitedTimestamp"])
    ]
)
data class RecentTabEntity(
    val url: String,
    val title: String,
    val snapshotPath: String? = null,
    val faviconUrl: String? = null,
    val lastVisitedTimestamp: Long = System.currentTimeMillis(),
    @ColumnInfo(defaultValue = "'default_personal'")
    val workspaceId: String = Workspace.DEFAULT_PERSONAL_ID
)

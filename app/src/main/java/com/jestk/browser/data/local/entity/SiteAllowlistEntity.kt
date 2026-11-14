package com.jestk.browser.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index

/** [workspaceScope] is a workspace id, or [ALL_WORKSPACES] (primary key parts cannot be null). */
@Entity(
    tableName = "site_allowlist",
    primaryKeys = ["host", "workspace_scope"],
    indices = [Index(value = ["workspace_scope"])],
)
data class SiteAllowlistEntity(
    val host: String,
    @ColumnInfo(name = "workspace_scope")
    val workspaceScope: String,
    val createdAt: Long,
) {
    companion object {
        const val ALL_WORKSPACES = "*"
    }
}

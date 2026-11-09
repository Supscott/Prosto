package com.jestk.browser.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "tab_folders",
    indices = [
        Index(value = ["position"]),
        Index(value = ["createdAt"]),
        Index(value = ["workspaceId"])
    ]
)
data class TabFolderEntity(
    @PrimaryKey
    val id: String,
    val title: String = "",
    val color: Int,
    val isCollapsed: Boolean = false,
    val position: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(defaultValue = "'default_personal'")
    val workspaceId: String = "default_personal"
)

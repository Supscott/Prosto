package com.jestk.browser.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "tab_folders",
    indices = [
        Index(value = ["position"]),
        Index(value = ["createdAt"])
    ]
)
data class TabFolderEntity(
    @PrimaryKey
    val id: String,
    val title: String = "",
    val color: Int,
    val isCollapsed: Boolean = false,
    val position: Int = 0,
    val createdAt: Long = System.currentTimeMillis()
)

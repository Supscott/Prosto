package com.jestk.browser.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "workspaces",
    indices = [
        Index(value = ["position"]),
        Index(value = ["createdAt"])
    ]
)
data class WorkspaceEntity(
    @PrimaryKey
    val id: String,
    val name: String,
    val color: Int,
    val position: Int = 0,
    val profileName: String = id,
    val createdAt: Long = System.currentTimeMillis()
)

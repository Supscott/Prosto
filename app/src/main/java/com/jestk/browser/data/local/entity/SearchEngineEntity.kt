package com.jestk.browser.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "search_engines",
    indices = [
        Index(value = ["orderPosition"]),
        Index(value = ["shortcut"])
    ]
)
data class SearchEngineEntity(
    @PrimaryKey
    val id: String,
    val displayName: String,
    val searchUrlTemplate: String,
    val iconUrl: String,
    val shortcut: String? = null,
    val orderPosition: Int = 0,
    val isBuiltIn: Boolean = false
)

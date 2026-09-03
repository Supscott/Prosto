package com.jestk.browser.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "favorites",
    indices = [
        Index(value = ["position"]),
        Index(value = ["url"])
    ]
)
data class FavoriteEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val url: String,
    val title: String,
    val position: Int = 0,
    val iconUrl: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)

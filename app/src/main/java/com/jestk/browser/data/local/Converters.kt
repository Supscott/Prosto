package com.jestk.browser.data.local

import android.net.Uri
import androidx.room.TypeConverter
import java.util.Date

class Converters {
    @TypeConverter
    fun fromTimestamp(value: Long?): Date? {
        return value?.let { Date(it) }
    }

    @TypeConverter
    fun dateToTimestamp(date: Date?): Long? {
        return date?.time
    }

    @TypeConverter
    fun fromUriString(value: String?): Uri? {
        return value?.let { Uri.parse(it) }
    }

    @TypeConverter
    fun uriToString(uri: Uri?): String? {
        return uri?.toString()
    }

    @TypeConverter
    fun fromStringList(value: String?): List<String>? {
        if (value.isNullOrEmpty()) return emptyList()
        return value.split("|||")
    }

    @TypeConverter
    fun stringListToString(list: List<String>?): String? {
        if (list.isNullOrEmpty()) return null
        return list.joinToString("|||")
    }
}

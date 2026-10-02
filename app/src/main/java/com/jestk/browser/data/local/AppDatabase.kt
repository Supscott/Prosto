package com.jestk.browser.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.jestk.browser.data.local.dao.DownloadDao
import com.jestk.browser.data.local.dao.FavoriteDao
import com.jestk.browser.data.local.dao.HistoryDao
import com.jestk.browser.data.local.dao.SettingDao
import com.jestk.browser.data.local.dao.TabDao
import com.jestk.browser.data.local.entity.DownloadEntity
import com.jestk.browser.data.local.entity.FavoriteEntity
import com.jestk.browser.data.local.entity.HistoryEntity
import com.jestk.browser.data.local.entity.SettingEntity
import com.jestk.browser.data.local.dao.RecentTabDao
import com.jestk.browser.data.local.entity.RecentTabEntity
import com.jestk.browser.data.local.entity.TabEntity

import com.jestk.browser.data.local.dao.SearchEngineDao
import com.jestk.browser.data.local.entity.SearchEngineEntity

@Database(
    entities = [
        TabEntity::class,
        HistoryEntity::class,
        FavoriteEntity::class,
        SettingEntity::class,
        RecentTabEntity::class,
        DownloadEntity::class,
        SearchEngineEntity::class
    ],
    version = 7,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun tabDao(): TabDao
    abstract fun historyDao(): HistoryDao
    abstract fun favoriteDao(): FavoriteDao
    abstract fun settingDao(): SettingDao
    abstract fun recentTabDao(): RecentTabDao
    abstract fun downloadDao(): DownloadDao
    abstract fun searchEngineDao(): SearchEngineDao

    companion object {
        private const val DATABASE_NAME = "jestk_browser.db"

        @Volatile
        private var instance: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    DATABASE_NAME
                )
                .fallbackToDestructiveMigration()
                .build()
                .also { instance = it }
            }
        }

        fun createInMemory(context: Context): AppDatabase {
            return Room.inMemoryDatabaseBuilder(
                context.applicationContext,
                AppDatabase::class.java
            )
            .allowMainThreadQueries()
            .build()
        }
    }
}

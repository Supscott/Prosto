package com.jestk.browser.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
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
import com.jestk.browser.data.local.dao.SearchEngineDao
import com.jestk.browser.data.local.dao.TabFolderDao
import com.jestk.browser.data.local.dao.WorkspaceDao
import com.jestk.browser.data.local.entity.RecentTabEntity
import com.jestk.browser.data.local.entity.SearchEngineEntity
import com.jestk.browser.data.local.entity.TabEntity
import com.jestk.browser.data.local.entity.TabFolderEntity
import com.jestk.browser.data.local.entity.WorkspaceEntity
import com.jestk.browser.domain.model.Workspace

@Database(
    entities = [
        TabEntity::class,
        HistoryEntity::class,
        FavoriteEntity::class,
        SettingEntity::class,
        RecentTabEntity::class,
        DownloadEntity::class,
        SearchEngineEntity::class,
        TabFolderEntity::class,
        WorkspaceEntity::class
    ],
    version = 10,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun tabDao(): TabDao
    abstract fun tabFolderDao(): TabFolderDao
    abstract fun historyDao(): HistoryDao
    abstract fun favoriteDao(): FavoriteDao
    abstract fun settingDao(): SettingDao
    abstract fun recentTabDao(): RecentTabDao
    abstract fun downloadDao(): DownloadDao
    abstract fun searchEngineDao(): SearchEngineDao
    abstract fun workspaceDao(): WorkspaceDao

    companion object {
        private const val DATABASE_NAME = "jestk_browser.db"

        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_history_url_timestamp` ON `history` (`url`, `timestamp`)")
            }
        }

        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `tab_folders` (
                        `id` TEXT NOT NULL,
                        `title` TEXT NOT NULL,
                        `color` INTEGER NOT NULL,
                        `isCollapsed` INTEGER NOT NULL,
                        `position` INTEGER NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_tab_folders_position` ON `tab_folders` (`position`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_tab_folders_createdAt` ON `tab_folders` (`createdAt`)")
                db.execSQL("ALTER TABLE `tabs` ADD COLUMN `folderId` TEXT DEFAULT NULL")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_tabs_folderId` ON `tabs` (`folderId`)")
            }
        }

        val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `workspaces` (
                        `id` TEXT NOT NULL,
                        `name` TEXT NOT NULL,
                        `color` INTEGER NOT NULL,
                        `position` INTEGER NOT NULL,
                        `profileName` TEXT NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_workspaces_position` ON `workspaces` (`position`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_workspaces_createdAt` ON `workspaces` (`createdAt`)")
                db.execSQL("ALTER TABLE `tabs` ADD COLUMN `workspaceId` TEXT NOT NULL DEFAULT 'default_personal'")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_tabs_workspaceId` ON `tabs` (`workspaceId`)")
                db.execSQL("ALTER TABLE `tab_folders` ADD COLUMN `workspaceId` TEXT NOT NULL DEFAULT 'default_personal'")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_tab_folders_workspaceId` ON `tab_folders` (`workspaceId`)")
                db.execSQL("ALTER TABLE `history` ADD COLUMN `workspaceId` TEXT NOT NULL DEFAULT 'default_personal'")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_history_workspaceId` ON `history` (`workspaceId`)")
                seedDefaultWorkspaces(db)
            }
        }

        fun seedDefaultWorkspaces(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                INSERT OR IGNORE INTO `workspaces` (`id`, `name`, `color`, `position`, `profileName`, `createdAt`)
                VALUES ('${Workspace.DEFAULT_PERSONAL_ID}', 'Personal', ${Workspace.DEFAULT_PERSONAL.color}, 0, '${Workspace.DEFAULT_PERSONAL_ID}', 0)
                """.trimIndent()
            )
            db.execSQL(
                """
                INSERT OR IGNORE INTO `workspaces` (`id`, `name`, `color`, `position`, `profileName`, `createdAt`)
                VALUES ('${Workspace.DEFAULT_WORK_ID}', 'Work', ${Workspace.DEFAULT_WORK.color}, 1, '${Workspace.DEFAULT_WORK_ID}', 0)
                """.trimIndent()
            )
        }

        @Volatile
        private var instance: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    DATABASE_NAME
                )
                .addMigrations(MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10)
                .addCallback(object : RoomDatabase.Callback() {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        super.onCreate(db)
                        seedDefaultWorkspaces(db)
                    }
                })
                .fallbackToDestructiveMigrationOnDowngrade()
                .build()
                .also { instance = it }
            }
        }

        fun createInMemory(context: Context): AppDatabase {
            return Room.inMemoryDatabaseBuilder(
                context.applicationContext,
                AppDatabase::class.java
            )
            .addCallback(object : RoomDatabase.Callback() {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    super.onCreate(db)
                    seedDefaultWorkspaces(db)
                }
            })
            .allowMainThreadQueries()
            .build()
        }
    }
}

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
    version = 13,
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

        val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `favorites` ADD COLUMN `workspaceId` TEXT NOT NULL DEFAULT 'default_personal'")
                db.execSQL("DROP INDEX IF EXISTS `index_favorites_position`")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_favorites_workspaceId_position` ON `favorites` (`workspaceId`, `position`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_favorites_workspaceId_url` ON `favorites` (`workspaceId`, `url`)")
            }
        }

        val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `recent_tabs` ADD COLUMN `workspaceId` TEXT NOT NULL DEFAULT 'default_personal'")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_recent_tabs_workspaceId_lastVisitedTimestamp` ON `recent_tabs` (`workspaceId`, `lastVisitedTimestamp`)")
            }
        }

        /**
         * DATA-2: the same URL may be a recent tab in several workspaces.
         * DATA-6: English placeholder names and titles become blank; the UI shows localized labels.
         */
        val MIGRATION_12_13 = object : Migration(12, 13) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `recent_tabs_new` (
                        `url` TEXT NOT NULL,
                        `title` TEXT NOT NULL,
                        `snapshotPath` TEXT,
                        `faviconUrl` TEXT,
                        `lastVisitedTimestamp` INTEGER NOT NULL,
                        `workspaceId` TEXT NOT NULL DEFAULT 'default_personal',
                        PRIMARY KEY(`url`, `workspaceId`)
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    INSERT INTO `recent_tabs_new` (`url`, `title`, `snapshotPath`, `faviconUrl`, `lastVisitedTimestamp`, `workspaceId`)
                    SELECT `url`, `title`, `snapshotPath`, `faviconUrl`, `lastVisitedTimestamp`, `workspaceId` FROM `recent_tabs`
                    """.trimIndent()
                )
                db.execSQL("DROP TABLE `recent_tabs`")
                db.execSQL("ALTER TABLE `recent_tabs_new` RENAME TO `recent_tabs`")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_recent_tabs_lastVisitedTimestamp` ON `recent_tabs` (`lastVisitedTimestamp`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_recent_tabs_workspaceId_lastVisitedTimestamp` ON `recent_tabs` (`workspaceId`, `lastVisitedTimestamp`)")

                db.execSQL("UPDATE `workspaces` SET `name` = '' WHERE `id` = '${Workspace.DEFAULT_PERSONAL_ID}' AND `name` = 'Personal'")
                db.execSQL("UPDATE `workspaces` SET `name` = '' WHERE `id` = '${Workspace.DEFAULT_WORK_ID}' AND `name` = 'Work'")
                db.execSQL("UPDATE `tabs` SET `title` = '' WHERE `title` IN ('Home', 'New Tab', 'Loading...') AND (`url` = '' OR `url` = 'about:blank' OR `title` <> 'Home')")
            }
        }

        fun seedDefaultWorkspaces(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                INSERT OR IGNORE INTO `workspaces` (`id`, `name`, `color`, `position`, `profileName`, `createdAt`)
                VALUES ('${Workspace.DEFAULT_PERSONAL_ID}', '', ${Workspace.DEFAULT_PERSONAL.color}, 0, '${Workspace.DEFAULT_PERSONAL_ID}', 0)
                """.trimIndent()
            )
            db.execSQL(
                """
                INSERT OR IGNORE INTO `workspaces` (`id`, `name`, `color`, `position`, `profileName`, `createdAt`)
                VALUES ('${Workspace.DEFAULT_WORK_ID}', '', ${Workspace.DEFAULT_WORK.color}, 1, '${Workspace.DEFAULT_WORK_ID}', 0)
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
                .addMigrations(MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13)
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

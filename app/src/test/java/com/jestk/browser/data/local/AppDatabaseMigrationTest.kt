package com.jestk.browser.data.local

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.jestk.browser.domain.model.Workspace
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@RunWith(RobolectricTestRunner::class)
class AppDatabaseMigrationTest {

    private lateinit var context: Context
    private lateinit var dbFile: File

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        dbFile = context.getDatabasePath("test_migration.db")
        if (dbFile.exists()) {
            dbFile.delete()
        }
    }

    @After
    fun tearDown() {
        if (dbFile.exists()) {
            dbFile.delete()
        }
    }

    private fun createV9Database(): SupportSQLiteDatabase {
        val configuration = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(dbFile.name)
            .callback(object : SupportSQLiteOpenHelper.Callback(9) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    // Create v9 tables
                    db.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS `tabs` (
                            `id` TEXT NOT NULL,
                            `url` TEXT NOT NULL,
                            `title` TEXT NOT NULL,
                            `snapshotPath` TEXT,
                            `faviconUrl` TEXT,
                            `themeColor` INTEGER,
                            `lastActiveTimestamp` INTEGER NOT NULL,
                            `position` INTEGER NOT NULL,
                            `isIncognito` INTEGER NOT NULL,
                            `isActive` INTEGER NOT NULL,
                            `canGoBack` INTEGER NOT NULL,
                            `canGoForward` INTEGER NOT NULL,
                            `folderId` TEXT,
                            PRIMARY KEY(`id`)
                        )
                        """.trimIndent()
                    )
                    db.execSQL("CREATE INDEX IF NOT EXISTS `index_tabs_position` ON `tabs` (`position`)")
                    db.execSQL("CREATE INDEX IF NOT EXISTS `index_tabs_lastActiveTimestamp` ON `tabs` (`lastActiveTimestamp`)")
                    db.execSQL("CREATE INDEX IF NOT EXISTS `index_tabs_folderId` ON `tabs` (`folderId`)")

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

                    db.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS `history` (
                            `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                            `url` TEXT NOT NULL,
                            `title` TEXT NOT NULL,
                            `timestamp` INTEGER NOT NULL,
                            `visitCount` INTEGER NOT NULL,
                            `faviconUrl` TEXT
                        )
                        """.trimIndent()
                    )
                    db.execSQL("CREATE INDEX IF NOT EXISTS `index_history_timestamp` ON `history` (`timestamp`)")
                    db.execSQL("CREATE INDEX IF NOT EXISTS `index_history_url` ON `history` (`url`)")
                    db.execSQL("CREATE INDEX IF NOT EXISTS `index_history_url_timestamp` ON `history` (`url`, `timestamp`)")
                }

                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {}
            })
            .build()

        val helper = FrameworkSQLiteOpenHelperFactory().create(configuration)
        return helper.writableDatabase
    }

    @Test
    fun migration9to10_addsWorkspacesTable_andWorkspaceIdColumns_andSeedsDefaults() {
        val db = createV9Database()

        // Insert pre-migration records without workspaceId
        db.execSQL(
            """
            INSERT INTO `tabs` (`id`, `url`, `title`, `lastActiveTimestamp`, `position`, `isIncognito`, `isActive`, `canGoBack`, `canGoForward`)
            VALUES ('tab-1', 'https://example.com', 'Example', 1000, 0, 0, 1, 0, 0)
            """.trimIndent()
        )
        db.execSQL(
            """
            INSERT INTO `tab_folders` (`id`, `title`, `color`, `isCollapsed`, `position`, `createdAt`)
            VALUES ('folder-1', 'Projects', -14578445, 0, 0, 1000)
            """.trimIndent()
        )
        db.execSQL(
            """
            INSERT INTO `history` (`url`, `title`, `timestamp`, `visitCount`)
            VALUES ('https://example.com', 'Example', 1000, 1)
            """.trimIndent()
        )

        // Execute MIGRATION_9_10
        AppDatabase.MIGRATION_9_10.migrate(db)

        // 1. Verify workspaces table exists and contains default Personal and Work workspaces
        val cursorWorkspaces = db.query("SELECT id, name, color, position, profileName FROM workspaces ORDER BY position ASC, id ASC")
        assertThat(cursorWorkspaces.count).isEqualTo(2)

        cursorWorkspaces.moveToFirst()
        val personalId = cursorWorkspaces.getString(0)
        val personalName = cursorWorkspaces.getString(1)
        val personalColor = cursorWorkspaces.getInt(2)
        val personalPos = cursorWorkspaces.getInt(3)
        val personalProfile = cursorWorkspaces.getString(4)

        assertThat(personalId).isEqualTo(Workspace.DEFAULT_PERSONAL_ID)
        assertThat(personalName).isEqualTo("Personal")
        assertThat(personalColor).isEqualTo(Workspace.DEFAULT_PERSONAL.color)
        assertThat(personalPos).isEqualTo(0)
        assertThat(personalProfile).isEqualTo(Workspace.DEFAULT_PERSONAL_ID)

        cursorWorkspaces.moveToNext()
        val workId = cursorWorkspaces.getString(0)
        val workName = cursorWorkspaces.getString(1)
        val workColor = cursorWorkspaces.getInt(2)
        val workPos = cursorWorkspaces.getInt(3)
        val workProfile = cursorWorkspaces.getString(4)

        assertThat(workId).isEqualTo(Workspace.DEFAULT_WORK_ID)
        assertThat(workName).isEqualTo("Work")
        assertThat(workColor).isEqualTo(Workspace.DEFAULT_WORK.color)
        assertThat(workPos).isEqualTo(1)
        assertThat(workProfile).isEqualTo(Workspace.DEFAULT_WORK_ID)
        cursorWorkspaces.close()

        // 2. Verify existing tab has migrated workspaceId = 'default_personal'
        val cursorTab = db.query("SELECT id, workspaceId FROM tabs WHERE id = 'tab-1'")
        assertThat(cursorTab.moveToFirst()).isTrue()
        assertThat(cursorTab.getString(1)).isEqualTo(Workspace.DEFAULT_PERSONAL_ID)
        cursorTab.close()

        // 3. Verify existing folder has migrated workspaceId = 'default_personal'
        val cursorFolder = db.query("SELECT id, workspaceId FROM tab_folders WHERE id = 'folder-1'")
        assertThat(cursorFolder.moveToFirst()).isTrue()
        assertThat(cursorFolder.getString(1)).isEqualTo(Workspace.DEFAULT_PERSONAL_ID)
        cursorFolder.close()

        // 4. Verify existing history record has migrated workspaceId = 'default_personal'
        val cursorHistory = db.query("SELECT id, workspaceId FROM history WHERE url = 'https://example.com'")
        assertThat(cursorHistory.moveToFirst()).isTrue()
        assertThat(cursorHistory.getString(1)).isEqualTo(Workspace.DEFAULT_PERSONAL_ID)
        cursorHistory.close()

        // 5. Verify insertion of new records with explicit workspaceId works
        db.execSQL(
            """
            INSERT INTO `tabs` (`id`, `url`, `title`, `lastActiveTimestamp`, `position`, `isIncognito`, `isActive`, `canGoBack`, `canGoForward`, `workspaceId`)
            VALUES ('tab-work', 'https://work.com', 'Work Tab', 2000, 1, 0, 0, 0, 0, 'default_work')
            """.trimIndent()
        )
        val cursorNewTab = db.query("SELECT workspaceId FROM tabs WHERE id = 'tab-work'")
        assertThat(cursorNewTab.moveToFirst()).isTrue()
        assertThat(cursorNewTab.getString(0)).isEqualTo("default_work")
        cursorNewTab.close()

        db.close()
    }

    private fun createV10Database(): SupportSQLiteDatabase {
        val configuration = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(dbFile.name)
            .callback(object : SupportSQLiteOpenHelper.Callback(10) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS `favorites` (
                            `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                            `url` TEXT NOT NULL,
                            `title` TEXT NOT NULL,
                            `position` INTEGER NOT NULL,
                            `iconUrl` TEXT,
                            `createdAt` INTEGER NOT NULL
                        )
                        """.trimIndent()
                    )
                    db.execSQL("CREATE INDEX IF NOT EXISTS `index_favorites_position` ON `favorites` (`position`)")
                    db.execSQL("CREATE INDEX IF NOT EXISTS `index_favorites_url` ON `favorites` (`url`)")
                }

                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {}
            })
            .build()

        val helper = FrameworkSQLiteOpenHelperFactory().create(configuration)
        return helper.writableDatabase
    }

    @Test
    fun migration10To11_addsWorkspaceIdToFavorites() {
        val db = createV10Database()
        db.execSQL(
            """
            INSERT INTO `favorites` (`id`, `url`, `title`, `position`, `iconUrl`, `createdAt`)
            VALUES (1, 'https://example.com', 'Example', 0, NULL, 1000)
            """.trimIndent()
        )

        AppDatabase.MIGRATION_10_11.migrate(db)

        val cursor = db.query("SELECT id, url, title, position, workspaceId FROM favorites WHERE id = 1")
        assertThat(cursor.moveToFirst()).isTrue()
        assertThat(cursor.getString(1)).isEqualTo("https://example.com")
        assertThat(cursor.getString(2)).isEqualTo("Example")
        assertThat(cursor.getString(4)).isEqualTo(Workspace.DEFAULT_PERSONAL_ID)
        cursor.close()

        db.execSQL(
            """
            INSERT INTO `favorites` (`url`, `title`, `position`, `iconUrl`, `createdAt`, `workspaceId`)
            VALUES ('https://work-bookmark.com', 'Work Bookmark', 0, NULL, 2000, 'default_work')
            """.trimIndent()
        )
        val cursorWork = db.query("SELECT workspaceId FROM favorites WHERE url = 'https://work-bookmark.com'")
        assertThat(cursorWork.moveToFirst()).isTrue()
        assertThat(cursorWork.getString(0)).isEqualTo("default_work")
        cursorWork.close()

        db.close()
    }
}

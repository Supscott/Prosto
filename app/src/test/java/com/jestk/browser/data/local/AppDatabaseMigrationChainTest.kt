package com.jestk.browser.data.local

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * PRO-137: Room's own [MigrationTestHelper] builds each old database from the exported schema
 * (`app/schemas`, wired as test assets in `build.gradle.kts`) and validates the migrated result
 * against the schema of the target version. `8.json` is the oldest exported schema.
 */
@RunWith(RobolectricTestRunner::class)
class AppDatabaseMigrationChainTest {

    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), AppDatabase::class.java)

    private val oldestVersion = 8

    private fun SupportSQLiteDatabase.count(sql: String): Int =
        query(sql).use { it.moveToFirst(); it.getInt(0) }

    private fun seedVersion8(db: SupportSQLiteDatabase) {
        db.execSQL(
            "INSERT INTO tabs (id, url, title, snapshotPath, faviconUrl, themeColor, lastActiveTimestamp, position, " +
                "isIncognito, isActive, canGoBack, canGoForward) " +
                "VALUES ('tab-1', 'https://tab.example', 'Tab', NULL, NULL, NULL, 2000, 0, 0, 1, 0, 0)"
        )
        db.execSQL(
            "INSERT INTO history (url, title, timestamp, visitCount, faviconUrl) " +
                "VALUES ('https://history.example', 'History', 3000, 2, NULL)"
        )
        db.execSQL(
            "INSERT INTO favorites (url, title, position, iconUrl, createdAt) " +
                "VALUES ('https://favorite.example', 'Favorite', 0, NULL, 4000)"
        )
        db.execSQL("INSERT INTO settings (`key`, value) VALUES ('theme', 'dark')")
        db.execSQL(
            "INSERT INTO recent_tabs (url, title, snapshotPath, faviconUrl, lastVisitedTimestamp) " +
                "VALUES ('https://recent.example', 'Recent', NULL, NULL, 5000)"
        )
        db.execSQL(
            "INSERT INTO downloads (url, fileName, filePath, mimeType, contentLength, timestamp, downloadId, status, " +
                "bytesDownloaded, totalBytes, errorMessage, etag) " +
                "VALUES ('https://download.example/a.zip', 'a.zip', '/files/a.zip', 'application/zip', 10, 6000, 7, " +
                "'COMPLETED', 10, 10, NULL, NULL)"
        )
        db.execSQL(
            "INSERT INTO search_engines (id, displayName, searchUrlTemplate, iconUrl, shortcut, orderPosition, isBuiltIn) " +
                "VALUES ('custom', 'Custom', 'https://search.example/?q=%s', '', 'c', 0, 0)"
        )
    }

    @Test
    fun migrationChain_fromOldestSchemaToLatest_validatesAndKeepsData() {
        val name = "migration_chain.db"
        helper.createDatabase(name, oldestVersion).use { seedVersion8(it) }

        val db = helper.runMigrationsAndValidate(name, AppDatabase.VERSION, true, *AppDatabase.ALL_MIGRATIONS)
        try {
            assertThat(db.version).isEqualTo(AppDatabase.VERSION)
            assertThat(db.count("SELECT COUNT(*) FROM tabs WHERE id = 'tab-1' AND url = 'https://tab.example' AND workspaceId = 'default_personal'")).isEqualTo(1)
            assertThat(db.count("SELECT COUNT(*) FROM history WHERE url = 'https://history.example' AND visitCount = 2 AND workspaceId = 'default_personal'")).isEqualTo(1)
            assertThat(db.count("SELECT COUNT(*) FROM favorites WHERE url = 'https://favorite.example' AND workspaceId = 'default_personal'")).isEqualTo(1)
            assertThat(db.count("SELECT COUNT(*) FROM settings WHERE `key` = 'theme' AND value = 'dark'")).isEqualTo(1)
            assertThat(db.count("SELECT COUNT(*) FROM downloads WHERE fileName = 'a.zip' AND status = 'COMPLETED'")).isEqualTo(1)
            assertThat(db.count("SELECT COUNT(*) FROM search_engines WHERE id = 'custom' AND shortcut = 'c'")).isEqualTo(1)
            // Tables added along the chain exist; the table dropped at 14 -> 15 is gone.
            assertThat(db.count("SELECT COUNT(*) FROM workspaces")).isEqualTo(2)
            assertThat(db.count("SELECT COUNT(*) FROM tab_folders")).isEqualTo(0)
            assertThat(db.count("SELECT COUNT(*) FROM site_allowlist")).isEqualTo(0)
            assertThat(db.count("SELECT COUNT(*) FROM sqlite_master WHERE name = 'recent_tabs'")).isEqualTo(0)
        } finally {
            db.close()
        }
    }

    @Test
    fun everyStep_fromEachExportedVersion_validatesAgainstTargetSchema() {
        val steps = AppDatabase.ALL_MIGRATIONS.filter { it.startVersion >= oldestVersion }
        assertThat(steps).isNotEmpty()
        assertThat(steps.last().endVersion).isEqualTo(AppDatabase.VERSION)
        for (step in steps) {
            val name = "migration_step_${step.startVersion}_${step.endVersion}.db"
            helper.createDatabase(name, step.startVersion).close()
            helper.runMigrationsAndValidate(name, step.endVersion, true, step).close()
        }
    }
}

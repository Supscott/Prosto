package com.jestk.browser.data.local

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import org.json.JSONObject
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

/**
 * PRO-116: version 15 drops `recent_tabs`. The version 14 database is built from the exported
 * `14.json` schema, then opened through Room so that its schema validation runs after the migration.
 */
@RunWith(RobolectricTestRunner::class)
class AppDatabaseMigration14To15Test {

    private lateinit var context: Context
    private lateinit var dbFile: File

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        dbFile = context.getDatabasePath("test_migration_14_15.db")
        dbFile.delete()
    }

    @After
    fun tearDown() {
        dbFile.delete()
    }

    private fun createV14Database(): SupportSQLiteDatabase {
        val schema = JSONObject(File("schemas/com.jestk.browser.data.local.AppDatabase/14.json").readText())
            .getJSONObject("database")
        val configuration = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(dbFile.name)
            .callback(object : SupportSQLiteOpenHelper.Callback(14) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    val entities = schema.getJSONArray("entities")
                    for (i in 0 until entities.length()) {
                        val entity = entities.getJSONObject(i)
                        val table = entity.getString("tableName")
                        db.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", table))
                        val indices = entity.getJSONArray("indices")
                        for (j in 0 until indices.length()) {
                            db.execSQL(indices.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}", table))
                        }
                    }
                    val setupQueries = schema.getJSONArray("setupQueries")
                    for (i in 0 until setupQueries.length()) db.execSQL(setupQueries.getString(i))
                }

                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {}
            })
            .build()
        return FrameworkSQLiteOpenHelperFactory().create(configuration).writableDatabase
    }

    private fun SupportSQLiteDatabase.count(sql: String): Int =
        query(sql).use { it.moveToFirst(); it.getInt(0) }

    @Test
    fun migration14To15_dropsRecentTabs_keepsOtherRows_andPassesRoomValidation() {
        val v14 = createV14Database()
        v14.execSQL(
            "INSERT INTO recent_tabs (url, title, snapshotPath, faviconUrl, lastVisitedTimestamp, workspaceId) " +
                "VALUES ('https://recent.example', 'Recent', '/snap.jpg', NULL, 1000, 'default_personal')"
        )
        v14.execSQL(
            "INSERT INTO tabs (id, url, title, lastActiveTimestamp, position, isIncognito, isActive, canGoBack, canGoForward) " +
                "VALUES ('tab-1', 'https://tab.example', 'Tab', 2000, 0, 0, 1, 0, 0)"
        )
        v14.execSQL(
            "INSERT INTO history (url, title, timestamp, visitCount, workspaceId) " +
                "VALUES ('https://history.example', 'History', 3000, 2, 'default_personal')"
        )
        v14.execSQL(
            "INSERT INTO favorites (url, title, position, iconUrl, createdAt, workspaceId) " +
                "VALUES ('https://favorite.example', 'Favorite', 0, NULL, 4000, 'default_personal')"
        )
        v14.close()

        val room = Room.databaseBuilder(context, AppDatabase::class.java, dbFile.name)
            .addMigrations(AppDatabase.MIGRATION_14_15)
            .allowMainThreadQueries()
            .build()
        try {
            // Opening runs the migration and then Room's schema validation; a mismatch throws here.
            val db = room.openHelper.writableDatabase

            assertThat(db.version).isEqualTo(15)
            val tables = db.query("SELECT name FROM sqlite_master WHERE type = 'table'").use { c ->
                buildList { while (c.moveToNext()) add(c.getString(0)) }
            }
            assertThat(tables).doesNotContain("recent_tabs")
            val indexes = db.query("SELECT name FROM sqlite_master WHERE type = 'index' AND tbl_name = 'recent_tabs'").use { it.count }
            assertThat(indexes).isEqualTo(0)

            assertThat(db.count("SELECT COUNT(*) FROM tabs WHERE id = 'tab-1' AND url = 'https://tab.example'")).isEqualTo(1)
            assertThat(db.count("SELECT COUNT(*) FROM history WHERE url = 'https://history.example' AND visitCount = 2")).isEqualTo(1)
            assertThat(db.count("SELECT COUNT(*) FROM favorites WHERE url = 'https://favorite.example'")).isEqualTo(1)
        } finally {
            room.close()
        }
    }
}

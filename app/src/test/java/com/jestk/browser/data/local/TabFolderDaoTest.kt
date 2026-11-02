package com.jestk.browser.data.local

import android.content.Context
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.jestk.browser.data.local.dao.TabDao
import com.jestk.browser.data.local.dao.TabFolderDao
import com.jestk.browser.data.local.entity.TabEntity
import com.jestk.browser.data.local.entity.TabFolderEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
class TabFolderDaoTest {

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    private lateinit var db: AppDatabase
    private lateinit var tabDao: TabDao
    private lateinit var tabFolderDao: TabFolderDao

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = AppDatabase.createInMemory(context)
        tabDao = db.tabDao()
        tabFolderDao = db.tabFolderDao()
    }

    @After
    @Throws(IOException::class)
    fun closeDb() {
        db.close()
    }

    @Test
    fun insertAndRetrieveFolder_returnsInsertedFolder() = runTest {
        val folder = TabFolderEntity(
            id = "folder-1",
            title = "Work",
            color = 0xFF2196F3.toInt(),
            isCollapsed = false,
            position = 0
        )
        tabFolderDao.insertFolder(folder)

        val retrieved = tabFolderDao.getFolderById("folder-1")
        assertThat(retrieved).isNotNull()
        assertThat(retrieved?.id).isEqualTo("folder-1")
        assertThat(retrieved?.title).isEqualTo("Work")
        assertThat(retrieved?.color).isEqualTo(0xFF2196F3.toInt())
        assertThat(retrieved?.isCollapsed).isFalse()
        assertThat(retrieved?.position).isEqualTo(0)
    }

    @Test
    fun getAllFolders_ordersByPositionAscThenIdAsc() = runTest {
        // Test deterministic tie-breaker (Lesson #6)
        val folderB = TabFolderEntity(id = "folder-b", title = "B", color = 1, position = 0)
        val folderA = TabFolderEntity(id = "folder-a", title = "A", color = 1, position = 0)
        val folderC = TabFolderEntity(id = "folder-c", title = "C", color = 1, position = 1)

        tabFolderDao.insertFolder(folderB)
        tabFolderDao.insertFolder(folderA)
        tabFolderDao.insertFolder(folderC)

        val foldersFlow = tabFolderDao.getAllFolders().first()
        assertThat(foldersFlow.map { it.id }).containsExactly("folder-a", "folder-b", "folder-c").inOrder()

        val foldersSync = tabFolderDao.getAllFoldersSync()
        assertThat(foldersSync.map { it.id }).containsExactly("folder-a", "folder-b", "folder-c").inOrder()
    }

    @Test
    fun updateFolder_updatesTitleAndColor() = runTest {
        val folder = TabFolderEntity(id = "f-1", title = "Old", color = 100)
        tabFolderDao.insertFolder(folder)

        tabFolderDao.updateFolder(folder.copy(title = "New", color = 200, isCollapsed = true))

        val updated = tabFolderDao.getFolderById("f-1")
        assertThat(updated?.title).isEqualTo("New")
        assertThat(updated?.color).isEqualTo(200)
        assertThat(updated?.isCollapsed).isTrue()
    }

    @Test
    fun deleteFolder_removesFolder() = runTest {
        val folder = TabFolderEntity(id = "f-1", title = "To Delete", color = 1)
        tabFolderDao.insertFolder(folder)

        tabFolderDao.deleteFolder("f-1")

        assertThat(tabFolderDao.getFolderById("f-1")).isNull()
    }

    @Test
    fun updateFolderPositions_reordersCorrectly() = runTest {
        val f1 = TabFolderEntity(id = "f-1", title = "1", color = 1, position = 0)
        val f2 = TabFolderEntity(id = "f-2", title = "2", color = 2, position = 1)
        tabFolderDao.insertFolder(f1)
        tabFolderDao.insertFolder(f2)

        tabFolderDao.updateFolderPositions(listOf(f2, f1))

        val reordered = tabFolderDao.getAllFoldersSync()
        assertThat(reordered.map { it.id }).containsExactly("f-2", "f-1").inOrder()
    }

    @Test
    fun tabDao_folderOperations_queryUpdateAndClear() = runTest {
        val tab1 = TabEntity(id = "t-1", position = 0, folderId = "folder-x")
        val tab2 = TabEntity(id = "t-2", position = 1, folderId = "folder-x")
        val tab3 = TabEntity(id = "t-3", position = 2, folderId = null)
        tabDao.insertTabs(listOf(tab1, tab2, tab3))

        val inFolder = tabDao.getTabsByFolderId("folder-x")
        assertThat(inFolder.map { it.id }).containsExactly("t-1", "t-2").inOrder()

        tabDao.updateFolderId("t-3", "folder-x")
        val inFolderAfterAdd = tabDao.getTabsByFolderId("folder-x")
        assertThat(inFolderAfterAdd.map { it.id }).containsExactly("t-1", "t-2", "t-3").inOrder()

        tabDao.clearFolderId("folder-x")
        assertThat(tabDao.getTabsByFolderId("folder-x")).isEmpty()
        assertThat(tabDao.getTabById("t-1")?.folderId).isNull()
    }

    @Test
    fun migration_8_9_createsTableAndIndicesAndAddsFolderIdToTabs() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dbFile = context.getDatabasePath("test_migration_8_9.db")
        dbFile.delete()

        val helper = androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory().create(
            androidx.sqlite.db.SupportSQLiteOpenHelper.Configuration.builder(context)
                .name("test_migration_8_9.db")
                .callback(object : androidx.sqlite.db.SupportSQLiteOpenHelper.Callback(8) {
                    override fun onCreate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                        db.execSQL(
                            """
                            CREATE TABLE `tabs` (
                                `id` TEXT NOT NULL PRIMARY KEY,
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
                                `canGoForward` INTEGER NOT NULL
                            )
                            """.trimIndent()
                        )
                    }

                    override fun onUpgrade(
                        db: androidx.sqlite.db.SupportSQLiteDatabase,
                        oldVersion: Int,
                        newVersion: Int
                    ) {}
                })
                .build()
        )

        val rawDb = helper.writableDatabase
        AppDatabase.MIGRATION_8_9.migrate(rawDb)

        val folderIndicesCursor = rawDb.query("PRAGMA index_list('tab_folders')")
        val folderIndexNames = mutableListOf<String>()
        while (folderIndicesCursor.moveToNext()) {
            val nameIdx = folderIndicesCursor.getColumnIndex("name")
            if (nameIdx != -1) {
                folderIndexNames.add(folderIndicesCursor.getString(nameIdx))
            }
        }
        folderIndicesCursor.close()

        assertThat(folderIndexNames).contains("index_tab_folders_position")
        assertThat(folderIndexNames).contains("index_tab_folders_createdAt")

        val tabIndicesCursor = rawDb.query("PRAGMA index_list('tabs')")
        val tabIndexNames = mutableListOf<String>()
        while (tabIndicesCursor.moveToNext()) {
            val nameIdx = tabIndicesCursor.getColumnIndex("name")
            if (nameIdx != -1) {
                tabIndexNames.add(tabIndicesCursor.getString(nameIdx))
            }
        }
        tabIndicesCursor.close()

        assertThat(tabIndexNames).contains("index_tabs_folderId")

        val tableInfoCursor = rawDb.query("PRAGMA table_info('tabs')")
        val columnNames = mutableListOf<String>()
        while (tableInfoCursor.moveToNext()) {
            val nameIdx = tableInfoCursor.getColumnIndex("name")
            if (nameIdx != -1) {
                columnNames.add(tableInfoCursor.getString(nameIdx))
            }
        }
        tableInfoCursor.close()
        assertThat(columnNames).contains("folderId")

        rawDb.close()
        dbFile.delete()
    }
}

package com.jestk.browser.data.repository

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.jestk.browser.data.local.AppDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class IncognitoAwareTabRepositoryTest {
    private val testDispatcher = StandardTestDispatcher()
    private lateinit var db: AppDatabase
    private lateinit var repository: IncognitoAwareTabRepository

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = AppDatabase.createInMemory(context)
        repository = IncognitoAwareTabRepository(TabRepositoryImpl(db.tabDao(), testDispatcher))
    }

    @After
    fun teardown() {
        db.close()
    }

    private suspend fun storedIds() = db.tabDao().getAllTabs().map { it.id }

    @Test
    fun incognitoTab_neverReachesTheDatabase() = runTest(testDispatcher) {
        val tab = repository.createTab(url = "https://secret.example", title = "Secret", isIncognito = true)

        repository.updateTabDetails(tab.id, "https://secret.example/2", "Two", canGoBack = true, canGoForward = false)
        repository.updateSnapshot(tab.id, "/snap.png")
        repository.updateFavicon(tab.id, "/icon.png")
        repository.updateThemeColor(tab.id, 0xFF112233.toInt())
        repository.setActiveTab(tab.id)
        repository.updateWorkspaceId(tab.id, "work")
        repository.updateFolderId(tab.id, "folder")
        repository.reorderTabs(listOf(tab))

        assertThat(storedIds()).isEmpty()
        val seen = repository.getTabById(tab.id)!!
        assertThat(seen.isIncognito).isTrue()
        assertThat(seen.url).isEqualTo("https://secret.example/2")
        assertThat(seen.title).isEqualTo("Two")
        assertThat(seen.snapshotPath).isNull()
        assertThat(seen.faviconUrl).isNull()
        assertThat(seen.themeColor).isEqualTo(0xFF112233.toInt())
        assertThat(seen.workspaceId).isEqualTo("work")
        assertThat(seen.folderId).isEqualTo("folder")
    }

    @Test
    fun regularTab_isStoredInTheDatabase() = runTest(testDispatcher) {
        val tab = repository.createTab(url = "https://example.com", title = "Example")

        assertThat(storedIds()).containsExactly(tab.id)
    }

    @Test
    fun getAllTabs_listsRegularTabsFirstThenIncognitoInCreationOrder() = runTest(testDispatcher) {
        val regularA = repository.createTab(url = "https://a.example")
        val incognitoA = repository.createTab(url = "https://i1.example", isIncognito = true)
        val regularB = repository.createTab(url = "https://b.example")
        val incognitoB = repository.createTab(url = "https://i2.example", isIncognito = true)

        val tabs = repository.getAllTabs().first()

        assertThat(tabs.map { it.id }).containsExactly(regularA.id, regularB.id, incognitoA.id, incognitoB.id).inOrder()
        assertThat(tabs.map { it.position }).isInOrder()
        assertThat(repository.getInitialTabs().map { it.id }).containsExactlyElementsIn(tabs.map { it.id }).inOrder()
    }

    @Test
    fun activeIncognitoTab_isTheOnlyActiveTabInTheMergedList() = runTest(testDispatcher) {
        val regular = repository.createTab(url = "https://a.example")
        val incognito = repository.createTab(url = "https://i.example", isIncognito = true)
        repository.setActiveTab(regular.id)
        assertThat(repository.getAllTabs().first().filter { it.isActive }.map { it.id }).containsExactly(regular.id)

        repository.setActiveTab(incognito.id)
        assertThat(repository.getAllTabs().first().filter { it.isActive }.map { it.id }).containsExactly(incognito.id)

        repository.setActiveTab(regular.id)
        assertThat(repository.getAllTabs().first().filter { it.isActive }.map { it.id }).containsExactly(regular.id)
    }

    @Test
    fun closingTheLastIncognitoTab_leavesRegularTabsUntouched() = runTest(testDispatcher) {
        val regular = repository.createTab(url = "https://a.example")
        val incognito = repository.createTab(url = "https://i.example", isIncognito = true)

        repository.closeTab(incognito.id)

        assertThat(repository.getAllTabs().first().map { it.id }).containsExactly(regular.id)
        assertThat(repository.getTabById(incognito.id)).isNull()
        assertThat(storedIds()).containsExactly(regular.id)
    }

    @Test
    fun closeAllTabs_dropsIncognitoTabsToo() = runTest(testDispatcher) {
        repository.createTab(url = "https://a.example")
        repository.createTab(url = "https://i.example", isIncognito = true)

        repository.closeAllTabs()

        assertThat(repository.getAllTabs().first()).isEmpty()
    }

    @Test
    fun recentTabs_includeIncognitoTabsOfTheWorkspace() = runTest(testDispatcher) {
        repository.createTab(url = "https://a.example", workspaceId = "personal")
        val incognito = repository.createTab(url = "https://i.example", isIncognito = true, workspaceId = "personal")
        repository.createTab(url = "https://other.example", isIncognito = true, workspaceId = "work")

        val recent = repository.getRecentTabs(limit = 6, workspaceId = "personal").first()

        assertThat(recent.map { it.id }).contains(incognito.id)
        assertThat(recent.map { it.workspaceId }.distinct()).containsExactly("personal")
    }

    @Test
    fun reorderTabs_sendsOnlyRegularTabsToTheDatabase() = runTest(testDispatcher) {
        val regularA = repository.createTab(url = "https://a.example")
        val regularB = repository.createTab(url = "https://b.example")
        val incognitoA = repository.createTab(url = "https://i1.example", isIncognito = true)
        val incognitoB = repository.createTab(url = "https://i2.example", isIncognito = true)

        repository.reorderTabs(listOf(incognitoB, regularB, incognitoA, regularA))

        val tabs = repository.getAllTabs().first()
        assertThat(tabs.map { it.id }).containsExactly(regularB.id, regularA.id, incognitoB.id, incognitoA.id).inOrder()
        assertThat(storedIds()).containsExactly(regularB.id, regularA.id)
    }
}

package com.jestk.browser.ui.viewmodel

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.jestk.browser.data.local.AppDatabase
import com.jestk.browser.data.repository.SiteAllowlistRepositoryImpl
import com.jestk.browser.domain.model.OverlayType
import com.jestk.browser.domain.model.Tab
import com.jestk.browser.domain.model.Workspace
import com.jestk.browser.engine.adblock.SiteAllowlist
import com.jestk.browser.ui.viewmodel.events.SiteAllowlistEventHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** The browser-menu toggle and the pill action of CB-14, driven through the event handler. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class SiteAllowlistEventsTest {
    private val dispatcher = StandardTestDispatcher()
    private lateinit var db: AppDatabase
    private lateinit var repository: SiteAllowlistRepositoryImpl
    private val snapshot = SiteAllowlist()
    private val commands = mutableListOf<NavigationCommand>()
    private var state = BrowserUiState(
        activeWorkspaceId = Workspace.DEFAULT_WORK_ID,
        activeOverlay = OverlayType.NONE,
        currentUrl = "https://www.news.example.com/story",
        isMenuExpanded = true,
    )

    private fun tab(id: String, url: String, workspaceId: String = Workspace.DEFAULT_WORK_ID) =
        Tab(id = id, url = url, workspaceId = workspaceId, isActive = id == "a")

    /** Opens [tabs]; the one with id "a" is the active tab. */
    private fun openTabs(vararg tabs: Tab) {
        state = state.copy(tabs = tabs.toList(), allTabs = tabs.toList(), activeTabId = "a")
    }

    private fun reloaded() = commands.filterIsInstance<NavigationCommand.ReloadTab>().map { it.tabId }

    @Before
    fun setUp() {
        db = AppDatabase.createInMemory(ApplicationProvider.getApplicationContext<Context>())
        repository = SiteAllowlistRepositoryImpl(db.siteAllowlistDao(), dispatcher)
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun TestScope.handler(scope: kotlinx.coroutines.CoroutineScope = this) = SiteAllowlistEventHandler(
        repository = repository,
        getCurrentState = { state },
        updateUiState = { transform -> state = transform(state) },
        emitNavigation = { commands += it },
        scope = scope,
        ioDispatcher = dispatcher,
        snapshot = snapshot,
    )

    /** Room runs its queries on its own executor, so wait on real time until [condition] holds. */
    private suspend fun TestScope.settle(condition: () -> Boolean) {
        repeat(300) {
            advanceUntilIdle()
            if (condition()) return
            withContext(Dispatchers.Default) { delay(10) }
        }
    }

    private suspend fun entries() = repository.observe().first().map { it.host to it.workspaceId }

    @Test
    fun menuItemOpensTheDialogForTheRegistrableDomainOfTheGivenUrl() = runTest(dispatcher) {
        handler().handle(OpenDisableBlockingPrompt("https://www.news.example.com/story", Workspace.DEFAULT_WORK_ID))

        assertThat(state.siteBlockingPromptHost).isEqualTo("example.com")
    }

    @Test
    fun noDialogForNonWebUrls() = runTest(dispatcher) {
        handler().handle(OpenDisableBlockingPrompt("", Workspace.DEFAULT_WORK_ID))
        assertThat(state.siteBlockingPromptHost).isNull()
        handler().handle(OpenDisableBlockingPrompt("about:blank", Workspace.DEFAULT_WORK_ID))
        assertThat(state.siteBlockingPromptHost).isNull()

        state = state.copy(currentUrl = "about:blank", activeOverlay = OverlayType.NONE)
        assertThat(state.currentAllowlistSite).isNull()
    }

    @Test
    fun dialogForAnotherTabNamesThatTabsSiteAndReloadsOnlyThatSite() = runTest(dispatcher) {
        openTabs(tab("a", "https://www.news.example.com/story"), tab("b", "https://shop.other.test/cart"))
        val handler = handler()
        handler.handle(OpenDisableBlockingPrompt("https://shop.other.test/cart", Workspace.DEFAULT_WORK_ID))
        assertThat(state.siteBlockingPromptHost).isEqualTo("other.test")

        handler.handle(ConfirmDisableBlocking(allWorkspaces = false))
        settle { commands.isNotEmpty() }

        assertThat(entries()).containsExactly("other.test" to Workspace.DEFAULT_WORK_ID)
        assertThat(reloaded()).containsExactly("b")
    }

    @Test
    fun confirmAddsTheActiveWorkspaceEntryRefreshesTheSnapshotAndReloads() = runTest(dispatcher) {
        openTabs(tab("a", "https://www.news.example.com/story"))
        val handler = handler()
        handler.handle(OpenDisableBlockingPrompt("https://www.news.example.com/story", Workspace.DEFAULT_WORK_ID))

        handler.handle(ConfirmDisableBlocking(allWorkspaces = false))
        settle { commands.isNotEmpty() }

        assertThat(state.siteBlockingPromptHost).isNull()
        assertThat(entries()).containsExactly("example.com" to Workspace.DEFAULT_WORK_ID)
        assertThat(snapshot.isAllowlisted("https://example.com/", Workspace.DEFAULT_WORK_ID)).isTrue()
        assertThat(snapshot.isAllowlisted("https://example.com/", Workspace.DEFAULT_PERSONAL_ID)).isFalse()
        assertThat(commands).containsExactly(NavigationCommand.ReloadTab("a"))
    }

    @Test
    fun confirmReloadsEveryOpenTabOfTheSiteInTheScopeButNoOthers() = runTest(dispatcher) {
        openTabs(
            tab("a", "https://www.news.example.com/story"),
            tab("b", "https://example.com/inactive"),
            tab("c", "https://example.com/personal", Workspace.DEFAULT_PERSONAL_ID),
            tab("d", "https://shop.other.test/cart"),
            tab("e", "about:blank"),
        )
        val handler = handler()
        handler.handle(OpenDisableBlockingPrompt("https://example.com/inactive", Workspace.DEFAULT_WORK_ID))

        handler.handle(ConfirmDisableBlocking(allWorkspaces = false))
        settle { reloaded().size >= 2 }

        assertThat(reloaded()).containsExactly("a", "b")
    }

    @Test
    fun confirmForAllWorkspacesAlsoReloadsTheSiteInOtherWorkspaces() = runTest(dispatcher) {
        openTabs(
            tab("a", "https://www.news.example.com/story"),
            tab("c", "https://example.com/personal", Workspace.DEFAULT_PERSONAL_ID),
        )
        val handler = handler()
        handler.handle(OpenDisableBlockingPrompt("https://example.com/", Workspace.DEFAULT_WORK_ID))

        handler.handle(ConfirmDisableBlocking(allWorkspaces = true))
        settle { reloaded().size >= 2 }

        assertThat(reloaded()).containsExactly("a", "c")
    }

    @Test
    fun confirmWithAllWorkspacesStoresTheNullScope() = runTest(dispatcher) {
        val handler = handler()
        handler.handle(OpenDisableBlockingPrompt("https://www.news.example.com/story", Workspace.DEFAULT_WORK_ID))

        handler.handle(ConfirmDisableBlocking(allWorkspaces = true))
        settle { snapshot.isAllowlisted("https://example.com/", null) }

        assertThat(entries()).containsExactly("example.com" to null)
        assertThat(snapshot.isAllowlisted("https://example.com/", Workspace.DEFAULT_PERSONAL_ID)).isTrue()
    }

    @Test
    fun dismissClosesTheDialogWithoutChanges() = runTest(dispatcher) {
        val handler = handler()
        handler.handle(OpenDisableBlockingPrompt("https://www.news.example.com/story", Workspace.DEFAULT_WORK_ID))

        handler.handle(DismissDisableBlockingPrompt)
        advanceUntilIdle()

        assertThat(state.siteBlockingPromptHost).isNull()
        assertThat(entries()).isEmpty()
        assertThat(commands).isEmpty()
    }

    @Test
    fun enableRemovesEveryEntryThatCoversThePageButNotOtherWorkspaces() = runTest(dispatcher) {
        repository.add("example.com", Workspace.DEFAULT_WORK_ID)
        repository.add("example.com", null)
        repository.add("example.com", Workspace.DEFAULT_PERSONAL_ID)
        repository.add("other.test", Workspace.DEFAULT_WORK_ID)
        snapshot.update(repository.observe().first())
        state = state.copy(siteAllowlist = repository.observe().first())
        assertThat(state.isSiteAllowlisted("https://www.news.example.com/story", Workspace.DEFAULT_WORK_ID)).isTrue()
        // "p" stays covered by its own Personal entry, so enabling in Work leaves it alone.
        openTabs(
            tab("a", "https://www.news.example.com/story"),
            tab("c", "https://example.com/inactive"),
            tab("p", "https://example.com/personal", Workspace.DEFAULT_PERSONAL_ID),
            tab("d", "https://shop.other.test/cart"),
        )

        handler().handle(EnableBlockingForSite("https://www.news.example.com/story", Workspace.DEFAULT_WORK_ID))
        settle { reloaded().size >= 2 }

        assertThat(entries()).containsExactly("example.com" to Workspace.DEFAULT_PERSONAL_ID, "other.test" to Workspace.DEFAULT_WORK_ID)
        assertThat(snapshot.isAllowlisted("https://example.com/", Workspace.DEFAULT_WORK_ID)).isFalse()
        assertThat(reloaded()).containsExactly("a", "c")
    }

    @Test
    fun enableForAnotherTabsSiteReloadsThatSiteOnlyNotTheActiveTab() = runTest(dispatcher) {
        repository.add("other.test", Workspace.DEFAULT_WORK_ID)
        snapshot.update(repository.observe().first())
        state = state.copy(siteAllowlist = repository.observe().first())
        openTabs(tab("a", "https://www.news.example.com/story"), tab("b", "https://www.other.test/x"))

        handler().handle(EnableBlockingForSite("https://www.other.test/x", Workspace.DEFAULT_WORK_ID))
        settle { commands.isNotEmpty() }

        assertThat(entries()).isEmpty()
        assertThat(reloaded()).containsExactly("b")
    }

    @Test
    fun theTabListIsReadWhenTheUserActsNotWhenTheWriteFinishes() = runTest(dispatcher) {
        openTabs(tab("a", "https://www.news.example.com/story"), tab("b", "https://shop.other.test/cart"))
        val handler = handler()
        handler.handle(OpenDisableBlockingPrompt("https://shop.other.test/cart", Workspace.DEFAULT_WORK_ID))

        handler.handle(ConfirmDisableBlocking(allWorkspaces = false))
        // The tab closes before the write finishes: the reload still targets the tab the user acted on.
        state = state.copy(tabs = emptyList(), allTabs = emptyList())
        settle { commands.isNotEmpty() }

        assertThat(reloaded()).containsExactly("b")
    }

    @Test
    fun enableWithNoOpenTabOfTheSiteReloadsNothing() = runTest(dispatcher) {
        repository.add("other.test", Workspace.DEFAULT_WORK_ID)
        snapshot.update(repository.observe().first())
        openTabs(tab("a", "https://www.news.example.com/story"))

        handler().handle(EnableBlockingForSite("https://www.other.test/x", Workspace.DEFAULT_WORK_ID))
        settle { !snapshot.isAllowlisted("https://other.test/", Workspace.DEFAULT_WORK_ID) }

        assertThat(entries()).isEmpty()
        assertThat(commands).isEmpty()
    }

    @Test
    fun popupPillActionAddsAnEntryForTheActiveWorkspaceWithoutReloading() = runTest(dispatcher) {
        handler().allowFromPopup("https://stream.example.org/watch?v=1")
        settle { snapshot.isAllowlisted("https://stream.example.org/", Workspace.DEFAULT_WORK_ID) }

        assertThat(entries()).containsExactly("example.org" to Workspace.DEFAULT_WORK_ID)
        assertThat(snapshot.isAllowlisted("https://stream.example.org/", Workspace.DEFAULT_WORK_ID)).isTrue()
        assertThat(commands).isEmpty()
    }

    @Test
    fun removeEventDeletesOneEntryForTheSettingsList() = runTest(dispatcher) {
        repository.add("example.com", null)
        repository.add("example.com", "w1")

        handler().handle(RemoveSiteAllowlistEntry("example.com", null))
        settle { snapshot.isAllowlisted("https://example.com/", "w1") }

        assertThat(entries()).containsExactly("example.com" to "w1")
    }

    @Test
    fun observeMirrorsEntriesIntoUiState() = runTest(dispatcher) {
        val handler = handler(backgroundScope)
        handler.observe()
        repository.add("example.com", null)
        settle { state.siteAllowlist.isNotEmpty() }

        assertThat(state.siteAllowlist.map { it.host }).containsExactly("example.com")
        assertThat(state.isSiteAllowlisted(state.currentUrl, Workspace.DEFAULT_WORK_ID)).isTrue()
    }

    @Test
    fun promptFromATabOfAnotherWorkspaceScopesTheEntryToThatWorkspace() = runTest(dispatcher) {
        val handler = handler()
        // The active workspace is Work; the clicked tab lives in Personal.
        handler.handle(OpenDisableBlockingPrompt("https://shop.other.test/cart", Workspace.DEFAULT_PERSONAL_ID))
        assertThat(state.siteBlockingPromptWorkspaceId).isEqualTo(Workspace.DEFAULT_PERSONAL_ID)

        handler.handle(ConfirmDisableBlocking(allWorkspaces = false))
        settle { snapshot.isAllowlisted("https://other.test/", Workspace.DEFAULT_PERSONAL_ID) }

        assertThat(entries()).containsExactly("other.test" to Workspace.DEFAULT_PERSONAL_ID)
        assertThat(snapshot.isAllowlisted("https://other.test/", Workspace.DEFAULT_WORK_ID)).isFalse()
        assertThat(state.siteBlockingPromptWorkspaceId).isNull()
    }

    @Test
    fun enableForATabOfAnotherWorkspaceRemovesOnlyThatWorkspacesEntry() = runTest(dispatcher) {
        repository.add("other.test", Workspace.DEFAULT_WORK_ID)
        repository.add("other.test", Workspace.DEFAULT_PERSONAL_ID)
        snapshot.update(repository.observe().first())
        state = state.copy(siteAllowlist = repository.observe().first())
        assertThat(state.isSiteAllowlisted("https://other.test/x", Workspace.DEFAULT_PERSONAL_ID)).isTrue()

        handler().handle(EnableBlockingForSite("https://other.test/x", Workspace.DEFAULT_PERSONAL_ID))
        settle { !snapshot.isAllowlisted("https://other.test/", Workspace.DEFAULT_PERSONAL_ID) }

        assertThat(entries()).containsExactly("other.test" to Workspace.DEFAULT_WORK_ID)
    }
}

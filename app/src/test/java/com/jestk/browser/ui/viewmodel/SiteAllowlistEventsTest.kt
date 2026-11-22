package com.jestk.browser.ui.viewmodel

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.jestk.browser.data.local.AppDatabase
import com.jestk.browser.data.repository.SiteAllowlistRepositoryImpl
import com.jestk.browser.domain.model.OverlayType
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
    fun menuItemOpensTheDialogForTheRegistrableDomainAndClosesTheMenu() = runTest(dispatcher) {
        handler().handle(OpenDisableBlockingPrompt)

        assertThat(state.siteBlockingPromptHost).isEqualTo("example.com")
        assertThat(state.isMenuExpanded).isFalse()
    }

    @Test
    fun noDialogOnHomeOrNonWebPages() = runTest(dispatcher) {
        state = state.copy(currentUrl = "")
        handler().handle(OpenDisableBlockingPrompt)
        assertThat(state.siteBlockingPromptHost).isNull()
        assertThat(state.currentAllowlistSite).isNull()

        state = state.copy(currentUrl = "about:blank", activeOverlay = OverlayType.NONE)
        assertThat(state.currentAllowlistSite).isNull()
    }

    @Test
    fun confirmAddsTheActiveWorkspaceEntryRefreshesTheSnapshotAndReloads() = runTest(dispatcher) {
        val handler = handler()
        handler.handle(OpenDisableBlockingPrompt)

        handler.handle(ConfirmDisableBlocking(allWorkspaces = false))
        settle { commands.isNotEmpty() }

        assertThat(state.siteBlockingPromptHost).isNull()
        assertThat(entries()).containsExactly("example.com" to Workspace.DEFAULT_WORK_ID)
        assertThat(snapshot.isAllowlisted("https://example.com/", Workspace.DEFAULT_WORK_ID)).isTrue()
        assertThat(snapshot.isAllowlisted("https://example.com/", Workspace.DEFAULT_PERSONAL_ID)).isFalse()
        assertThat(commands).containsExactly(NavigationCommand.Reload)
    }

    @Test
    fun confirmWithAllWorkspacesStoresTheNullScope() = runTest(dispatcher) {
        val handler = handler()
        handler.handle(OpenDisableBlockingPrompt)

        handler.handle(ConfirmDisableBlocking(allWorkspaces = true))
        settle { snapshot.isAllowlisted("https://example.com/", null) }

        assertThat(entries()).containsExactly("example.com" to null)
        assertThat(snapshot.isAllowlisted("https://example.com/", Workspace.DEFAULT_PERSONAL_ID)).isTrue()
    }

    @Test
    fun dismissClosesTheDialogWithoutChanges() = runTest(dispatcher) {
        val handler = handler()
        handler.handle(OpenDisableBlockingPrompt)

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
        assertThat(state.isCurrentSiteAllowlisted).isTrue()

        handler().handle(EnableBlockingForCurrentSite)
        settle { commands.isNotEmpty() }

        assertThat(entries()).containsExactly("example.com" to Workspace.DEFAULT_PERSONAL_ID, "other.test" to Workspace.DEFAULT_WORK_ID)
        assertThat(snapshot.isAllowlisted("https://example.com/", Workspace.DEFAULT_WORK_ID)).isFalse()
        assertThat(commands).containsExactly(NavigationCommand.Reload)
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
        assertThat(state.isCurrentSiteAllowlisted).isTrue()
    }
}

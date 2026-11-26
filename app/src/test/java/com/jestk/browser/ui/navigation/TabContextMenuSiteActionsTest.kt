package com.jestk.browser.ui.navigation

import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.google.common.truth.Truth.assertThat
import com.jestk.browser.R
import com.jestk.browser.domain.model.FavoriteItem
import com.jestk.browser.domain.model.SiteAllowlistEntry
import com.jestk.browser.domain.model.Tab
import com.jestk.browser.domain.model.ThemeMode
import com.jestk.browser.domain.model.Workspace
import com.jestk.browser.ui.theme.JestkBrowserTheme
import com.jestk.browser.ui.viewmodel.BrowserUiEvent
import com.jestk.browser.ui.viewmodel.BrowserUiState
import com.jestk.browser.ui.viewmodel.EnableBlockingForSite
import com.jestk.browser.ui.viewmodel.OpenDisableBlockingPrompt
import com.jestk.browser.ui.viewmodel.ReloadTab
import com.jestk.browser.ui.viewmodel.ToggleTabFavorite
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.GraphicsMode

/** The favorite star and the blocking row of the tab long-tap menu (moved out of the three-dots menu, PRO-194). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TabContextMenuSiteActionsTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val events = mutableListOf<BrowserUiEvent>()
    private var dismissed = false

    private fun string(id: Int): String = RuntimeEnvironment.getApplication().getString(id)

    private fun show(state: BrowserUiState, tab: Tab) {
        composeRule.setContent {
            JestkBrowserTheme(themeMode = ThemeMode.LIGHT) {
                val actions = tabMenuSiteActions(tab, state) { events += it }
                TabContextMenu(
                    expanded = true,
                    onDismissRequest = { dismissed = true },
                    isDark = false,
                    onCloseTab = {},
                    onCopyUrl = {},
                    isFavorite = actions.isFavorite,
                    onToggleFavorite = actions.onToggleFavorite,
                    isSiteAllowlisted = actions.isSiteAllowlisted,
                    onToggleBlocking = actions.onToggleBlocking,
                    onShareLink = {},
                    onReload = actions.onReload,
                )
            }
        }
    }

    /** The active tab is "a"; an optional inactive tab is "b". Returns the state and its tabs. */
    private fun stateOf(
        activeUrl: String,
        inactiveUrl: String? = null,
        favorites: List<FavoriteItem> = emptyList(),
        allowlist: List<SiteAllowlistEntry> = emptyList(),
    ): Pair<BrowserUiState, List<Tab>> {
        val active = Tab(id = "a", url = activeUrl, title = "Active", isActive = true)
        val inactive = inactiveUrl?.let { Tab(id = "b", url = it, title = "Other") }
        val tabs = listOfNotNull(active, inactive)
        val state =
            BrowserUiState(
                currentUrl = activeUrl,
                pageTitle = "Active",
                tabs = tabs,
                activeTabId = "a",
                favorites = favorites,
                siteAllowlist = allowlist,
            )
        return state to tabs
    }

    @Test
    fun webTabOffersAddAndTapTogglesThatTabThenDismisses() {
        val (state, tabs) = stateOf("https://example.com/a")
        show(state, tabs.first())

        composeRule.onNodeWithContentDescription(string(R.string.menu_add_to_favorites)).performClick()

        assertThat(events).containsExactly(ToggleTabFavorite("a"))
        assertThat(dismissed).isTrue()
    }

    @Test
    fun inactiveTabMenuOffersReloadForThatTabAndDismisses() {
        val (state, tabs) = stateOf("https://example.com/a", inactiveUrl = "https://other.test/x")
        show(state, tabs.last())

        composeRule.onNodeWithContentDescription(string(R.string.reload_tab)).performClick()

        assertThat(events).containsExactly(ReloadTab("b"))
        assertThat(dismissed).isTrue()
    }

    @Test
    fun activeTabMenuReloadIsTiedToItsTabId() {
        val (state, tabs) = stateOf("https://example.com/a")
        show(state, tabs.first())

        composeRule.onNodeWithContentDescription(string(R.string.reload_tab)).performClick()

        assertThat(events).containsExactly(ReloadTab("a"))
    }

    @Test
    fun iconRowOrderIsCopyShareFavoriteReload() {
        val (state, tabs) = stateOf("https://example.com/a", inactiveUrl = "https://other.test/x")
        show(state, tabs.last())

        fun x(description: Int) =
            composeRule.onNodeWithContentDescription(string(description)).fetchSemanticsNode().positionInRoot.x

        val order = listOf(R.string.copy_link_url, R.string.share_link, R.string.menu_add_to_favorites, R.string.reload_tab)
        assertThat(order.map(::x)).isInStrictOrder()
    }

    @Test
    fun blockingLabelsDropOnThisSite() {
        val (state, tabs) = stateOf("https://example.com/a")
        show(state, tabs.first())
        composeRule.onNodeWithText("Disable blocking").assertExists()
        assertThat(string(R.string.menu_enable_blocking)).isEqualTo("Enable blocking")
    }

    @Test
    fun savedPageOffersRemove() {
        val saved = FavoriteItem(id = 7, title = "Page", url = "https://example.com/a", workspaceId = Workspace.DEFAULT_PERSONAL_ID)
        val (state, tabs) = stateOf("https://example.com/a", favorites = listOf(saved))
        show(state, tabs.first())

        composeRule.onNodeWithContentDescription(string(R.string.menu_remove_from_favorites)).assertExists()
        composeRule.onNodeWithContentDescription(string(R.string.menu_add_to_favorites)).assertDoesNotExist()
    }

    @Test
    fun inactiveTabActsOnItsOwnPageNotTheActiveOne() {
        val saved = FavoriteItem(id = 7, title = "Other", url = "https://other.test/x", workspaceId = Workspace.DEFAULT_PERSONAL_ID)
        val (state, tabs) = stateOf("https://example.com/a", inactiveUrl = "https://other.test/x", favorites = listOf(saved))
        show(state, tabs.last())

        composeRule.onNodeWithContentDescription(string(R.string.menu_remove_from_favorites)).performClick()

        assertThat(events).containsExactly(ToggleTabFavorite("b"))
    }

    @Test
    fun blockingRowOfAnInactiveTabNamesThatTabsSite() {
        val (state, tabs) = stateOf("https://example.com/a", inactiveUrl = "https://www.other.test/x")
        show(state, tabs.last())

        composeRule.onNodeWithText(string(R.string.menu_disable_blocking)).performClick()

        assertThat(events).containsExactly(OpenDisableBlockingPrompt("https://www.other.test/x", Workspace.DEFAULT_PERSONAL_ID))
    }

    @Test
    fun allowlistedSiteOffersEnableBlocking() {
        val entry = SiteAllowlistEntry("other.test", null, 1L)
        val (state, tabs) = stateOf("https://example.com/a", inactiveUrl = "https://www.other.test/x", allowlist = listOf(entry))
        show(state, tabs.last())

        composeRule.onNodeWithText(string(R.string.menu_disable_blocking)).assertDoesNotExist()
        composeRule.onNodeWithText(string(R.string.menu_enable_blocking)).performClick()

        assertThat(events).containsExactly(EnableBlockingForSite("https://www.other.test/x", Workspace.DEFAULT_PERSONAL_ID))
    }

    @Test
    fun blockingStateFollowsTheClickedTabsWorkspaceNotTheActiveOne() {
        // The active workspace is Personal and the site is switched off there only; the tab lives in Work.
        val entry = SiteAllowlistEntry("other.test", Workspace.DEFAULT_PERSONAL_ID, 1L)
        val workTab = Tab(id = "w", url = "https://www.other.test/x", title = "Other", workspaceId = Workspace.DEFAULT_WORK_ID)
        val state =
            BrowserUiState(
                currentUrl = "https://example.com/a",
                tabs = listOf(Tab(id = "a", url = "https://example.com/a", isActive = true), workTab),
                activeTabId = "a",
                activeWorkspaceId = Workspace.DEFAULT_PERSONAL_ID,
                siteAllowlist = listOf(entry),
            )

        val actions = tabMenuSiteActions(workTab, state) { events += it }

        assertThat(actions.isSiteAllowlisted).isFalse()
        actions.onToggleBlocking!!.invoke()
        assertThat(events).containsExactly(OpenDisableBlockingPrompt("https://www.other.test/x", Workspace.DEFAULT_WORK_ID))
    }

    @Test
    fun incognitoTabHasNoStarButKeepsTheBlockingRow() {
        val incognito = Tab(id = "i", url = "https://secret.example/", title = "Secret", isIncognito = true)
        val state = BrowserUiState(currentUrl = "https://example.com/a", tabs = listOf(incognito), activeTabId = "x")

        val actions = tabMenuSiteActions(incognito, state) {}

        assertThat(actions.onToggleFavorite).isNull()
        assertThat(actions.isFavorite).isFalse()
        assertThat(actions.onToggleBlocking).isNotNull()
    }

    @Test
    fun homeAndInternalTabsHaveNeitherStarNorBlockingRow() {
        for (url in listOf("", "about:blank", "chrome://version")) {
            val (state, tabs) = stateOf("https://example.com/a", inactiveUrl = url)
            val actions = tabMenuSiteActions(tabs.last(), state) {}
            assertThat(actions.onToggleFavorite).isNull()
            assertThat(actions.onToggleBlocking).isNull()
            assertThat(actions.onReload).isNull()
        }
    }
}

package com.jestk.browser.domain.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import kotlin.random.Random

class DomainGroupSuggestionTest {
    private val ws = Workspace.DEFAULT_PERSONAL_ID

    private fun tab(
        id: String,
        url: String,
        folderId: String? = null,
        workspaceId: String = ws,
        lastActive: Long = 0L,
    ) = Tab(id = id, url = url, folderId = folderId, workspaceId = workspaceId, lastActiveTimestamp = lastActive)

    @Test
    fun subdomainsCountTowardsTheRegistrableDomain() {
        val tabs = listOf(
            tab("1", "https://www.youtube.com/watch?v=1"),
            tab("2", "https://music.youtube.com/"),
            tab("3", "http://youtube.com/feed"),
        )

        val suggestion = suggestDomainGroup(tabs, ws)

        assertThat(suggestion?.domain).isEqualTo("youtube.com")
        assertThat(suggestion?.folderTitle).isEqualTo("Youtube")
        assertThat(suggestion?.tabIds).containsExactly("1", "2", "3").inOrder()
    }

    @Test
    fun twoTabsAreBelowTheThreshold() {
        val tabs = listOf(tab("1", "https://a.com/1"), tab("2", "https://a.com/2"))

        assertThat(suggestDomainGroup(tabs, ws)).isNull()
    }

    @Test
    fun tabsAlreadyInAFolderAreNotCounted() {
        val tabs = listOf(
            tab("1", "https://a.com/1"),
            tab("2", "https://a.com/2"),
            tab("3", "https://a.com/3", folderId = "f"),
        )
        assertThat(suggestDomainGroup(tabs, ws)).isNull()

        val withFourth = tabs + tab("4", "https://a.com/4")
        assertThat(suggestDomainGroup(withFourth, ws)?.tabIds).containsExactly("1", "2", "4").inOrder()
    }

    @Test
    fun otherWorkspacesAreIgnored() {
        val tabs = listOf(
            tab("1", "https://a.com/1"),
            tab("2", "https://a.com/2"),
            tab("3", "https://a.com/3", workspaceId = "other"),
            tab("4", "https://a.com/4", workspaceId = "other"),
        )

        assertThat(suggestDomainGroup(tabs, ws)).isNull()
        assertThat(suggestDomainGroup(tabs, "other")).isNull()
        assertThat(suggestDomainGroup(tabs + tab("5", "https://a.com/5", workspaceId = "other"), "other")?.tabIds)
            .containsExactly("3", "4", "5")
    }

    @Test
    fun nonWebAndHostlessTabsAreSkipped() {
        val tabs = listOf(
            tab("1", ""),
            tab("2", "about:blank"),
            tab("3", "file:///sdcard/a.html"),
            tab("4", "https://192.168.0.1/a"),
            tab("5", "https://192.168.0.1/b"),
            tab("6", "https://192.168.0.1/c"),
            tab("7", "http://localhost:8080/a"),
            tab("8", "http://localhost:8080/b"),
            tab("9", "http://localhost:8080/c"),
            tab("10", "chrome://version"),
        )

        assertThat(suggestDomainGroup(tabs, ws)).isNull()
    }

    @Test
    fun dismissedSiteStaysHiddenUntilItsTabCountGrows() {
        val three = listOf(tab("1", "https://a.com/1"), tab("2", "https://a.com/2"), tab("3", "https://a.com/3"))
        val dismissed = mapOf(DomainGroupKey(ws, "a.com") to 3)

        assertThat(suggestDomainGroup(three, ws, dismissed)).isNull()
        assertThat(suggestDomainGroup(three + tab("4", "https://m.a.com/4"), ws, dismissed)?.tabIds).hasSize(4)
    }

    @Test
    fun dismissalDoesNotCrossWorkspaces() {
        val tabs = (1..3).map { tab("$it", "https://a.com/$it", workspaceId = "other") }
        val dismissed = mapOf(DomainGroupKey(ws, "a.com") to 3)

        assertThat(suggestDomainGroup(tabs, "other", dismissed)?.domain).isEqualTo("a.com")
    }

    @Test
    fun dismissedSiteDoesNotHideAnotherSite() {
        val tabs = (1..3).map { tab("a$it", "https://a.com/$it") } + (1..3).map { tab("b$it", "https://b.org/$it") }
        val dismissed = mapOf(DomainGroupKey(ws, "a.com") to 3)

        assertThat(suggestDomainGroup(tabs, ws, dismissed)?.domain).isEqualTo("b.org")
    }

    @Test
    fun theSiteWithTheMostTabsWins() {
        val tabs = (1..3).map { tab("a$it", "https://a.com/$it") } + (1..4).map { tab("b$it", "https://b.org/$it") }

        assertThat(suggestDomainGroup(tabs, ws)?.domain).isEqualTo("b.org")
    }

    @Test
    fun aTieGoesToTheMostRecentlyUsedSite() {
        val tabs = (1..3).map { tab("a$it", "https://a.com/$it", lastActive = 10L) } +
            (1..3).map { tab("b$it", "https://b.org/$it", lastActive = if (it == 2) 50L else 5L) }

        assertThat(suggestDomainGroup(tabs, ws)?.domain).isEqualTo("b.org")
    }

    @Test
    fun folderTitleDropsThePublicSuffix() {
        assertThat(folderTitleForDomain("youtube.com")).isEqualTo("Youtube")
        assertThat(folderTitleForDomain("bbc.co.uk")).isEqualTo("Bbc")
        assertThat(folderTitleForDomain("github.com")).isEqualTo("Github")
        assertThat(folderTitleForDomain("example.com.au")).isEqualTo("Example")
    }

    @Test
    fun registrableDomainHandlesMultiPartSuffixes() {
        assertThat(registrableDomain("https://www.bbc.co.uk/news")).isEqualTo("bbc.co.uk")
        assertThat(registrableDomain("https://music.youtube.com/")).isEqualTo("youtube.com")
        assertThat(registrableDomain("https://user.github.io/")).isEqualTo("user.github.io")
        assertThat(registrableDomain("https://co.uk/")).isNull()
    }

    @Test
    fun colorPrefersOnesNotUsedInTheWorkspace() {
        val usedExceptLast = TabFolder.PALETTE.dropLast(1).mapIndexed { i, c -> TabFolder(id = "f$i", color = c, workspaceId = ws) }
        repeat(20) {
            assertThat(pickFolderColor(usedExceptLast, ws, Random(it))).isEqualTo(TabFolder.PALETTE.last())
        }
    }

    @Test
    fun colorIgnoresFoldersOfOtherWorkspacesAndFallsBackToThePalette() {
        val otherWorkspace = TabFolder.PALETTE.map { TabFolder(id = it.toString(), color = it, workspaceId = "other") }
        assertThat(pickFolderColor(otherWorkspace, ws, Random(1))).isIn(TabFolder.PALETTE)

        val allUsed = TabFolder.PALETTE.map { TabFolder(id = it.toString(), color = it, workspaceId = ws) }
        assertThat(pickFolderColor(allUsed, ws, Random(1))).isIn(TabFolder.PALETTE)
    }
}

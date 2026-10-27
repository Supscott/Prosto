package com.jestk.browser.engine

import com.jestk.browser.domain.model.FavoriteItem
import com.jestk.browser.domain.model.HistoryItem
import com.jestk.browser.domain.model.Tab
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SearchInputAutocompleteMatcherTest {

    private val matcher = SearchInputAutocompleteMatcher()

    @Test
    fun findInlineMatch_emptyQuery_returnsNull() {
        val result = matcher.findInlineMatch(
            query = "",
            tabs = listOf(Tab(id = "1", url = "https://github.com", title = "GitHub")),
            favorites = emptyList(),
            history = emptyList(),
        )
        assertNull(result)
    }

    @Test
    fun findInlineMatch_queryWithSpaces_returnsNull() {
        val result = matcher.findInlineMatch(
            query = "git hub",
            tabs = listOf(Tab(id = "1", url = "https://github.com", title = "GitHub")),
            favorites = emptyList(),
            history = emptyList(),
        )
        assertNull(result)
    }

    @Test
    fun findInlineMatch_matchesDomainPrefix_preservesTypedCase() {
        val result = matcher.findInlineMatch(
            query = "Git",
            tabs = listOf(Tab(id = "1", url = "https://github.com/torvalds", title = "GitHub")),
            favorites = emptyList(),
            history = emptyList(),
        )
        assertEquals("Github.com", result)
    }

    @Test
    fun findInlineMatch_stripsSchemeAndWww_forDomainMatch() {
        val result = matcher.findInlineMatch(
            query = "you",
            tabs = emptyList(),
            favorites = listOf(FavoriteItem(id = 1, url = "https://www.youtube.com/watch", title = "YouTube")),
            history = emptyList(),
        )
        assertEquals("youtube.com", result)
    }

    @Test
    fun findInlineMatch_prioritizesOpenTabsOverFavoritesAndHistory() {
        val tabs = listOf(Tab(id = "1", url = "https://gitlab.com", title = "GitLab", lastActiveTimestamp = 100))
        val favorites = listOf(FavoriteItem(id = 1, url = "https://github.com", title = "GitHub"))
        val history = listOf(HistoryItem(id = 1, url = "https://gitea.io", title = "Gitea", timestamp = 200))

        val result = matcher.findInlineMatch(
            query = "git",
            tabs = tabs,
            favorites = favorites,
            history = history,
        )
        // Tabs has highest priority
        assertEquals("gitlab.com", result)
    }

    @Test
    fun findInlineMatch_prioritizesFavoritesOverHistory() {
        val favorites = listOf(FavoriteItem(id = 1, url = "https://github.com", title = "GitHub"))
        val history = listOf(HistoryItem(id = 1, url = "https://gitlab.com", title = "GitLab", timestamp = 200))

        val result = matcher.findInlineMatch(
            query = "git",
            tabs = emptyList(),
            favorites = favorites,
            history = history,
        )
        // Favorites has higher priority than history
        assertEquals("github.com", result)
    }

    @Test
    fun findInlineMatch_historySortsByMostRecentTimestamp() {
        val history = listOf(
            HistoryItem(id = 1, url = "https://gitlab.com", title = "GitLab", timestamp = 100),
            HistoryItem(id = 2, url = "https://github.com", title = "GitHub", timestamp = 500),
        )

        val result = matcher.findInlineMatch(
            query = "git",
            tabs = emptyList(),
            favorites = emptyList(),
            history = history,
        )
        // github.com has timestamp 500 vs gitlab 100
        assertEquals("github.com", result)
    }

    @Test
    fun findInlineMatch_withSlash_completesPath() {
        val history = listOf(
            HistoryItem(id = 1, url = "https://github.com/torvalds/linux", title = "Linux", timestamp = 100),
        )

        val result = matcher.findInlineMatch(
            query = "github.com/tor",
            tabs = emptyList(),
            favorites = emptyList(),
            history = history,
        )
        assertEquals("github.com/torvalds/linux", result)
    }

    @Test
    fun findInlineMatch_exactMatch_returnsNull() {
        val history = listOf(
            HistoryItem(id = 1, url = "https://github.com", title = "GitHub", timestamp = 100),
        )

        val result = matcher.findInlineMatch(
            query = "github.com",
            tabs = emptyList(),
            favorites = emptyList(),
            history = history,
        )
        // Already fully typed, no remaining suffix to autocomplete
        assertNull(result)
    }

    @Test
    fun findMatchingOpenTabs_returnsTabsMatchingQuery() {
        val tabs = listOf(
            Tab(id = "1", url = "https://www.youtube.com/feed", title = "YouTube Feed"),
            Tab(id = "2", url = "https://youtube.com/watch?v=123", title = "YouTube Video"),
            Tab(id = "3", url = "https://google.com", title = "Google"),
        )

        val matches = matcher.findMatchingOpenTabs(query = "you", tabs = tabs)
        assertEquals(2, matches.size)
        assertEquals("1", matches[0].id)
        assertEquals("2", matches[1].id)
    }

    @Test
    fun findInlineMatch_queryWithTabWhitespace_returnsNull() {
        val result = matcher.findInlineMatch(
            query = "git\thub",
            tabs = listOf(Tab(id = "1", url = "https://github.com", title = "GitHub")),
            favorites = emptyList(),
            history = emptyList(),
        )
        assertNull(result)
    }

    @Test
    fun findInlineMatch_uppercaseSchemeAndWww_stripsCorrectly() {
        val result = matcher.findInlineMatch(
            query = "you",
            tabs = emptyList(),
            favorites = listOf(FavoriteItem(id = 1, url = "HTTPS://WWW.YOUTUBE.COM/watch", title = "YouTube")),
            history = emptyList(),
        )
        assertEquals("youtube.com", result)
    }

    @Test
    fun findMatchingOpenTabs_matchesByTitle() {
        val tabs = listOf(
            Tab(id = "1", url = "https://reddit.com/r/androiddev", title = "Android Developers Community"),
            Tab(id = "2", url = "https://google.com", title = "Google"),
        )

        val matches = matcher.findMatchingOpenTabs(query = "developers", tabs = tabs)
        assertEquals(1, matches.size)
        assertEquals("1", matches[0].id)
    }
}

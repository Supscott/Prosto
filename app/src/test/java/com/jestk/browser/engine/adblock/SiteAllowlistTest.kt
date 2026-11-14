package com.jestk.browser.engine.adblock

import com.google.common.truth.Truth.assertThat
import com.jestk.browser.domain.model.SiteAllowlistEntry
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SiteAllowlistTest {
    private fun entry(host: String, workspace: String?) = SiteAllowlistEntry(host, workspace, 0L)

    @Test
    fun emptyByDefault() {
        assertThat(SiteAllowlist().isAllowlisted("https://example.com/", "w1")).isFalse()
    }

    @Test
    fun registrableDomainEntryCoversSubdomains() {
        val list = SiteAllowlist().apply { update(listOf(entry("example.com", null))) }

        assertThat(list.isAllowlisted("https://example.com/", "w1")).isTrue()
        assertThat(list.isAllowlisted("https://www.example.com/a", "w1")).isTrue()
        assertThat(list.isAllowlisted("https://deep.sub.example.com/", "w1")).isTrue()
        assertThat(list.isAllowlisted("https://notexample.com/", "w1")).isFalse()
        assertThat(list.isAllowlisted("https://example.com.evil.test/", "w1")).isFalse()
    }

    @Test
    fun subdomainEntryDoesNotCoverTheParent() {
        val list = SiteAllowlist().apply { update(listOf(entry("news.example.com", null))) }

        assertThat(list.isAllowlisted("https://news.example.com/", "w1")).isTrue()
        assertThat(list.isAllowlisted("https://example.com/", "w1")).isFalse()
        assertThat(list.isAllowlisted("https://shop.example.com/", "w1")).isFalse()
    }

    @Test
    fun workspaceEntryOnlyAppliesInThatWorkspace() {
        val list = SiteAllowlist().apply { update(listOf(entry("example.com", "personal"))) }

        assertThat(list.isAllowlisted("https://example.com/", "personal")).isTrue()
        assertThat(list.isAllowlisted("https://example.com/", "work")).isFalse()
        assertThat(list.isAllowlisted("https://example.com/", null)).isFalse()
    }

    @Test
    fun allWorkspacesEntryAppliesEverywhereIncludingUnknownWorkspace() {
        val list = SiteAllowlist().apply { update(listOf(entry("example.com", null))) }

        assertThat(list.isAllowlisted("https://example.com/", "personal")).isTrue()
        assertThat(list.isAllowlisted("https://example.com/", "work")).isTrue()
        assertThat(list.isAllowlisted("https://example.com/", null)).isTrue()
    }

    @Test
    fun updateReplacesTheSnapshot() {
        val list = SiteAllowlist().apply { update(listOf(entry("example.com", null))) }

        list.update(emptyList())

        assertThat(list.isAllowlisted("https://example.com/", "w")).isFalse()
    }

    @Test
    fun nonWebAndBlankUrlsAreNotAllowlisted() {
        val list = SiteAllowlist().apply { update(listOf(entry("example.com", null))) }

        assertThat(list.isAllowlisted(null, "w")).isFalse()
        assertThat(list.isAllowlisted("", "w")).isFalse()
        assertThat(list.isAllowlisted("about:blank", "w")).isFalse()
    }
}

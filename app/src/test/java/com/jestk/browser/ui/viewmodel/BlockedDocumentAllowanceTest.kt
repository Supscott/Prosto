package com.jestk.browser.ui.viewmodel

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class BlockedDocumentAllowanceTest {
    @Test
    fun allowance_appliesToTheHostInThatTabOnly() {
        val allowed = emptyMap<String, Set<String>>().withDocumentAllowed("tabA", "https://Ads.example/landing?x=1")

        assertThat(allowed.allowsDocument("tabA", "https://ads.example/other")).isTrue()
        assertThat(allowed.allowsDocument("tabA", "https://sub.ads.example/")).isFalse()
        assertThat(allowed.allowsDocument("tabB", "https://ads.example/landing")).isFalse()
    }

    @Test
    fun allowance_accumulatesHostsPerTab() {
        val allowed = emptyMap<String, Set<String>>()
            .withDocumentAllowed("tabA", "https://one.example/")
            .withDocumentAllowed("tabA", "https://two.example/")
            .withDocumentAllowed("tabB", "https://three.example/")

        assertThat(allowed["tabA"]).containsExactly("one.example", "two.example")
        assertThat(allowed["tabB"]).containsExactly("three.example")
    }

    @Test
    fun allowance_ignoresAnUrlWithoutAHost() {
        assertThat(emptyMap<String, Set<String>>().withDocumentAllowed("tabA", "")).isEmpty()
        assertThat(emptyMap<String, Set<String>>().allowsDocument("tabA", "")).isFalse()
    }
}

package com.jestk.browser.engine.adblock

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SessionPopupAllowlistTest {
    private val list = SessionPopupAllowlist(hostOf = { it.substringAfter("://").substringBefore('/') })

    @Test
    fun allowsOnlyTheAllowedHost() {
        list.allow("https://www.stream.example/watch?v=1")
        assertThat(list.allows("https://stream.example/other")).isTrue()
        assertThat(list.allows("https://other.example/")).isFalse()
    }

    @Test
    fun nullAndBlankAreNeverAllowed() {
        list.allow("https://stream.example/")
        assertThat(list.allows(null)).isFalse()
        assertThat(list.allows("")).isFalse()
    }

    @Test
    fun worksBehindThePopupSiteAllowlistInterface() {
        val gateAllowlist: PopupSiteAllowlist = list
        list.allow("https://stream.example/")
        assertThat(gateAllowlist.allows("https://stream.example/x")).isTrue()
    }
}

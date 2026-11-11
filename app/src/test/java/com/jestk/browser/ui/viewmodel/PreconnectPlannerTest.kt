package com.jestk.browser.ui.viewmodel

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PreconnectPlannerTest {
    private var clock = 0L
    private val planner = PreconnectPlanner(now = { clock }, minIntervalMs = 30_000L)
    private val search: (String) -> String = { "https://search.brave.com/search?q=$it" }

    @Test
    fun typedHost_preconnectsItsOrigin() {
        assertThat(planner.originToPreconnect("github.com/torvalds", search)).isEqualTo("https://github.com")
    }

    @Test
    fun searchQuery_preconnectsSearchEngine() {
        assertThat(planner.originToPreconnect("moon phases", search)).isEqualTo("https://search.brave.com")
    }

    @Test
    fun halfTypedOrLocalHost_isSkipped() {
        assertThat(planner.originToPreconnect("github.c", search)).isNull()
        assertThat(planner.originToPreconnect("localhost:8080", search)).isNull()
        assertThat(planner.originToPreconnect("192.168.1.1", search)).isNull()
        assertThat(planner.originToPreconnect("a", search)).isNull()
    }

    @Test
    fun explicitSchemeAndPort_areKept() {
        assertThat(planner.originToPreconnect("http://example.org:8443/x", search)).isEqualTo("http://example.org:8443")
    }

    @Test
    fun sameOrigin_isPreconnectedOncePerInterval() {
        assertThat(planner.originToPreconnect("moon", search)).isNotNull()
        clock = 10_000L
        assertThat(planner.originToPreconnect("moon phases", search)).isNull()
        clock = 40_001L
        assertThat(planner.originToPreconnect("moon phases today", search)).isNotNull()
    }
}

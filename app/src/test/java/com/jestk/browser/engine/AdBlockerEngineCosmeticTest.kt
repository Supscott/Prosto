package com.jestk.browser.engine

import com.google.common.truth.Truth.assertThat
import com.jestk.browser.engine.adblock.CosmeticResources
import com.jestk.browser.engine.adblock.EngineSet
import com.jestk.browser.engine.adblock.FakeContentMatcher
import org.junit.After
import org.junit.Before
import org.junit.Test

class AdBlockerEngineCosmeticTest {
    private val adsResources = CosmeticResources(
        hideSelectors = listOf(".ad", "#banner"),
        exceptions = emptySet(),
        procedural = emptyList(),
        generichide = false,
        scriptlets = listOf(listOf("set-constant", "a", "true")),
    )
    private val privacyResources = CosmeticResources(
        hideSelectors = listOf(".tracker-box"),
        exceptions = emptySet(),
        procedural = emptyList(),
        generichide = false,
        scriptlets = listOf(listOf("set-constant", "a", "true"), listOf("noop-fn", "b")),
    )

    @Before
    fun setUp() {
        AdBlockerEngine.resetForTests()
        AdBlockerEngine.engineStore.swap(
            EngineSet(
                ads = FakeContentMatcher(cosmeticByHost = mapOf("site.test" to adsResources)),
                privacy = FakeContentMatcher(cosmeticByHost = mapOf("site.test" to privacyResources)),
                rulesCount = 1,
            ),
        )
    }

    @After
    fun tearDown() {
        AdBlockerEngine.resetForTests()
    }

    @Test
    fun adsOnly_returnsTheAdsEngineResources() {
        val result = AdBlockerEngine.cosmeticResources("https://site.test/", adsEnabled = true, trackersEnabled = false)

        assertThat(result).isEqualTo(adsResources)
    }

    @Test
    fun trackersOn_mergesOnlyNewPrivacyScriptletsAndNeverPrivacyCss() {
        val result = AdBlockerEngine.cosmeticResources("https://site.test/", adsEnabled = true, trackersEnabled = true)

        assertThat(result.hideSelectors).containsExactly(".ad", "#banner").inOrder()
        assertThat(result.scriptlets).containsExactly(
            listOf("set-constant", "a", "true"),
            listOf("noop-fn", "b"),
        ).inOrder()
    }

    @Test
    fun trackersOnly_returnsPrivacyScriptletsWithoutCss() {
        val result = AdBlockerEngine.cosmeticResources("https://site.test/", adsEnabled = false, trackersEnabled = true)

        assertThat(result.hideSelectors).isEmpty()
        assertThat(result.scriptlets).hasSize(2)
    }

    @Test
    fun bothOff_isEmpty() {
        assertThat(AdBlockerEngine.cosmeticResources("https://site.test/", false, false)).isEqualTo(CosmeticResources.EMPTY)
    }

    @Test
    fun engineNotReady_isEmpty() {
        AdBlockerEngine.resetForTests()

        assertThat(AdBlockerEngine.cosmeticResources("https://site.test/", true, true)).isEqualTo(CosmeticResources.EMPTY)
    }
}

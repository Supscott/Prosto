package com.jestk.browser.engine

import com.google.common.truth.Truth.assertThat
import com.jestk.browser.engine.adblock.ContentBlockingBridge
import com.jestk.browser.engine.adblock.CosmeticResources
import org.json.JSONObject
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ContentBlockingBridgeTest {
    private val resources = CosmeticResources(
        hideSelectors = listOf(".ad", "#banner"),
        exceptions = setOf("keep"),
        procedural = emptyList(),
        generichide = true,
        scriptlets = listOf(listOf("set-constant", "a", "true")),
    )

    private class Seen(var calls: Int = 0, var ads: Boolean? = null, var trackers: Boolean? = null)

    private fun bridge(
        ads: () -> Boolean = { true },
        trackers: () -> Boolean = { true },
        seen: Seen = Seen(),
        result: CosmeticResources = resources,
    ) = ContentBlockingBridge(
        isEnabledProvider = ads,
        isTrackersEnabledProvider = trackers,
        cosmeticProvider = { _, a, t ->
            seen.calls++
            seen.ads = a
            seen.trackers = t
            result
        },
    )

    @Test
    fun cosmetic_returnsTheDocumentedShape() {
        val json = JSONObject(bridge().cosmetic("https://site.test/page"))

        assertThat(json.getJSONArray("css").getString(0)).isEqualTo(".ad,#banner{display:none!important}")
        assertThat(json.getBoolean("generichide")).isTrue()
        assertThat(json.getJSONArray("exceptions").getString(0)).isEqualTo("keep")
        assertThat(json.getJSONArray("scriptlets").getJSONArray(0).getString(0)).isEqualTo("set-constant")
    }

    @Test
    fun cosmetic_isEmptyWhenAdsAreOffWithoutAskingTheEngine() {
        val seen = Seen()

        assertThat(bridge(ads = { false }, seen = seen).cosmetic("https://site.test/")).isEqualTo("{}")
        assertThat(seen.calls).isEqualTo(0)
    }

    @Test
    fun cosmetic_isEmptyForChallengeAuthAndNonHttpUrls() {
        val seen = Seen()
        val b = bridge(seen = seen)

        assertThat(b.cosmetic("https://challenges.cloudflare.com/turnstile")).isEqualTo("{}")
        assertThat(b.cosmetic("https://www.recaptcha.net/recaptcha/api2")).isEqualTo("{}")
        assertThat(b.cosmetic("https://site.test/cdn-cgi/challenge-platform/h/g")).isEqualTo("{}")
        assertThat(b.cosmetic("about:blank")).isEqualTo("{}")
        assertThat(b.cosmetic("")).isEqualTo("{}")
        assertThat(b.cosmetic(null)).isEqualTo("{}")
        assertThat(seen.calls).isEqualTo(0)
    }

    @Test
    fun cosmetic_passesTheTrackerToggleToTheEngine() {
        val seen = Seen()

        bridge(trackers = { false }, seen = seen).cosmetic("https://site.test/")

        assertThat(seen.ads).isTrue()
        assertThat(seen.trackers).isFalse()
    }

    @Test
    fun cosmetic_passesScriptletsThroughEvenWithoutSelectors() {
        val onlyScriptlets = CosmeticResources(
            emptyList(), emptySet(), emptyList(), false,
            listOf(listOf("json-prune", "a b"), listOf("noop-fn")),
        )

        val scriptlets = JSONObject(bridge(result = onlyScriptlets).cosmetic("https://site.test/")).getJSONArray("scriptlets")

        assertThat(scriptlets.length()).isEqualTo(2)
        assertThat(scriptlets.getJSONArray(0).getString(1)).isEqualTo("a b")
        assertThat(scriptlets.getJSONArray(1).length()).isEqualTo(1)
    }

    @Test
    fun cosmetic_dropsScriptletsWhenAdsAreOffEvenIfTrackersAreOn() {
        val seen = Seen()

        val json = bridge(ads = { false }, trackers = { true }, seen = seen).cosmetic("https://site.test/")

        assertThat(json).isEqualTo("{}")
        assertThat(seen.calls).isEqualTo(0)
    }

    @Test
    fun cosmetic_isEmptyWhenTheEngineHasNothing() {
        assertThat(bridge(result = CosmeticResources.EMPTY).cosmetic("https://site.test/")).isEqualTo("{}")
    }

    @Test
    fun cosmetic_survivesAFailingEngine() {
        val b = ContentBlockingBridge(
            isEnabledProvider = { true },
            cosmeticProvider = { _, _, _ -> error("native failure") },
        )

        assertThat(b.cosmetic("https://site.test/")).isEqualTo("{}")
    }

    @Test
    fun cosmetic_splitsVeryLongSelectorLists() {
        val many = CosmeticResources((1..2000).map { ".selector-number-$it" }, emptySet(), emptyList(), false, emptyList())

        val css = JSONObject(bridge(result = many).cosmetic("https://site.test/")).getJSONArray("css")

        assertThat(css.length()).isGreaterThan(1)
        for (i in 0 until css.length()) {
            assertThat(css.getString(i)).endsWith("{display:none!important}")
        }
    }

    @Test
    fun isEnabled_followsTheAdsProviderWithoutStackOverflow() {
        var flag = true
        val b = ContentBlockingBridge { flag }

        assertThat(b.isEnabled()).isTrue()
        flag = false
        assertThat(b.isEnabled()).isFalse()
    }

    @Test
    fun openCleanPlayer_delegatesOnlyWhenEnabled() {
        var requested: String? = null
        var enabled = true
        val b = ContentBlockingBridge(
            isEnabledProvider = { true },
            onCleanPlayerRequested = { requested = it; true },
            isCleanPlayerEnabledProvider = { enabled },
        )

        assertThat(b.isCleanPlayerEnabled()).isTrue()
        assertThat(b.openCleanPlayer("https://m.youtube.com/watch?v=dQw4w9WgXcQ")).isTrue()
        assertThat(requested).isEqualTo("https://m.youtube.com/watch?v=dQw4w9WgXcQ")

        enabled = false
        requested = null
        assertThat(b.openCleanPlayer("https://m.youtube.com/watch?v=dQw4w9WgXcQ")).isFalse()
        assertThat(requested).isNull()
    }

    @Test
    fun orientationCalls_reachTheHandlers() {
        val calls = mutableListOf<String?>()
        val b = ContentBlockingBridge(
            isEnabledProvider = { true },
            onOrientationLockRequested = { calls += "lock:$it" },
            onOrientationUnlockRequested = { calls += "unlock" },
        )

        b.lockOrientation("landscape")
        b.unlockOrientation()

        assertThat(calls).containsExactly("lock:landscape", "unlock").inOrder()
    }
}

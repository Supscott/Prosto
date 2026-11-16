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
    fun cosmetic_withNothingSpecificStillAllowsGenericRules() {
        val json = JSONObject(bridge(result = CosmeticResources.EMPTY).cosmetic("https://site.test/"))

        assertThat(json.getBoolean("generichide")).isFalse()
        assertThat(json.getJSONArray("css").length()).isEqualTo(0)
    }

    @Test
    fun cosmetic_isEmptyForGenerichideWithNothingElse() {
        val none = CosmeticResources(emptyList(), emptySet(), emptyList(), true, emptyList())

        assertThat(bridge(result = none).cosmetic("https://site.test/")).isEqualTo("{}")
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

    private val hasTextFilter =
        """{"selector":[{"type":"css-selector","arg":".ad"},{"type":"has-text","arg":"/sponsored/i"}]}"""
    private val removeFilter = """{"selector":[{"type":"css-selector","arg":".b"}],"action":{"type":"remove"}}"""

    @Test
    fun cosmetic_passesProceduralFiltersThroughAsObjects() {
        val withProcedural = resources.copy(procedural = listOf(hasTextFilter, removeFilter))

        val procedural = JSONObject(bridge(result = withProcedural).cosmetic("https://site.test/")).getJSONArray("procedural")

        assertThat(procedural.length()).isEqualTo(2)
        val first = procedural.getJSONObject(0)
        assertThat(first.getJSONArray("selector").getJSONObject(1).getString("type")).isEqualTo("has-text")
        assertThat(first.getJSONArray("selector").getJSONObject(1).getString("arg")).isEqualTo("/sponsored/i")
        assertThat(first.has("action")).isFalse()
        assertThat(procedural.getJSONObject(1).getJSONObject("action").getString("type")).isEqualTo("remove")
    }

    @Test
    fun cosmetic_alwaysCarriesAProceduralArray() {
        val json = JSONObject(bridge().cosmetic("https://site.test/"))

        assertThat(json.getJSONArray("procedural").length()).isEqualTo(0)
    }

    @Test
    fun cosmetic_dropsMalformedProceduralEntries() {
        val broken = resources.copy(
            procedural = listOf("not json", "[]", """{"selector":[]}""", """{"selector":"x"}""", """{"action":null}""", removeFilter),
        )

        val procedural = JSONObject(bridge(result = broken).cosmetic("https://site.test/")).getJSONArray("procedural")

        assertThat(procedural.length()).isEqualTo(1)
    }

    @Test
    fun cosmetic_capsTheProceduralFiltersPerFrame() {
        val many = resources.copy(procedural = List(3000) { removeFilter })

        val procedural = JSONObject(bridge(result = many).cosmetic("https://site.test/")).getJSONArray("procedural")

        assertThat(procedural.length()).isEqualTo(1000)
    }

    @Test
    fun cosmetic_isNotEmptyForGenerichideWithOnlyProceduralFilters() {
        val onlyProcedural = CosmeticResources(emptyList(), emptySet(), listOf(removeFilter), true, emptyList())

        val json = JSONObject(bridge(result = onlyProcedural).cosmetic("https://site.test/"))

        assertThat(json.getBoolean("generichide")).isTrue()
        assertThat(json.getJSONArray("procedural").length()).isEqualTo(1)
    }

    @Test
    fun cosmetic_isEmptyForGenerichideWithOnlyMalformedProceduralFilters() {
        val none = CosmeticResources(emptyList(), emptySet(), listOf("nope"), true, emptyList())

        assertThat(bridge(result = none).cosmetic("https://site.test/")).isEqualTo("{}")
    }

    @Test
    fun cosmetic_sendsNoProceduralFiltersForChallengePagesOrWhenAdsAreOff() {
        val withProcedural = resources.copy(procedural = listOf(removeFilter))

        assertThat(bridge(result = withProcedural).cosmetic("https://challenges.cloudflare.com/turnstile")).isEqualTo("{}")
        assertThat(bridge(ads = { false }, result = withProcedural).cosmetic("https://site.test/")).isEqualTo("{}")
    }

    @Test
    fun cosmetic_sendsNoProceduralFiltersOnAllowlistedSites() {
        val b = ContentBlockingBridge(
            isEnabledProvider = { true },
            cosmeticProvider = { _, _, _ -> resources.copy(procedural = listOf(removeFilter)) },
            isSiteAllowlisted = { true },
        )

        assertThat(b.cosmetic("https://site.test/")).isEqualTo("{}")
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

    private class GenericSeen(
        var classes: List<String> = emptyList(),
        var ids: List<String> = emptyList(),
        var exceptions: Set<String> = emptySet(),
        var calls: Int = 0,
    )

    private fun genericBridge(
        seen: GenericSeen = GenericSeen(),
        ads: () -> Boolean = { true },
        cosmetic: CosmeticResources = CosmeticResources.EMPTY.copy(exceptions = setOf(".keep")),
        answer: (List<String>, List<String>) -> List<String> = { c, i -> c.map { ".$it" } + i.map { "#$it" } },
    ) = ContentBlockingBridge(
        isEnabledProvider = ads,
        cosmeticProvider = { _, _, _ -> cosmetic },
        genericProvider = { c, i, x ->
            seen.calls++
            seen.classes = c
            seen.ids = i
            seen.exceptions = x
            answer(c, i)
        },
    )

    @Test
    fun generic_returnsOneRulePerSelector() {
        val css = genericBridge().generic("https://site.test/", "ad-box promo", "top")

        assertThat(css.split("\n")).containsExactly(
            ".ad-box{display:none!important}",
            ".promo{display:none!important}",
            "#top{display:none!important}",
        ).inOrder()
    }

    @Test
    fun generic_usesTheExceptionsOfTheFramesCosmeticAnswer() {
        val seen = GenericSeen()
        val b = genericBridge(seen)

        b.cosmetic("https://site.test/")
        b.generic("https://site.test/", "keep", "")

        assertThat(seen.exceptions).containsExactly(".keep")
    }

    @Test
    fun generic_recomputesExceptionsForAFrameItNeverAnswered() {
        val seen = GenericSeen()

        genericBridge(seen).generic("https://other.test/", "a", "")

        assertThat(seen.exceptions).containsExactly(".keep")
    }

    @Test
    fun generic_isEmptyForGenerichideFrames() {
        val hide = CosmeticResources.EMPTY.copy(generichide = true)
        val seen = GenericSeen()

        assertThat(genericBridge(seen, cosmetic = hide).generic("https://site.test/", "a", "b")).isEmpty()
        assertThat(seen.calls).isEqualTo(0)
    }

    @Test
    fun generic_isEmptyWhenAdsAreOffOrForChallengeAuthAndNonHttpFrames() {
        val seen = GenericSeen()
        val b = genericBridge(seen)

        assertThat(genericBridge(seen, ads = { false }).generic("https://site.test/", "a", "")).isEmpty()
        assertThat(b.generic("https://challenges.cloudflare.com/x", "a", "")).isEmpty()
        assertThat(b.generic("about:blank", "a", "")).isEmpty()
        assertThat(b.generic(null, "a", "")).isEmpty()
        assertThat(seen.calls).isEqualTo(0)
    }

    @Test
    fun generic_isEmptyForEmptyAndBlankInput() {
        val seen = GenericSeen()

        assertThat(genericBridge(seen).generic("https://site.test/", "", "  ")).isEmpty()
        assertThat(genericBridge(seen).generic("https://site.test/", null, null)).isEmpty()
        assertThat(seen.calls).isEqualTo(0)
    }

    @Test
    fun generic_truncatesHugeInputWithoutPassingAPartialToken() {
        val seen = GenericSeen()
        val huge = (1..50_000).joinToString(" ") { "class-name-number-$it" }

        genericBridge(seen).generic("https://site.test/", huge, "")

        assertThat(seen.classes.size).isAtMost(1_000)
        assertThat(seen.classes.all { Regex("class-name-number-\\d+").matches(it) }).isTrue()
        assertThat(seen.classes.first()).isEqualTo("class-name-number-1")
    }

    @Test
    fun generic_truncatesAnOverlongInputAtATokenBoundary() {
        val seen = GenericSeen()
        val huge = "a".repeat(99) + " " + (1..2_000).joinToString(" ") { "tok-$it-" + "z".repeat(40) }

        genericBridge(seen).generic("https://site.test/", huge, "")

        assertThat(seen.classes.last()).matches("tok-\\d+-z{40}")
    }

    @Test
    fun generic_dropsOverlongTokensAndDuplicates() {
        val seen = GenericSeen()

        genericBridge(seen).generic("https://site.test/", "a a " + "x".repeat(101) + " b", "")

        assertThat(seen.classes).containsExactly("a", "b").inOrder()
    }

    @Test
    fun generic_neverEmitsASelectorThatCouldEscapeItsRule() {
        val css = genericBridge(answer = { _, _ -> listOf(".ok", ".a}body{display:none", ".b\n.c") })
            .generic("https://site.test/", "ok", "")

        assertThat(css).isEqualTo(".ok{display:none!important}")
    }

    @Test
    fun generic_survivesAFailingEngine() {
        val css = genericBridge(answer = { _, _ -> error("native failure") }).generic("https://site.test/", "a", "")

        assertThat(css).isEmpty()
    }

    @Test
    fun cosmetic_isEmptyOnAllowlistedSitesWithoutAskingTheEngine() {
        val seen = Seen()
        val b = ContentBlockingBridge(
            isEnabledProvider = { true },
            cosmeticProvider = { _, _, _ ->
                seen.calls++
                resources
            },
            isSiteAllowlisted = { it.contains("allowed.test") },
        )

        assertThat(b.cosmetic("https://allowed.test/page")).isEqualTo("{}")
        assertThat(seen.calls).isEqualTo(0)
        assertThat(b.cosmetic("https://site.test/page")).isNotEqualTo("{}")
        assertThat(seen.calls).isEqualTo(1)
    }

    @Test
    fun cosmetic_aFailingAllowlistLookupFallsBackToBlocking() {
        val b = ContentBlockingBridge(
            isEnabledProvider = { true },
            cosmeticProvider = { _, _, _ -> resources },
            isSiteAllowlisted = { error("boom") },
        )

        assertThat(b.cosmetic("https://site.test/page")).isNotEqualTo("{}")
    }

    @Test
    fun generic_isEmptyOnAllowlistedSites() {
        var asked = 0
        val b = ContentBlockingBridge(
            isEnabledProvider = { true },
            cosmeticProvider = { _, _, _ -> CosmeticResources.EMPTY },
            genericProvider = { _, _, _ ->
                asked++
                listOf(".ad")
            },
            isSiteAllowlisted = { it.contains("allowed.test") },
        )

        assertThat(b.generic("https://allowed.test/", "ad", "")).isEmpty()
        assertThat(asked).isEqualTo(0)
        assertThat(b.generic("https://site.test/", "ad", "")).isNotEmpty()
    }
}

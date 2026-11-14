package com.jestk.browser.engine.adblock

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class CosmeticResourcesTest {
    @Test fun fullJson() {
        val r = CosmeticResources.parse(
            """{"hide":[".ad","#b"],"exceptions":["x"],"procedural":["p"],"generichide":true,
            "scriptlets":[["json-prune","a"],["abort-on-property-read","b","c"]]}""",
        )
        assertEquals(listOf(".ad", "#b"), r.hideSelectors)
        assertEquals(setOf("x"), r.exceptions)
        assertEquals(listOf("p"), r.procedural)
        assertTrue(r.generichide)
        assertEquals(listOf(listOf("json-prune", "a"), listOf("abort-on-property-read", "b", "c")), r.scriptlets)
    }

    @Test fun partialJson() {
        val r = CosmeticResources.parse("""{"hide":[".ad"]}""")
        assertEquals(CosmeticResources.EMPTY.copy(hideSelectors = listOf(".ad")), r)
    }

    @Test fun emptyObject() = assertEquals(CosmeticResources.EMPTY, CosmeticResources.parse("{}"))

    @Test fun malformed() {
        assertEquals(CosmeticResources.EMPTY, CosmeticResources.parse("not json"))
        assertEquals(CosmeticResources.EMPTY, CosmeticResources.parse(""))
        assertEquals(CosmeticResources.EMPTY, CosmeticResources.parse("[1,2]"))
    }
}

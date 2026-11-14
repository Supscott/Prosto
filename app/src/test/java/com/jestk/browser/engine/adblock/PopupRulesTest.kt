package com.jestk.browser.engine.adblock

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PopupRulesTest {
    @Test
    fun hostRuleBecomesADocumentRule() {
        assertThat(PopupRules.rewrite("||ads.example^\$popup")).isEqualTo("||ads.example^\$document")
    }

    @Test
    fun otherOptionsAreKeptAndTypeOptionsDropped() {
        assertThat(PopupRules.rewrite("||ads.example^\$popup,third-party,domain=a.com|b.com"))
            .isEqualTo("||ads.example^\$document,third-party,domain=a.com|b.com")
        assertThat(PopupRules.rewrite("||ads.example^\$script,popup,1p")).isEqualTo("||ads.example^\$document,1p")
    }

    @Test
    fun pathRulesAndExceptionsAreRewrittenToo() {
        assertThat(PopupRules.rewrite("||site.test^*/go.php\$popup")).isEqualTo("||site.test^*/go.php\$document")
        assertThat(PopupRules.rewrite("@@||ok.example^\$popup")).isEqualTo("@@||ok.example^\$document")
    }

    @Test
    fun nonPopupLinesAndUnsupportedModifiersAreLeftOut() {
        for (line in listOf(
            "||ads.example^",
            "||ads.example^\$script",
            "||ads.example^\$~popup,third-party",
            "example.com##.ad",
            "! comment \$popup",
            "[Adblock Plus 2.0]",
            "||ads.example^\$popup,redirect=noopjs",
            "",
        )) {
            assertThat(PopupRules.rewrite(line)).isNull()
        }
    }

    @Test
    fun extractJoinsThePopupRulesOfAllListsIntoOneList() {
        val lists = listOf("||a.example^\$popup\n||b.example^\n", "! x\n@@||c.example^\$popup,domain=d.com\n")
        assertThat(PopupRules.extract(lists)).containsExactly("||a.example^\$document\n@@||c.example^\$document,domain=d.com\n")
        assertThat(PopupRules.extract(listOf("||b.example^"))).isEmpty()
    }
}

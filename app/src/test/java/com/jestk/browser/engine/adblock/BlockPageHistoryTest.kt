package com.jestk.browser.engine.adblock

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class BlockPageHistoryTest {
    private val block = "Page blocked${BlockPage.TITLE_MARKER}"

    @Test
    fun backSteps_isOneWhenTheEntryBeforeIsARealPage() {
        assertThat(BlockPageHistory.backSteps(listOf("A", "B"), currentIndex = 1)).isEqualTo(-1)
    }

    @Test
    fun backSteps_stepsOverBlockPageEntries() {
        assertThat(BlockPageHistory.backSteps(listOf("A", block, "C"), currentIndex = 2)).isEqualTo(-2)
        assertThat(BlockPageHistory.backSteps(listOf("A", block, block, "D"), currentIndex = 3)).isEqualTo(-3)
    }

    @Test
    fun backSteps_leavesTheBlockPageItselfToTheEntryBeforeIt() {
        // Go back from the block page lands on the page the user came from.
        assertThat(BlockPageHistory.backSteps(listOf("A", block), currentIndex = 1)).isEqualTo(-1)
        assertThat(BlockPageHistory.backSteps(listOf("A", block, block), currentIndex = 2)).isEqualTo(-2)
    }

    @Test
    fun backSteps_isNullWhenOnlyBlockPagesOrNothingIsBehind() {
        assertThat(BlockPageHistory.backSteps(listOf("A"), currentIndex = 0)).isNull()
        assertThat(BlockPageHistory.backSteps(listOf(block, "B"), currentIndex = 1)).isNull()
        assertThat(BlockPageHistory.backSteps(listOf(block, block), currentIndex = 1)).isNull()
    }

    @Test
    fun backSteps_ignoresEntriesAheadOfTheCurrentOne() {
        assertThat(BlockPageHistory.backSteps(listOf("A", "B", block), currentIndex = 1)).isEqualTo(-1)
        assertThat(BlockPageHistory.backSteps(listOf(block, "B", "C"), currentIndex = 1)).isNull()
    }

    @Test
    fun backSteps_treatsEntriesWithoutTitleAsRealPages() {
        assertThat(BlockPageHistory.backSteps(listOf(null, block), currentIndex = 1)).isEqualTo(-1)
    }
}

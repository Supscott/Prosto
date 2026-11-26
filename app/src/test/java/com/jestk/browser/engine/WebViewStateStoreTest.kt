package com.jestk.browser.engine

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class WebViewStateStoreTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @After
    fun clearStore() {
        WebViewStateStore.deleteAll()
    }

    @Test
    fun writtenState_readsBackByteForByte() {
        val file = folder.newFile("tab.state")
        val entries = mapOf("WEBVIEW_CHROMIUM_STATE" to ByteArray(2048) { (it % 251).toByte() })

        WebViewStateStore.write(file, entries)
        val read = WebViewStateStore.readEntries(file)

        assertThat(read?.keys).containsExactly("WEBVIEW_CHROMIUM_STATE")
        assertThat(read!!.getValue("WEBVIEW_CHROMIUM_STATE")).isEqualTo(entries.getValue("WEBVIEW_CHROMIUM_STATE"))
    }

    @Test
    fun restore_doesNotReloadByItselfAndLeavesTheMarkToTheCaller() {
        WebViewStateStore.save("t", FakeHistoryWebView(context))
        WebViewStateStore.markReloadPending("t")
        val fresh = FakeHistoryWebView(context)

        assertThat(WebViewStateStore.restore("t", fresh)).isTrue()

        assertThat(fresh.reloads).isEqualTo(0)
        assertThat(WebViewStateStore.takeReloadPending("t")).isTrue()
        assertThat(WebViewStateStore.takeReloadPending("t")).isFalse()
    }

    @Test
    fun deleteDropsOneMarkAndKeepsTheOthers() {
        WebViewStateStore.markReloadPending("a")
        WebViewStateStore.markReloadPending("b")

        WebViewStateStore.delete("a")

        assertThat(WebViewStateStore.takeReloadPending("a")).isFalse()
        assertThat(WebViewStateStore.takeReloadPending("b")).isTrue()
    }

    @Test
    fun deleteAllDropsEveryMark() {
        WebViewStateStore.markReloadPending("a")
        WebViewStateStore.markReloadPending("b")

        WebViewStateStore.deleteAll()

        assertThat(WebViewStateStore.takeReloadPending("a")).isFalse()
        assertThat(WebViewStateStore.takeReloadPending("b")).isFalse()
    }

    @Test
    fun retainOnlyDropsMarksOfTabsThatAreGone() {
        WebViewStateStore.markReloadPending("kept")
        WebViewStateStore.markReloadPending("gone")

        WebViewStateStore.retainOnly(setOf("kept"))

        assertThat(WebViewStateStore.takeReloadPending("gone")).isFalse()
        assertThat(WebViewStateStore.takeReloadPending("kept")).isTrue()
    }

    @Test
    fun moveCarriesTheMarkToTheReopenedTab() {
        WebViewStateStore.save("old", FakeHistoryWebView(context))
        WebViewStateStore.markReloadPending("old")

        WebViewStateStore.move("old", "new")

        assertThat(WebViewStateStore.takeReloadPending("old")).isFalse()
        assertThat(WebViewStateStore.takeReloadPending("new")).isTrue()
    }

    @Test
    fun moveCarriesTheMarkEvenWithoutSavedState() {
        WebViewStateStore.markReloadPending("old")

        WebViewStateStore.move("old", "new")

        assertThat(WebViewStateStore.takeReloadPending("new")).isTrue()
    }

    @Test
    fun truncatedOrForeignFile_isRejected() {
        val file = folder.newFile("tab.state")
        WebViewStateStore.write(file, mapOf("k" to ByteArray(100)))
        file.writeBytes(file.readBytes().copyOf(20))
        assertThat(WebViewStateStore.readEntries(file)).isNull()

        file.writeText("not a state file")
        assertThat(WebViewStateStore.readEntries(file)).isNull()
    }
}

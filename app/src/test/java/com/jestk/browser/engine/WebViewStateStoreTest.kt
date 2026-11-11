package com.jestk.browser.engine

import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class WebViewStateStoreTest {
    @get:Rule
    val folder = TemporaryFolder()

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
    fun truncatedOrForeignFile_isRejected() {
        val file = folder.newFile("tab.state")
        WebViewStateStore.write(file, mapOf("k" to ByteArray(100)))
        file.writeBytes(file.readBytes().copyOf(20))
        assertThat(WebViewStateStore.readEntries(file)).isNull()

        file.writeText("not a state file")
        assertThat(WebViewStateStore.readEntries(file)).isNull()
    }
}

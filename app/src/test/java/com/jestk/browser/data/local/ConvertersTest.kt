package com.jestk.browser.data.local

import android.net.Uri
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.Date

@RunWith(RobolectricTestRunner::class)
class ConvertersTest {

    private val converters = Converters()

    @Test
    fun dateConverters_bidirectionalFidelity() {
        val now = Date()
        val timestamp = converters.dateToTimestamp(now)
        val reconstructed = converters.fromTimestamp(timestamp)

        assertThat(timestamp).isEqualTo(now.time)
        assertThat(reconstructed).isEqualTo(now)
        assertThat(converters.dateToTimestamp(null)).isNull()
        assertThat(converters.fromTimestamp(null)).isNull()
    }

    @Test
    fun uriConverters_bidirectionalFidelity() {
        val uri = Uri.parse("https://jestk.com/search?q=android")
        val string = converters.uriToString(uri)
        val reconstructed = converters.fromUriString(string)

        assertThat(string).isEqualTo("https://jestk.com/search?q=android")
        assertThat(reconstructed).isEqualTo(uri)
        assertThat(converters.uriToString(null)).isNull()
        assertThat(converters.fromUriString(null)).isNull()
    }

    @Test
    fun stringListConverters_bidirectionalFidelity() {
        val list = listOf("alpha", "beta", "gamma")
        val serialized = converters.stringListToString(list)
        val deserialized = converters.fromStringList(serialized)

        assertThat(serialized).isEqualTo("alpha|||beta|||gamma")
        assertThat(deserialized).containsExactly("alpha", "beta", "gamma").inOrder()
        assertThat(converters.stringListToString(null)).isNull()
        assertThat(converters.fromStringList(null)).isEmpty()
    }
}

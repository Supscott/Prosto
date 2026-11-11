package com.jestk.browser.engine

import android.content.ComponentCallbacks2
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class WebViewPoolSizingTest {
    private val gib = 1024L * 1024L * 1024L

    @Test
    fun poolSize_followsDeviceMemory() {
        assertThat(WebViewPoolSizing.maxWebViews(8 * gib, isLowRamDevice = true)).isEqualTo(2)
        assertThat(WebViewPoolSizing.maxWebViews(2 * gib, isLowRamDevice = false)).isEqualTo(3)
        assertThat(WebViewPoolSizing.maxWebViews(4 * gib, isLowRamDevice = false)).isEqualTo(5)
        assertThat(WebViewPoolSizing.maxWebViews(12 * gib, isLowRamDevice = false)).isEqualTo(8)
        assertThat(WebViewPoolSizing.maxWebViews(0L, isLowRamDevice = false)).isEqualTo(WebViewPoolSizing.DEFAULT_MAX_WEBVIEWS)
    }

    @Test
    fun goingToBackground_isNotMemoryPressure() {
        assertThat(WebViewPoolSizing.isMemoryPressure(ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN)).isFalse()
        assertThat(WebViewPoolSizing.isMemoryPressure(ComponentCallbacks2.TRIM_MEMORY_BACKGROUND)).isFalse()
        assertThat(WebViewPoolSizing.isMemoryPressure(ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW)).isTrue()
        assertThat(WebViewPoolSizing.isMemoryPressure(ComponentCallbacks2.TRIM_MEMORY_COMPLETE)).isTrue()
    }
}

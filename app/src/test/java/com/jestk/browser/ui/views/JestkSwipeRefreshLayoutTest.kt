package com.jestk.browser.ui.views

import android.content.Context
import android.widget.FrameLayout
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class JestkSwipeRefreshLayoutTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun tabsContainer_isFoundWhileTheSpinnerCircleIsChildZero() {
        val layout = JestkSwipeRefreshLayout(context)
        val container = FrameLayout(context)
        layout.addView(container)

        // The trap behind "the dock switches tabs but the page stays": index 0 is the circle.
        assertThat(layout.getChildAt(0)).isNotInstanceOf(FrameLayout::class.java)
        assertThat(layout.tabsContainer).isSameInstanceAs(container)
    }

    @Test
    fun tabsContainer_survivesTheSpinnerReordering() {
        val layout = JestkSwipeRefreshLayout(context)
        val container = FrameLayout(context)
        layout.addView(container)

        layout.isRefreshing = true
        layout.isRefreshing = false

        assertThat(layout.tabsContainer).isSameInstanceAs(container)
    }
}

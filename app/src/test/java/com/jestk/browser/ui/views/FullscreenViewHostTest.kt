package com.jestk.browser.ui.views

import android.content.Context
import android.view.View
import android.widget.FrameLayout
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class FullscreenViewHostTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun attach_movesViewFromPreviousParentIntoContainer() {
        val host = FullscreenViewHost(context)
        val oldParent = FrameLayout(context)
        val view = View(context)
        oldParent.addView(view)

        host.attach(view)

        assertThat(view.parent).isSameInstanceAs(host.container)
        assertThat(oldParent.childCount).isEqualTo(0)
        assertThat(view.keepScreenOn).isTrue()
    }

    @Test
    fun attach_sameViewTwice_keepsSingleChild() {
        val host = FullscreenViewHost(context)
        val view = View(context)

        host.attach(view)
        host.attach(view)

        assertThat(host.container.childCount).isEqualTo(1)
        assertThat(host.container.getChildAt(0)).isSameInstanceAs(view)
    }

    @Test
    fun attach_newView_replacesPreviousOne() {
        val host = FullscreenViewHost(context)
        val first = View(context)
        val second = View(context)

        host.attach(first)
        host.attach(second)

        assertThat(host.container.childCount).isEqualTo(1)
        assertThat(first.parent).isNull()
        assertThat(second.parent).isSameInstanceAs(host.container)
    }

    @Test
    fun detach_removesView() {
        val host = FullscreenViewHost(context)
        val view = View(context)
        host.attach(view)

        host.detach()

        assertThat(host.container.childCount).isEqualTo(0)
        assertThat(view.parent).isNull()
    }
}

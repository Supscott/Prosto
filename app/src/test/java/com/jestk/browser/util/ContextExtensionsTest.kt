package com.jestk.browser.util

import android.app.Activity
import android.content.ContextWrapper
import androidx.activity.ComponentActivity
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ContextExtensionsTest {

    @Test
    fun findActivity_withDirectActivity_returnsActivity() {
        val controller = Robolectric.buildActivity(ComponentActivity::class.java).setup()
        val activity = controller.get()

        assertThat(activity.findActivity()).isSameInstanceAs(activity)
    }

    @Test
    fun findActivity_withNestedContextWrapper_returnsActivity() {
        val controller = Robolectric.buildActivity(ComponentActivity::class.java).setup()
        val activity = controller.get()

        val wrapperLevel1 = ContextWrapper(activity)
        val wrapperLevel2 = ContextWrapper(wrapperLevel1)

        assertThat(wrapperLevel2.findActivity()).isSameInstanceAs(activity)
    }

    @Test
    fun findActivity_withApplicationContext_returnsNull() {
        val appContext = ApplicationProvider.getApplicationContext<android.content.Context>()
        assertThat(appContext.findActivity()).isNull()
    }
}

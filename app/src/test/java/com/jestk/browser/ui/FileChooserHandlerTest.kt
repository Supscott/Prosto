package com.jestk.browser.ui

import android.app.Activity
import android.net.Uri
import android.webkit.ValueCallback
import com.google.common.truth.Truth.assertThat
import com.jestk.browser.util.findActivity
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/**
 * Covers the [FileChooserHandler] abstraction that replaced the concrete
 * `MainActivity` downcast in the WebView file chooser path.
 */
@RunWith(RobolectricTestRunner::class)
class FileChooserHandlerTest {

    @Test
    fun mainActivity_implementsFileChooserHandler() {
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()

        assertThat(activity).isInstanceOf(FileChooserHandler::class.java)
    }

    @Test
    fun findActivity_resolvesFileChooserHandlerFromHostActivity() {
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()

        val handler = activity.findActivity() as? FileChooserHandler

        assertThat(handler).isNotNull()
    }

    @Test
    fun findActivity_resolvesNoHandlerForActivityWithoutFileChooserSupport() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()

        val handler = activity.findActivity() as? FileChooserHandler

        assertThat(handler).isNull()
    }

    @Test
    fun openFileChooser_viaInterface_launchesPicker() {
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        val handler = activity.findActivity() as? FileChooserHandler

        var received: Array<Uri>? = arrayOf(Uri.parse("file://dummy"))
        val callback = ValueCallback<Array<Uri>> { uris -> received = uris }

        val launched = handler!!.openFileChooser(callback, null)

        assertThat(launched).isTrue()
        // The callback is only invoked once the system picker returns a result.
        assertThat(received).isNotNull()
        assertThat(shadowOf(activity).nextStartedActivityForResult).isNotNull()
    }
}

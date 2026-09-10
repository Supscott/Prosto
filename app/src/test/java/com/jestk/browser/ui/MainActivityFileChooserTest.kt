package com.jestk.browser.ui

import android.content.Intent
import android.net.Uri
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class MainActivityFileChooserTest {

    @Test
    fun openFileChooser_withNullCallback_returnsFalse() {
        val activityController = Robolectric.buildActivity(MainActivity::class.java).setup()
        val activity = activityController.get()

        val result = activity.openFileChooser(null, null)
        assertThat(result).isFalse()
    }

    @Test
    fun openFileChooser_cancelsPreviousCallback() {
        val activityController = Robolectric.buildActivity(MainActivity::class.java).setup()
        val activity = activityController.get()

        var prevResult: Array<Uri>? = arrayOf(Uri.parse("file://dummy"))
        val prevCallback = ValueCallback<Array<Uri>> { uris ->
            prevResult = uris
        }

        val newCallback = ValueCallback<Array<Uri>> { }

        activity.openFileChooser(prevCallback, null)
        // Opening second chooser should cancel the first callback
        activity.openFileChooser(newCallback, null)

        assertThat(prevResult).isNull()
    }

    @Test
    fun openFileChooser_launchesIntent() {
        val activityController = Robolectric.buildActivity(MainActivity::class.java).setup()
        val activity = activityController.get()

        val callback = ValueCallback<Array<Uri>> { }
        val launched = activity.openFileChooser(callback, null)

        assertThat(launched).isTrue()
        val nextIntent = shadowOf(activity).nextStartedActivityForResult
        assertThat(nextIntent).isNotNull()
        assertThat(nextIntent.intent.action).isEqualTo(Intent.ACTION_GET_CONTENT)
    }

    @Test
    fun openFileChooser_customParams_launchesCustomIntent() {
        val activityController = Robolectric.buildActivity(MainActivity::class.java).setup()
        val activity = activityController.get()

        val callback = ValueCallback<Array<Uri>> { }
        val params = object : WebChromeClient.FileChooserParams() {
            override fun getMode(): Int = MODE_OPEN
            override fun getAcceptTypes(): Array<String> = arrayOf("image/png")
            override fun isCaptureEnabled(): Boolean = false
            override fun getTitle(): CharSequence = "Select image"
            override fun getFilenameHint(): String? = null
            override fun createIntent(): Intent = Intent(Intent.ACTION_GET_CONTENT).apply {
                type = "image/png"
            }
        }

        val launched = activity.openFileChooser(callback, params)

        assertThat(launched).isTrue()
        val nextIntent = shadowOf(activity).nextStartedActivityForResult
        assertThat(nextIntent).isNotNull()
        assertThat(nextIntent.intent.type).isEqualTo("image/png")
    }

    @Test
    fun onDestroy_cancelsPendingCallback() {
        val activityController = Robolectric.buildActivity(MainActivity::class.java).setup()
        val activity = activityController.get()

        var callbackResult: Array<Uri>? = arrayOf(Uri.parse("file://dummy"))
        val callback = ValueCallback<Array<Uri>> { uris ->
            callbackResult = uris
        }

        activity.openFileChooser(callback, null)
        activityController.destroy()

        assertThat(callbackResult).isNull()
    }
}

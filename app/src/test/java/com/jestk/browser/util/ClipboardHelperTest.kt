package com.jestk.browser.util

import android.content.ClipboardManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.shadows.ShadowToast

@RunWith(RobolectricTestRunner::class)
class ClipboardHelperTest {

    @Test
    fun copyToClipboard_copiesTextAndShowsDefaultToast() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        ShadowToast.reset()

        ClipboardHelper.copyToClipboard(
            context = context,
            text = "https://example.com",
            label = "URL"
        )

        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = clipboard.primaryClip
        assertThat(clip).isNotNull()
        assertThat(clip?.itemCount).isEqualTo(1)
        assertThat(clip?.getItemAt(0)?.text?.toString()).isEqualTo("https://example.com")
        assertThat(clip?.description?.label?.toString()).isEqualTo("URL")
        assertThat(ShadowToast.getTextOfLatestToast()).isEqualTo("URL copied to clipboard")
    }

    @Test
    fun copyToClipboard_withCustomToastMessage_showsCustomToast() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        ShadowToast.reset()

        ClipboardHelper.copyToClipboard(
            context = context,
            text = "https://example.com/image.png",
            label = "Image URL",
            toastMessage = "Custom toast message"
        )

        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        assertThat(clipboard.primaryClip?.getItemAt(0)?.text?.toString()).isEqualTo("https://example.com/image.png")
        assertThat(ShadowToast.getTextOfLatestToast()).isEqualTo("Custom toast message")
    }

    @Test
    fun copyToClipboard_whenShowToastFalse_doesNotShowToast() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        ShadowToast.reset()

        ClipboardHelper.copyToClipboard(
            context = context,
            text = "secret_text",
            showToast = false
        )

        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        assertThat(clipboard.primaryClip?.getItemAt(0)?.text?.toString()).isEqualTo("secret_text")
        assertThat(ShadowToast.getLatestToast()).isNull()
    }
}

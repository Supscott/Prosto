package com.jestk.browser.engine

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.WebView
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SpareWebViewTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun waitingSpare_handlesRendererDeathAndIsDropped() {
        // The unused spare already shares the app's renderer: an unhandled death kills the app
        // (seen on device with CDP Page.crash).
        val spareWebView = SpareWebView { ctx, _ -> WebView(ctx) }
        spareWebView.schedule(context, null)
        // The spare is built in an IdleHandler, which runs once the looper drains a message.
        Handler(Looper.getMainLooper()).post {}
        shadowOf(Looper.getMainLooper()).idle()
        val waiting = peekSpare(spareWebView)!!

        val handled = shadowOf(waiting).webViewClient.onRenderProcessGone(waiting, null)

        assertThat(handled).isTrue()
        assertThat(peekSpare(spareWebView)).isNull()
        assertThat(spareWebView.take(context, null)).isNull()
    }

    private fun peekSpare(spareWebView: SpareWebView): WebView? =
        SpareWebView::class.java.getDeclaredField("spare").apply { isAccessible = true }.get(spareWebView) as WebView?
}

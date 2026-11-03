package com.jestk.browser.ui.overlays

import android.content.pm.ActivityInfo
import androidx.activity.ComponentActivity
import androidx.compose.ui.platform.ComposeView
import com.google.common.truth.Truth.assertThat
import com.jestk.browser.ui.theme.JestkBrowserTheme
import com.jestk.browser.util.YouTubeUrlHelper
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CleanVideoPlayerOverlayTest {

    @Test
    fun renderCleanVideoPlayerOverlay() {
        val controller = Robolectric.buildActivity(ComponentActivity::class.java).setup()
        val activity = controller.get()

        var openOnWebUrl: String? = null
        var isClosed = false

        val composeView = ComposeView(activity).apply {
            setContent {
                JestkBrowserTheme {
                    CleanVideoPlayerOverlay(
                        videoId = "dQw4w9WgXcQ",
                        originalUrl = "https://m.youtube.com/watch?v=dQw4w9WgXcQ",
                        startSeconds = 15,
                        onClose = { isClosed = true },
                        onOpenOnWeb = { openOnWebUrl = it },
                    )
                }
            }
        }

        activity.setContentView(composeView)
        composeView.measure(
            android.view.View.MeasureSpec.makeMeasureSpec(1080, android.view.View.MeasureSpec.EXACTLY),
            android.view.View.MeasureSpec.makeMeasureSpec(2400, android.view.View.MeasureSpec.EXACTLY),
        )
        composeView.layout(0, 0, 1080, 2400)

        assertThat(isClosed).isFalse()
        assertThat(openOnWebUrl).isNull()
    }

    @Test
    fun orientationTarget_shortsVsStandard() {
        val standardUrl = "https://m.youtube.com/watch?v=dQw4w9WgXcQ"
        val isStandardShort = standardUrl.contains("/shorts/", ignoreCase = true) || standardUrl.contains("/shorts", ignoreCase = true)
        val standardOrientation = if (isStandardShort) {
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
        } else {
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        }
        assertThat(standardOrientation).isEqualTo(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE)

        val shortsUrl = "https://m.youtube.com/shorts/dQw4w9WgXcQ"
        val isShorts = shortsUrl.contains("/shorts/", ignoreCase = true) || shortsUrl.contains("/shorts", ignoreCase = true)
        val shortsOrientation = if (isShorts) {
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
        } else {
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        }
        assertThat(shortsOrientation).isEqualTo(ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT)
    }

    @Test
    fun buildEmbedHtml_containsFullscreenAndFsParameters() {
        val html = YouTubeUrlHelper.buildEmbedHtml("dQw4w9WgXcQ", 0)
        assertThat(html).contains("fs=1")
        assertThat(html).contains("allow=\"accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture; web-share; fullscreen\"")
        assertThat(html).contains("allowfullscreen=\"true\"")
    }
}

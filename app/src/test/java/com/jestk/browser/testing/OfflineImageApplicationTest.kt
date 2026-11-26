package com.jestk.browser.testing

import android.app.Application
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import coil.Coil
import coil.request.ImageRequest
import coil.request.ImageResult
import coil.request.SuccessResult
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import java.time.Duration

/**
 * Fails when a Robolectric test's default Coil loader is the real one: the `.invalid` host can only
 * load through [FakeFaviconFetcher], so a real network fetch would end in an `ErrorResult` (PRO-178).
 */
@RunWith(RobolectricTestRunner::class)
class OfflineImageApplicationTest {
    @Test
    fun defaultCoilLoaderServesHttpImagesOffline() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        assertTrue("test Application is ${app::class.java.name}", app is OfflineImageApplication)
        var result: ImageResult? = null
        val request =
            ImageRequest.Builder(app)
                .data("https://favicons.invalid/s2/favicons?domain=example.com")
                .listener(
                    onSuccess = { _, success -> result = success },
                    onError = { _, error -> result = error },
                )
                .build()
        Coil.imageLoader(app).enqueue(request)
        // Coil finishes on the main dispatcher, which is this thread: pump the looper instead of blocking.
        val deadline = System.nanoTime() + 10_000_000_000L
        while (result == null && System.nanoTime() < deadline) {
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(50))
            Thread.sleep(10)
        }
        assertTrue("expected the fake fetcher to serve the image, got $result", result is SuccessResult)
    }
}

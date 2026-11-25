package com.jestk.browser.testing

import android.content.Context
import android.os.Looper
import android.view.View
import coil.Coil
import coil.EventListener
import coil.ImageLoader
import coil.decode.SvgDecoder
import coil.request.ErrorResult
import coil.request.ImageRequest
import coil.request.SuccessResult
import org.robolectric.Shadows.shadowOf
import java.time.Duration
import java.util.concurrent.atomic.AtomicInteger

/**
 * Makes Coil loads deterministic in Robolectric preview tests. Coil decodes on background
 * dispatchers, so a capture taken right after composition can land before or after an
 * `AsyncImage` finishes (PRO-144). [install] swaps the singleton loader for one that counts
 * in-flight requests; [awaitIdle] pumps the main looper until none is left.
 */
class CoilIdleTracker private constructor() {
    private val started = AtomicInteger(0)
    private val finished = AtomicInteger(0)

    private val listener =
        object : EventListener {
            override fun onStart(request: ImageRequest) {
                started.incrementAndGet()
            }

            override fun onCancel(request: ImageRequest) {
                finished.incrementAndGet()
            }

            override fun onSuccess(request: ImageRequest, result: SuccessResult) {
                finished.incrementAndGet()
            }

            override fun onError(request: ImageRequest, result: ErrorResult) {
                finished.incrementAndGet()
            }
        }

    /** Pumps the main looper until every started request has finished, then lets Compose redraw. */
    fun awaitIdle(timeoutMs: Long = 10_000, failOnTimeout: Boolean = true) {
        val looper = shadowOf(Looper.getMainLooper())
        val deadline = System.nanoTime() + timeoutMs * 1_000_000
        do {
            looper.idleFor(Duration.ofMillis(50))
            if (started.get() == finished.get()) break
            Thread.sleep(10)
        } while (System.nanoTime() < deadline)
        check(!failOnTimeout || started.get() == finished.get()) {
            "Coil requests still in flight after ${timeoutMs}ms: started=${started.get()} finished=${finished.get()}"
        }
        looper.idleFor(Duration.ofMillis(200))
    }

    /**
     * [awaitIdle], then measures and lays [view] out again so the loaded images are in the next draw.
     * Best effort: the favicon service is a real network call, so a slow answer must not fail a preview.
     */
    fun awaitIdle(view: View, width: Int, height: Int) {
        awaitIdle(failOnTimeout = false)
        view.measure(
            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY),
        )
        view.layout(0, 0, width, height)
    }

    companion object {
        fun install(context: Context): CoilIdleTracker {
            val tracker = CoilIdleTracker()
            Coil.setImageLoader(
                ImageLoader.Builder(context.applicationContext)
                    .components { add(SvgDecoder.Factory()) }
                    .eventListener(tracker.listener)
                    .build(),
            )
            return tracker
        }

        fun uninstall() {
            Coil.reset()
        }
    }
}

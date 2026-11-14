package com.jestk.browser.engine.adblock

import android.util.Log
import androidx.webkit.ServiceWorkerClientCompat
import androidx.webkit.ServiceWorkerControllerCompat
import androidx.webkit.WebViewFeature
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Applies the network policy to requests made by service workers (YouTube, many news PWAs), which
 * never reach a tab's `shouldInterceptRequest`. The client is process-wide and registered once from
 * the UI layer that already owns the settings providers (`WebViewPoolBindingEffects`).
 */
object ServiceWorkerBlocking {
    private val registered = AtomicBoolean(false)

    internal var isSupported: () -> Boolean = {
        WebViewFeature.isFeatureSupported(WebViewFeature.SERVICE_WORKER_BASIC_USAGE) &&
            WebViewFeature.isFeatureSupported(WebViewFeature.SERVICE_WORKER_SHOULD_INTERCEPT_REQUEST)
    }

    internal var install: (ServiceWorkerClientCompat) -> Unit = {
        ServiceWorkerControllerCompat.getInstance().setServiceWorkerClient(it)
    }

    /** Registers the client on the first call; later calls and unsupported WebViews do nothing. */
    fun register(adsEnabled: () -> Boolean, trackersEnabled: () -> Boolean): Boolean {
        if (!isSupported() || !registered.compareAndSet(false, true)) return false
        val interceptor = NetworkRequestInterceptor(adsEnabled, trackersEnabled)
        return try {
            install(
                object : ServiceWorkerClientCompat() {
                    // No tab URL: the source comes from Referer/Origin of the worker's request.
                    override fun shouldInterceptRequest(request: android.webkit.WebResourceRequest) =
                        interceptor.intercept(request, tabUrl = null)
                },
            )
            true
        } catch (t: Throwable) {
            registered.set(false)
            Log.w("ServiceWorkerBlocking", "Could not register the service-worker client", t)
            false
        }
    }

    internal fun resetForTest() {
        registered.set(false)
    }
}

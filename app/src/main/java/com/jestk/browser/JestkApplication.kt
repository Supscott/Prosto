package com.jestk.browser

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import com.jestk.browser.engine.addSiteIcons
import com.jestk.browser.di.AppContainer
import com.jestk.browser.di.DefaultAppContainer
import com.jestk.browser.domain.model.registrableDomain
import com.jestk.browser.engine.FaviconManager
import com.jestk.browser.engine.SnapshotEngine
import com.jestk.browser.engine.WebViewStateStore
import com.jestk.browser.engine.ProfileCleanup
import com.jestk.browser.engine.adblock.FilterListServices
import com.jestk.browser.engine.adblock.SiteAllowlistSync
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

open class JestkApplication : Application(), ImageLoaderFactory {
    lateinit var container: AppContainer
        private set

    /** Coil's default loader plus the fetcher that loads a site's own icon (PRO-184). */
    override fun newImageLoader(): ImageLoader =
        ImageLoader.Builder(this).components { addSiteIcons() }.build()

    override fun onCreate() {
        super.onCreate()
        SnapshotEngine.init(applicationContext)
        WebViewStateStore.init(applicationContext)
        FaviconManager.init(applicationContext)
        container = DefaultAppContainer(applicationContext)
        runCatching { FilterListServices.install(applicationContext) }
        val background = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        // The blocking hooks read an in-memory copy of the per-site allowlist; keep it current for the process lifetime.
        SiteAllowlistSync.start(background, container.siteAllowlistRepository)
        // OkHttp reads and unpacks its public suffix list on the first registrable-domain lookup and
        // makes the caller wait; do it here so the first composition of the domain-group chip does not.
        background.launch { registrableDomain("https://www.example.com/") }
        // Delete the incognito profiles and deleted-workspace profiles an earlier process left on disk.
        // No WebView uses them (neither kind is ever restored), so running after onCreate is soon enough.
        ProfileCleanup.start(applicationContext)
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (::container.isInitialized) container.webViewPool.onTrimMemory(level)
    }

    /**
     * Releases the container resources when the process is torn down.
     *
     * Android only calls this hook on emulators, so it is a best-effort teardown: the container
     * shutdown is idempotent and the engine singletons recreate their scope lazily, therefore
     * leaving it to the process is safe.
     */
    override fun onTerminate() {
        container.shutdown()
        super.onTerminate()
    }
}

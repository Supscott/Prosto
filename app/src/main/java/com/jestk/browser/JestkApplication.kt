package com.jestk.browser

import android.app.Application
import com.jestk.browser.di.AppContainer
import com.jestk.browser.di.DefaultAppContainer
import com.jestk.browser.domain.model.registrableDomain
import com.jestk.browser.engine.FaviconManager
import com.jestk.browser.engine.SnapshotEngine
import com.jestk.browser.engine.WebViewStateStore
import com.jestk.browser.engine.deleteLeftoverIncognitoProfile
import com.jestk.browser.engine.adblock.FilterListServices
import com.jestk.browser.engine.adblock.SiteAllowlistSync
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class JestkApplication : Application() {
    lateinit var container: AppContainer
        private set

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
        // Incognito tabs do not survive the process; drop the profile a killed process left behind.
        background.launch { deleteLeftoverIncognitoProfile() }
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

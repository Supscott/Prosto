package com.jestk.browser

import android.app.Application
import com.jestk.browser.di.AppContainer
import com.jestk.browser.di.DefaultAppContainer
import com.jestk.browser.engine.FaviconManager
import com.jestk.browser.engine.SnapshotEngine

class JestkApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        SnapshotEngine.init(applicationContext)
        FaviconManager.init(applicationContext)
        container = DefaultAppContainer(applicationContext)
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

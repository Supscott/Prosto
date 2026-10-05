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
}

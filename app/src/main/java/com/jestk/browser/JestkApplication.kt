package com.jestk.browser

import android.app.Application
import com.jestk.browser.di.AppContainer
import com.jestk.browser.di.DefaultAppContainer

class JestkApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = DefaultAppContainer(applicationContext)
    }
}

package com.jestk.browser.engine

import okhttp3.OkHttpClient

/**
 * The app's one OkHttp root. Subsystems derive their client with `root.newBuilder()`, which
 * keeps their own timeouts and interceptors but shares the connection pool and dispatcher.
 */
object HttpClients {
    val root: OkHttpClient by lazy { OkHttpClient() }
}

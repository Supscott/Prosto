package com.jestk.browser.testing

import coil.ImageLoader
import coil.ImageLoaderFactory
import com.jestk.browser.JestkApplication

/**
 * The Application of every Robolectric test (`application=` in `robolectric.properties`). Coil's
 * default singleton asks the application for an [ImageLoaderFactory], so any `AsyncImage` in any
 * test gets a loader that serves http(s) images from [FakeFaviconFetcher] and never reaches the
 * network (PRO-178). [CoilIdleTracker.install] still replaces it in the preview tests that wait for
 * image loads.
 */
class OfflineImageApplication : JestkApplication(), ImageLoaderFactory {
    override fun newImageLoader(): ImageLoader = offlineImageLoaderBuilder(this).build()
}

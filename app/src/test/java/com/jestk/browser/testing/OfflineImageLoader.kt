package com.jestk.browser.testing

import android.content.Context
import coil.ImageLoader
import coil.decode.SvgDecoder

/**
 * The Coil loader every Robolectric test uses: http(s) images come from [FakeFaviconFetcher] (no
 * network) and SVGs decode. [OfflineImageApplication] builds the default loader from it and
 * [CoilIdleTracker.install] adds its request listener, so the two never drift apart.
 */
fun offlineImageLoaderBuilder(context: Context): ImageLoader.Builder =
    ImageLoader.Builder(context.applicationContext)
        .components {
            add(FakeFaviconFetcher.Factory())
            add(SvgDecoder.Factory())
        }

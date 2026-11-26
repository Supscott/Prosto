package com.jestk.browser.engine

import coil.ComponentRegistry
import coil.ImageLoader
import coil.decode.DataSource
import coil.decode.ImageSource
import coil.fetch.FetchResult
import coil.fetch.Fetcher
import coil.fetch.SourceResult
import coil.key.Keyer
import coil.request.Options
import okio.Buffer
import java.io.IOException

/** The image-loader model of a site icon: the origin whose [SiteIcon.candidateUrls] are tried in order. */
data class SiteIconModel(val origin: String)

/** An icon found on a site: its bytes and the MIME type sniffed from them. */
class SiteIconBytes(val bytes: ByteArray, val mimeType: String)

/** Coil fetcher for [SiteIconModel]: a failure (no candidate is an image) is an image error, so the caller draws its letter tile. */
class SiteIconFetcher(
    private val model: SiteIconModel,
    private val options: Options,
) : Fetcher {
    override suspend fun fetch(): FetchResult {
        val icon =
            SiteIconDownloader.fetch(model.origin, cache = SiteIconDiskCache.forContext(options.context))
                ?: throw IOException("No icon on ${model.origin}")
        return SourceResult(
            source = ImageSource(Buffer().write(icon.bytes), options.context),
            mimeType = icon.mimeType,
            dataSource = DataSource.NETWORK,
        )
    }

    class Factory : Fetcher.Factory<SiteIconModel> {
        override fun create(data: SiteIconModel, options: Options, imageLoader: ImageLoader): Fetcher =
            SiteIconFetcher(data, options)
    }

    object SiteIconKeyer : Keyer<SiteIconModel> {
        override fun key(data: SiteIconModel, options: Options): String = "siteicon:${data.origin}"
    }
}

/** Registers the site-icon fetcher and its memory-cache key on an image loader's component registry. */
fun ComponentRegistry.Builder.addSiteIcons(): ComponentRegistry.Builder =
    add(SiteIconFetcher.SiteIconKeyer).add(SiteIconFetcher.Factory())

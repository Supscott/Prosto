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
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okio.Buffer
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

/** The image-loader model of a site icon: the origin whose [SiteIcon.candidateUrls] are tried in order. */
data class SiteIconModel(val origin: String)

/** An icon found on a site: its bytes and the MIME type sniffed from them. */
class SiteIconBytes(val bytes: ByteArray, val mimeType: String)

/**
 * Downloads a site's own icon, trying [SiteIcon.candidateUrls] in order.
 *
 * Requests go through a dedicated client that has no cookie jar (OkHttp's default), so they never
 * carry the cookies of the browsing profiles, and send no Referer. A candidate counts only when
 * its bytes sniff as an image: a 404 page, or `/favicon.ico` redirected to an HTML page, is
 * skipped and the next candidate is tried. A failed origin is not asked again for a few minutes.
 */
object SiteIconDownloader {
    private const val TIMEOUT_SECONDS = 6L
    private const val MAX_BYTES = 512L * 1024
    private const val FAILURE_TTL_MS = 5 * 60 * 1000L
    private const val USER_AGENT = "Mozilla/5.0 ProstoBrowser/1.0"

    private val failedAt = ConcurrentHashMap<String, Long>()

    private val defaultClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }

    suspend fun fetch(
        origin: String,
        client: OkHttpClient = defaultClient,
        now: () -> Long = System::currentTimeMillis,
    ): SiteIconBytes? {
        val failedTime = failedAt[origin]
        if (failedTime != null && now() - failedTime < FAILURE_TTL_MS) return null
        for (url in SiteIcon.candidateUrls(origin)) {
            val icon = download(client, url)
            if (icon != null) {
                failedAt.remove(origin)
                return icon
            }
        }
        failedAt[origin] = now()
        return null
    }

    internal fun clearFailures() = failedAt.clear()

    private suspend fun download(client: OkHttpClient, url: String): SiteIconBytes? =
        suspendCancellableCoroutine { continuation ->
            val request =
                runCatching {
                    Request.Builder()
                        .url(url)
                        .header("User-Agent", USER_AGENT)
                        .header("Accept", "image/*")
                        .build()
                }.getOrNull()
            if (request == null) {
                continuation.resume(null)
                return@suspendCancellableCoroutine
            }
            val call = client.newCall(request)
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(
                object : Callback {
                    override fun onFailure(call: Call, e: IOException) = continuation.resume(null)

                    override fun onResponse(call: Call, response: Response) {
                        val icon = response.use { readImage(it) }
                        continuation.resume(icon)
                    }
                },
            )
        }

    private fun readImage(response: Response): SiteIconBytes? {
        if (!response.isSuccessful) return null
        val body = response.body ?: return null
        if (body.contentLength() > MAX_BYTES) return null
        return try {
            val source = body.source()
            if (source.request(MAX_BYTES + 1)) return null
            val bytes = source.readByteArray()
            sniffImageMime(bytes)?.let { SiteIconBytes(bytes, it) }
        } catch (_: IOException) {
            null
        }
    }

    /** The image MIME type the leading bytes announce, or null when they are not a known raster format. */
    internal fun sniffImageMime(bytes: ByteArray): String? {
        fun starts(vararg magic: Int) =
            bytes.size >= magic.size && magic.indices.all { (bytes[it].toInt() and 0xFF) == magic[it] }
        return when {
            starts(0x89, 0x50, 0x4E, 0x47) -> "image/png"
            starts(0x00, 0x00, 0x01, 0x00) -> "image/x-icon"
            starts(0xFF, 0xD8, 0xFF) -> "image/jpeg"
            starts(0x47, 0x49, 0x46, 0x38) -> "image/gif"
            starts(0x52, 0x49, 0x46, 0x46) && bytes.size > 12 &&
                String(bytes, 8, 4, Charsets.US_ASCII) == "WEBP" -> "image/webp"
            else -> null
        }
    }
}

/** Coil fetcher for [SiteIconModel]: a failure (no candidate is an image) is an image error, so the caller draws its letter tile. */
class SiteIconFetcher(
    private val model: SiteIconModel,
    private val options: Options,
) : Fetcher {
    override suspend fun fetch(): FetchResult {
        val icon = SiteIconDownloader.fetch(model.origin) ?: throw IOException("No icon on ${model.origin}")
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

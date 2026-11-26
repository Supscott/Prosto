package com.jestk.browser.engine

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

/**
 * Downloads a site's own icon, trying [SiteIcon.candidateUrls] in order.
 *
 * - Requests go through a dedicated client that has no cookie jar (OkHttp's default), so they never
 *   carry the cookies of the browsing profiles, and send no Referer.
 * - Redirects are followed by hand, at most [MAX_REDIRECTS]: a hop to a private host or from https
 *   to http is refused before any request is made (a public site must not make the phone probe the
 *   LAN or fall back to plain HTTP). A call may take at most [CALL_TIMEOUT_SECONDS].
 * - A candidate counts only when its bytes sniff as an image: a 404 page, or `/favicon.ico`
 *   redirected to an HTML page, is skipped and the next candidate is tried.
 * - Concurrent loads of one origin share one call. With a [SiteIconDiskCache] the result, or the
 *   absence of an icon, is remembered across cold starts; without one an origin that failed is
 *   remembered in memory only (the most recent [MAX_REMEMBERED_FAILURES]).
 */
object SiteIconDownloader {
    private const val CONNECT_TIMEOUT_SECONDS = 6L
    private const val READ_TIMEOUT_SECONDS = 6L
    internal const val CALL_TIMEOUT_SECONDS = 10L
    internal const val MAX_REDIRECTS = 3
    private const val MAX_BYTES = 512L * 1024
    private const val MAX_REMEMBERED_FAILURES = 256
    private const val USER_AGENT = "Mozilla/5.0 ProstoBrowser/1.0"

    private val failedAt: MutableMap<String, Long> =
        Collections.synchronizedMap(
            object : LinkedHashMap<String, Long>(16, 0.75f, true) {
                override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Long>) =
                    size > MAX_REMEMBERED_FAILURES
            },
        )
    private val inFlight = ConcurrentHashMap<String, Deferred<SiteIconBytes?>>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Redirects stay manual (see [download]), so the client must not follow them itself. */
    internal val defaultClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .callTimeout(CALL_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .followRedirects(false)
            .followSslRedirects(false)
            .build()
    }

    suspend fun fetch(
        origin: String,
        client: OkHttpClient = defaultClient,
        now: () -> Long = System::currentTimeMillis,
        cache: SiteIconDiskCache? = null,
        isPrivateHost: (String) -> Boolean = SiteIcon::isPrivateHost,
    ): SiteIconBytes? {
        val failedTime = failedAt[origin]
        if (failedTime != null && now() - failedTime < SiteIconDiskCache.MISS_TTL_MS) return null
        val shared =
            inFlight.computeIfAbsent(origin) {
                scope.async(start = CoroutineStart.LAZY) {
                    try {
                        load(origin, client, now, cache, isPrivateHost)
                    } finally {
                        inFlight.remove(origin)
                    }
                }
            }
        return shared.await()
    }

    internal fun clearFailures() = failedAt.clear()

    private suspend fun load(
        origin: String,
        client: OkHttpClient,
        now: () -> Long,
        cache: SiteIconDiskCache?,
        isPrivateHost: (String) -> Boolean,
    ): SiteIconBytes? {
        when (val cached = cache?.read(origin, now())) {
            is SiteIconDiskCache.Entry.Hit -> return cached.icon
            SiteIconDiskCache.Entry.Missing -> {
                failedAt[origin] = now()
                return null
            }
            null -> Unit
        }
        for (url in SiteIcon.candidateUrls(origin)) {
            val icon = download(client, url, isPrivateHost)
            if (icon != null) {
                failedAt.remove(origin)
                cache?.writeHit(origin, icon, now())
                return icon
            }
        }
        failedAt[origin] = now()
        cache?.writeMiss(origin, now())
        return null
    }

    private suspend fun download(client: OkHttpClient, startUrl: String, isPrivateHost: (String) -> Boolean): SiteIconBytes? {
        var url: HttpUrl = startUrl.toHttpUrlOrNull() ?: return null
        for (hop in 0..MAX_REDIRECTS) {
            val response = execute(client, url) ?: return null
            val next =
                response.use { r ->
                    if (!r.isRedirect) return readImage(r)
                    val target = r.header("Location")?.let { r.request.url.resolve(it) } ?: return null
                    if (!redirectAllowed(url, target, isPrivateHost)) return null
                    target
                }
            url = next
        }
        return null
    }

    /** Whether a redirect from [from] to [to] may be followed: public host, no downgrade to http. */
    internal fun redirectAllowed(from: HttpUrl, to: HttpUrl, isPrivateHost: (String) -> Boolean): Boolean =
        (to.scheme == "http" || to.scheme == "https") &&
            !(from.isHttps && !to.isHttps) &&
            !isPrivateHost(to.host.lowercase())

    private suspend fun execute(client: OkHttpClient, url: HttpUrl): Response? =
        suspendCancellableCoroutine { continuation ->
            val request =
                Request.Builder()
                    .url(url)
                    .header("User-Agent", USER_AGENT)
                    .header("Accept", "image/*")
                    .build()
            val call = client.newCall(request)
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(
                object : Callback {
                    override fun onFailure(call: Call, e: IOException) = continuation.resume(null)

                    override fun onResponse(call: Call, response: Response) = continuation.resume(response)
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

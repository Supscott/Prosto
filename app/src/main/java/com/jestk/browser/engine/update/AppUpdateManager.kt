package com.jestk.browser.engine.update

import android.content.Context
import android.util.Log
import com.jestk.browser.domain.model.AppUpdate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import com.jestk.browser.util.SharedPrefsUpdateCheckStore
import com.jestk.browser.util.UpdateCheckStore
import okhttp3.Request
import okhttp3.Response
import org.json.JSONObject
import java.util.concurrent.TimeUnit

private const val TAG = "AppUpdateManager"

/**
 * Talks to the GitHub Releases API to detect newer releases.
 * Does not download or install APKs itself; instead provides release metadata
 * for transparent redirect and notification.
 */
open class AppUpdateManager(
    private val context: Context,
    val store: UpdateCheckStore = SharedPrefsUpdateCheckStore(context),
    private val baseUrl: String = "https://api.github.com",
    private val webBaseUrl: String = "https://github.com",
    private val clock: () -> Long = { System.currentTimeMillis() },
) {
    private val client =
        OkHttpClient
            .Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()

    /** The same client without redirects: the tag check reads the redirect target itself. */
    private val noRedirectClient = client.newBuilder().followRedirects(false).followSslRedirects(false).build()

    /**
     * The latest release's version from the `github.com/<repo>/releases/latest` redirect, or null.
     * That page is outside the REST API's anonymous limit (60 requests an hour per IP, shared by
     * everyone behind the IP), so it is the cheap "did anything change?" probe; the API is asked
     * for the notes only when this returns a newer version.
     */
    open suspend fun fetchLatestTagVersion(repo: String = DEFAULT_REPO): String? =
        withContext(Dispatchers.IO) {
            runCatching {
                val request =
                    Request
                        .Builder()
                        .url("${webBaseUrl.trimEnd('/')}/$repo/releases/latest")
                        .header("User-Agent", USER_AGENT)
                        .head()
                        .build()
                noRedirectClient.newCall(request).execute().use { response ->
                    if (!response.isRedirect) return@use null
                    versionFromReleaseLocation(response.header("Location"))
                }
            }.getOrElse { e ->
                Log.w(TAG, "Latest tag probe failed: ${e.message}")
                null
            }
        }

    /**
     * Asks the GitHub API for the latest release. A stored [etag] makes the request conditional,
     * so an unchanged release answers 304 without a body. For anonymous requests a 304 still
     * counts against the rate limit (measured 2026-11-19), so callers probe
     * [fetchLatestTagVersion] first.
     */
    open suspend fun fetchLatestRelease(
        repo: String = DEFAULT_REPO,
        etag: String? = null,
    ): UpdateFetchResult =
        withContext(Dispatchers.IO) {
            runCatching {
                val url = "${baseUrl.trimEnd('/')}/repos/$repo/releases/latest"
                Log.d(TAG, "Fetching latest release from $url (etag=${etag != null})")
                val request =
                    Request
                        .Builder()
                        .url(url)
                        .header("Accept", "application/vnd.github+json")
                        .header("User-Agent", USER_AGENT)
                        .apply { if (etag != null) header("If-None-Match", etag) }
                        .build()
                client.newCall(request).execute().use { response ->
                    if (response.code == 304) return@use UpdateFetchResult.NotModified
                    if (!response.isSuccessful) {
                        return@use UpdateFetchResult.Failed(response.code, retryAtMs(response))
                    }
                    val parsed = parseGithubRelease(response.body?.string().orEmpty())
                    if (parsed == null) {
                        Log.w(TAG, "Could not parse valid release with APK from response")
                        return@use UpdateFetchResult.Failed(response.code, null)
                    }
                    UpdateFetchResult.Fresh(parsed, response.header("ETag"))
                }
            }.getOrElse { e ->
                Log.e(TAG, "Exception while fetching latest release", e)
                UpdateFetchResult.Failed(0, null)
            }
        }

    /** Earliest time GitHub asks us to retry: `Retry-After` seconds, or `X-RateLimit-Reset` when the quota is spent. */
    private fun retryAtMs(response: Response): Long? {
        response.header("Retry-After")?.trim()?.toLongOrNull()?.let { return clock() + it * 1000L }
        if (response.header("X-RateLimit-Remaining")?.trim() == "0") {
            response.header("X-RateLimit-Reset")?.trim()?.toLongOrNull()?.let { return it * 1000L }
        }
        return null
    }

    /** Version of the build that is currently installed. */
    open fun currentVersion(): String =
        runCatching {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull() ?: "0.0.0"

    companion object {
        const val DEFAULT_REPO = "Supscott/Prosto"
        private const val USER_AGENT = "Prosto-Android"
    }
}

/** Outcome of one release request. */
sealed interface UpdateFetchResult {
    data class Fresh(val release: AppUpdate, val etag: String?) : UpdateFetchResult

    data object NotModified : UpdateFetchResult

    /** [code] is the HTTP status, or 0 for a network or parse error; [retryAtMs] comes from the rate-limit headers. */
    data class Failed(val code: Int, val retryAtMs: Long?) : UpdateFetchResult
}

/** `https://github.com/o/r/releases/tag/v1.2.3` -> `1.2.3`; null for any other location. */
fun versionFromReleaseLocation(location: String?): String? {
    val tag = location?.substringAfter("/releases/tag/", missingDelimiterValue = "")
        ?.substringBefore('?')?.substringBefore('#')?.trim('/')
    if (tag.isNullOrBlank()) return null
    return tag.removePrefix("v").removePrefix("V").takeIf { it.isNotBlank() }
}

/**
 * Maps a GitHub release payload to an [AppUpdate]. Returns null when the release
 * is empty, draft, prerelease, or missing a valid version name.
 */
fun parseGithubRelease(json: String): AppUpdate? {
    if (json.isBlank()) return null
    val root = runCatching { JSONObject(json) }.getOrNull() ?: return null
    if (root.optBoolean("draft") || root.optBoolean("prerelease")) return null

    val tag = root.optString("tag_name").ifBlank { root.optString("name") }.trim()
    val versionName = tag.removePrefix("v").removePrefix("V")
    if (versionName.isBlank()) return null

    val releaseUrl = root.optString("html_url").ifBlank { "https://github.com/Supscott/Prosto/releases/tag/$tag" }
    return AppUpdate(
        versionName = versionName,
        releaseNotes = root.optString("body"),
        releaseUrl = releaseUrl,
    )
}

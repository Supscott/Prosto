package com.jestk.browser.engine.update

import android.content.Context
import android.util.Log
import com.jestk.browser.domain.model.AppUpdate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
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
) {
    private val client =
        OkHttpClient
            .Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()

    /** Returns the latest GitHub release that ships an APK, or null when unavailable. */
    open suspend fun fetchLatestRelease(repo: String = DEFAULT_REPO): AppUpdate? =
        withContext(Dispatchers.IO) {
            runCatching {
                val url = "https://api.github.com/repos/$repo/releases/latest"
                Log.d(TAG, "Fetching latest release from $url")
                val request =
                    Request
                        .Builder()
                        .url(url)
                        .header("Accept", "application/vnd.github+json")
                        .header("User-Agent", USER_AGENT)
                        .build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        Log.w(TAG, "GitHub release fetch failed: HTTP ${response.code} ${response.message}")
                        return@use null
                    }
                    val body = response.body?.string().orEmpty()
                    val parsed = parseGithubRelease(body)
                    if (parsed != null) {
                        Log.d(TAG, "Fetched release: ${parsed.versionName}")
                    } else {
                        Log.w(TAG, "Could not parse valid release with APK from response")
                    }
                    parsed
                }
            }.onFailure { e ->
                Log.e(TAG, "Exception while fetching latest release", e)
            }.getOrNull()
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

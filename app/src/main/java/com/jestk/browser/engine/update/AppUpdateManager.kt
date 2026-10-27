package com.jestk.browser.engine.update

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.FileProvider
import com.jestk.browser.R
import com.jestk.browser.domain.model.AppUpdate
import com.jestk.browser.domain.model.compareVersions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

private const val TAG = "AppUpdateManager"

/**
 * Talks to the GitHub Releases API, downloads release APKs and hands them to the
 * system package installer.
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

    /** Downloads the release APK into app-private storage. */
    open suspend fun download(
        update: AppUpdate,
        onProgress: (Int) -> Unit,
    ): File? {
        val target = updateFile(update.versionName)
        val temp = File(target.parentFile, "${target.name}.part")
        return withContext(Dispatchers.IO) {
            runCatching {
                val request =
                    Request
                        .Builder()
                        .url(update.apkUrl)
                        .header("User-Agent", USER_AGENT)
                        .build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@use null
                    val body = response.body ?: return@use null
                    val total = body.contentLength()
                    body.byteStream().use { input ->
                        temp.outputStream().use { output ->
                            val buffer = ByteArray(BUFFER_SIZE)
                            var downloaded = 0L
                            while (true) {
                                val read = input.read(buffer)
                                if (read == -1) break
                                output.write(buffer, 0, read)
                                downloaded += read
                                if (total > 0) onProgress(((downloaded * 100) / total).toInt().coerceIn(0, 100))
                            }
                        }
                    }
                    target.delete()
                    if (temp.renameTo(target)) target else null
                } ?: run {
                    temp.delete()
                    null
                }
            }.onFailure { temp.delete() }.getOrNull()
        }
    }

    /** Version of the build that is currently installed. */
    open fun currentVersion(): String =
        runCatching {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull() ?: "0.0.0"

    /** Version of a previously downloaded APK that is still newer than [currentVersion], if any. */
    open fun pendingUpdateVersion(currentVersion: String = currentVersion()): String? {
        val files = updateDir().listFiles { file -> file.isFile && file.name.endsWith(APK_SUFFIX) } ?: return null
        val candidates =
            files.mapNotNull { file ->
                val version = file.name.removePrefix(FILE_PREFIX).removeSuffix(APK_SUFFIX)
                if (version.isBlank()) null else version to file
            }
        // Outdated APKs are useless: drop them so they never get installed.
        candidates
            .filter { (version, _) -> compareVersions(version, currentVersion) <= 0 }
            .forEach { (_, file) -> file.delete() }
        return candidates
            .filter { (version, _) -> compareVersions(version, currentVersion) > 0 }
            .sortedWith { (left, _), (right, _) -> compareVersions(left, right) }
            .lastOrNull()
            ?.first
    }

    open fun canInstallPackages(): Boolean = context.packageManager.canRequestPackageInstalls()

    /** Opens the system installer for a previously downloaded APK. */
    open fun installUpdate(versionName: String): Boolean {
        val file = updateFile(versionName)
        if (!file.exists()) return false
        return runCatching {
            context.startActivity(installIntent(file))
            true
        }.getOrDefault(false)
    }

    /** Sends the user to the system screen where "install unknown apps" can be granted. */
    open fun requestInstallPermission(): Boolean =
        runCatching {
            context.startActivity(
                Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:${context.packageName}"),
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
            true
        }.getOrDefault(false)

    open fun deletePendingUpdate(versionName: String) {
        updateFile(versionName).delete()
    }

    /** Tells the user that a new version is waiting to be installed. */
    open fun notifyUpdateReady(versionName: String) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    context.getString(R.string.update_notification_channel),
                    NotificationManager.IMPORTANCE_DEFAULT,
                ),
            )
        }
        val file = updateFile(versionName)
        val contentIntent =
            if (file.exists()) {
                try {
                    PendingIntent.getActivity(
                        context,
                        0,
                        installIntent(file),
                        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                    )
                } catch (t: Throwable) {
                    android.util.Log.w("AppUpdateManager", "Failed to create install intent for notification", t)
                    null
                }
            } else {
                null
            }
        val notification =
            NotificationCompat
                .Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_update_notif)
                .setContentTitle(context.getString(R.string.update_notification_title))
                .setContentText(context.getString(R.string.update_notification_body, versionName))
                .setAutoCancel(true)
                .setContentIntent(contentIntent)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .build()
        manager.notify(NOTIFICATION_ID, notification)
    }

    private fun installIntent(file: File): Intent {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        return Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, APK_MIME)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    private fun updateDir(): File = File(context.filesDir, UPDATE_DIR).apply { mkdirs() }

    private fun updateFile(versionName: String): File = File(updateDir(), "$FILE_PREFIX$versionName$APK_SUFFIX")

    companion object {
        const val DEFAULT_REPO = "Supscott/Prosto"
        private const val USER_AGENT = "Prosto-Android"
        private const val UPDATE_DIR = "updates"
        private const val FILE_PREFIX = "prosto-"
        private const val APK_SUFFIX = ".apk"
        private const val APK_MIME = "application/vnd.android.package-archive"
        private const val CHANNEL_ID = "prosto_updates"
        private const val NOTIFICATION_ID = 4201
        private const val BUFFER_SIZE = 16 * 1024
    }
}

/**
 * Maps a GitHub release payload to an [AppUpdate]. Returns null when the release
 * carries no installable APK.
 */
fun parseGithubRelease(json: String): AppUpdate? {
    if (json.isBlank()) return null
    val root = runCatching { JSONObject(json) }.getOrNull() ?: return null
    if (root.optBoolean("draft") || root.optBoolean("prerelease")) return null

    val tag = root.optString("tag_name").ifBlank { root.optString("name") }.trim()
    val versionName = tag.removePrefix("v").removePrefix("V")
    if (versionName.isBlank()) return null

    val assets = root.optJSONArray("assets") ?: return null
    for (index in 0 until assets.length()) {
        val asset = assets.optJSONObject(index) ?: continue
        val name = asset.optString("name")
        val isExpectedReleaseApk = name.startsWith("prosto-", ignoreCase = true) && name.endsWith(".apk", ignoreCase = true)
        if (!isExpectedReleaseApk) continue
        val apkUrl = asset.optString("browser_download_url")
        if (apkUrl.isBlank()) continue
        return AppUpdate(
            versionName = versionName,
            releaseNotes = root.optString("body"),
            apkUrl = apkUrl,
            releaseUrl = root.optString("html_url"),
        )
    }
    return null
}

package com.jestk.browser.util

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.core.content.FileProvider
import com.jestk.browser.R
import com.jestk.browser.domain.model.DownloadItem
import com.jestk.browser.engine.download.DownloadFileNameResolver
import java.io.File

/**
 * Handles opening and executing downloaded files.
 *
 * For APK files:
 * - Overrides MIME type to `application/vnd.android.package-archive`.
 * - On Android 8.0+ (API 26+), verifies `context.packageManager.canRequestPackageInstalls()`.
 *   If false, redirects the user to `Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES` with the app's package URI.
 *   If true, directly launches `Intent.ACTION_VIEW` with `FLAG_GRANT_READ_URI_PERMISSION` and `FLAG_ACTIVITY_NEW_TASK`
 *   (without `Intent.createChooser` which drops permissions for the package installer).
 *
 * For non-APK files:
 * - Launches `Intent.createChooser` with `Intent.ACTION_VIEW`.
 */
object DownloadFileOpener {

    private const val TAG = "DownloadFileOpener"

    fun openDownloadedFile(
        context: Context,
        download: DownloadItem,
    ): Boolean {
        return openFile(
            context = context,
            filePath = download.filePath,
            fileName = download.fileName,
            mimeType = download.mimeType,
        )
    }

    fun openFile(
        context: Context,
        filePath: String,
        fileName: String,
        mimeType: String? = null,
    ): Boolean {
        try {
            val file = File(filePath)
            if (!file.exists()) {
                Toast.makeText(
                    context,
                    context.getString(R.string.download_file_not_found, fileName),
                    Toast.LENGTH_SHORT,
                ).show()
                return false
            }

            val uri: Uri =
                try {
                    FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.fileprovider",
                        file,
                    )
                } catch (e: Exception) {
                    // A file:// URI cannot leave the app on API 24+, so there is no fallback.
                    Log.e(TAG, "FileProvider cannot share $filePath", e)
                    Toast.makeText(
                        context,
                        context.getString(R.string.download_cannot_open),
                        Toast.LENGTH_SHORT,
                    ).show()
                    return false
                }

            val resolvedMime = DownloadFileNameResolver.resolveMimeType(fileName, mimeType)
            val isApk = fileName.endsWith(".apk", ignoreCase = true) ||
                resolvedMime == DownloadFileNameResolver.MIME_TYPE_APK

            if (isApk) {
                return openApkFile(context, uri)
            }

            val intent =
                Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, resolvedMime)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }

            val chooser = Intent.createChooser(intent, context.getString(R.string.download_open_with)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
            return true
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(
                context,
                context.getString(R.string.download_no_app_to_open),
                Toast.LENGTH_SHORT,
            ).show()
            return false
        } catch (e: Exception) {
            Log.e(TAG, "Cannot open file: $fileName", e)
            Toast.makeText(
                context,
                context.getString(R.string.download_cannot_open),
                Toast.LENGTH_SHORT,
            ).show()
            return false
        }
    }

    private fun openApkFile(
        context: Context,
        uri: Uri,
    ): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            !context.packageManager.canRequestPackageInstalls()
        ) {
            Toast.makeText(
                context,
                context.getString(R.string.download_install_unknown_apps_prompt),
                Toast.LENGTH_LONG,
            ).show()
            try {
                val settingsIntent =
                    Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                        data = Uri.parse("package:${context.packageName}")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                context.startActivity(settingsIntent)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to launch ACTION_MANAGE_UNKNOWN_APP_SOURCES", e)
                try {
                    val fallbackIntent =
                        Intent(Settings.ACTION_SECURITY_SETTINGS).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                    context.startActivity(fallbackIntent)
                } catch (secError: Exception) {
                    Log.e(TAG, "Failed to launch security settings", secError)
                }
            }
            return false
        }

        val installIntent =
            Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, DownloadFileNameResolver.MIME_TYPE_APK)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        context.startActivity(installIntent)
        return true
    }
}

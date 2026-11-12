package com.jestk.browser.util

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.jestk.browser.R
import com.jestk.browser.domain.model.DownloadItem
import com.jestk.browser.domain.model.DownloadStatus
import com.jestk.browser.engine.download.DownloadFileNameResolver
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowToast
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.O])
class DownloadFileOpenerTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var context: Context

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext<Application>()
        // FileProvider caches its roots per authority in a static map, but Robolectric gives every
        // test a new data directory: drop the cache so each test resolves its own filesDir.
        FileProvider::class.java.getDeclaredField("sCache").apply { isAccessible = true }
            .let { (it.get(null) as MutableMap<*, *>).clear() }
    }

    /** A file in a directory the app's FileProvider shares (`files-path` "Download"). */
    private fun downloadedFile(name: String): File =
        File(context.filesDir, "Download").apply { mkdirs() }.resolve(name).apply { writeBytes(byteArrayOf(1)) }

    @Test
    fun openFile_returnsFalseAndShowsToast_whenFileDoesNotExist() {
        val nonExistentPath = File(tempFolder.root, "ghost.apk").absolutePath
        val result = DownloadFileOpener.openFile(
            context = context,
            filePath = nonExistentPath,
            fileName = "ghost.apk",
            mimeType = DownloadFileNameResolver.MIME_TYPE_APK,
        )

        assertThat(result).isFalse()
        val latestToast = ShadowToast.getTextOfLatestToast()
        assertThat(latestToast).isEqualTo(context.getString(R.string.download_file_not_found, "ghost.apk"))
    }

    @Test
    fun openFile_opensUnknownAppSourcesSetting_whenApkAndPermissionNotGrantedOnAndroidO() {
        val apkFile = downloadedFile("app-release.apk")
        shadowOf(context.packageManager).setCanRequestPackageInstalls(false)

        val result = DownloadFileOpener.openFile(
            context = context,
            filePath = apkFile.absolutePath,
            fileName = apkFile.name,
            mimeType = "application/octet-stream",
        )

        assertThat(result).isFalse()
        val latestToast = ShadowToast.getTextOfLatestToast()
        assertThat(latestToast).isEqualTo(context.getString(R.string.download_install_unknown_apps_prompt))

        val startedIntent = shadowOf(context as Application).nextStartedActivity
        assertThat(startedIntent).isNotNull()
        assertThat(startedIntent.action).isEqualTo(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES)
        assertThat(startedIntent.data).isEqualTo(Uri.parse("package:${context.packageName}"))
        assertThat(startedIntent.flags and Intent.FLAG_ACTIVITY_NEW_TASK).isNotEqualTo(0)
    }

    @Test
    fun openFile_launchesPackageInstallerDirectly_whenApkAndPermissionGranted() {
        val apkFile = downloadedFile("test-tool.apk")
        shadowOf(context.packageManager).setCanRequestPackageInstalls(true)

        val download = DownloadItem(
            id = 1L,
            url = "https://github.com/test/releases/download/v1.0/test-tool.apk",
            fileName = apkFile.name,
            filePath = apkFile.absolutePath,
            mimeType = "application/octet-stream",
            contentLength = 1024L,
            status = DownloadStatus.COMPLETED,
            bytesDownloaded = 1024L,
            totalBytes = 1024L,
        )

        val result = DownloadFileOpener.openDownloadedFile(context, download)

        assertThat(result).isTrue()
        val startedIntent = shadowOf(context as Application).nextStartedActivity
        assertThat(startedIntent).isNotNull()
        assertThat(startedIntent.action).isEqualTo(Intent.ACTION_VIEW)
        assertThat(startedIntent.type).isEqualTo(DownloadFileNameResolver.MIME_TYPE_APK)
        assertThat(startedIntent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION).isNotEqualTo(0)
        assertThat(startedIntent.flags and Intent.FLAG_ACTIVITY_NEW_TASK).isNotEqualTo(0)
        // Ensure chooser was NOT used
        assertThat(startedIntent.action).isNotEqualTo(Intent.ACTION_CHOOSER)
    }

    @Test
    fun openFile_launchesChooser_forNonApkFile() {
        val pdfFile = downloadedFile("document.pdf")

        val result = DownloadFileOpener.openFile(
            context = context,
            filePath = pdfFile.absolutePath,
            fileName = pdfFile.name,
            mimeType = "application/pdf",
        )

        assertThat(result).isTrue()
        val startedIntent = shadowOf(context as Application).nextStartedActivity
        assertThat(startedIntent).isNotNull()
        assertThat(startedIntent.action).isEqualTo(Intent.ACTION_CHOOSER)
        val targetIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            startedIntent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
        } else {
            @Suppress("DEPRECATION")
            startedIntent.getParcelableExtra(Intent.EXTRA_INTENT)
        }
        assertThat(targetIntent).isNotNull()
        assertThat(targetIntent?.action).isEqualTo(Intent.ACTION_VIEW)
        assertThat(targetIntent?.type).isEqualTo("application/pdf")
        assertThat(targetIntent?.flags?.and(Intent.FLAG_GRANT_READ_URI_PERMISSION)).isNotEqualTo(0)
    }

    @Test
    fun openFile_returnsFalseWithoutStartingActivity_whenFileProviderCannotShareTheFile() {
        // A file:// URI cannot leave the app on API 24+, so there is no fallback to Uri.fromFile.
        val outsideProviderPaths = tempFolder.newFile("notes.txt")

        val result = DownloadFileOpener.openFile(
            context = context,
            filePath = outsideProviderPaths.absolutePath,
            fileName = outsideProviderPaths.name,
            mimeType = "text/plain",
        )

        assertThat(result).isFalse()
        assertThat(shadowOf(context as Application).nextStartedActivity).isNull()
        assertThat(ShadowToast.getTextOfLatestToast()).isEqualTo(context.getString(R.string.download_cannot_open))
    }
}

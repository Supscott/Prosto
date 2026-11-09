package com.jestk.browser.util

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
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
    }

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
        val apkFile = tempFolder.newFile("app-release.apk")
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
        val apkFile = tempFolder.newFile("test-tool.apk")
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
        val pdfFile = tempFolder.newFile("document.pdf")

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
}

package com.jestk.browser.engine

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class FaviconManagerTest {

    @Test
    fun extractFallbackFaviconUrl_withValidHttpHttps_returnsGoogleFaviconUrl() {
        val result1 = FaviconManager.extractFallbackFaviconUrl("https://example.com/page")
        assertThat(result1).isEqualTo("https://www.google.com/s2/favicons?sz=128&domain=example.com")

        val result2 = FaviconManager.extractFallbackFaviconUrl("http://www.sub.github.com/repo")
        assertThat(result2).isEqualTo("https://www.google.com/s2/favicons?sz=128&domain=sub.github.com")
    }

    @Test
    fun extractFallbackFaviconUrl_withBlankOrNull_returnsNull() {
        assertThat(FaviconManager.extractFallbackFaviconUrl(null)).isNull()
        assertThat(FaviconManager.extractFallbackFaviconUrl("")).isNull()
        assertThat(FaviconManager.extractFallbackFaviconUrl("   ")).isNull()
    }

    @Test
    fun extractFallbackFaviconUrl_withAboutOrFileOrDataUrl_returnsNull() {
        assertThat(FaviconManager.extractFallbackFaviconUrl("about:blank")).isNull()
        assertThat(FaviconManager.extractFallbackFaviconUrl("file:///android_asset/page.html")).isNull()
        assertThat(FaviconManager.extractFallbackFaviconUrl("data:image/png;base64,1234")).isNull()
        assertThat(FaviconManager.extractFallbackFaviconUrl("content://media/external/images")).isNull()
        assertThat(FaviconManager.extractFallbackFaviconUrl("javascript:void(0)")).isNull()
    }

    @Test
    fun extractFallbackFaviconUrl_withLocalhostOrPrivateIp_returnsNull() {
        assertThat(FaviconManager.extractFallbackFaviconUrl("http://localhost:8080")).isNull()
        assertThat(FaviconManager.extractFallbackFaviconUrl("http://127.0.0.1:3000")).isNull()
        assertThat(FaviconManager.extractFallbackFaviconUrl("http://192.168.1.1")).isNull()
        assertThat(FaviconManager.extractFallbackFaviconUrl("http://10.0.0.1/admin")).isNull()
        assertThat(FaviconManager.extractFallbackFaviconUrl("http://172.16.0.1")).isNull()
        assertThat(FaviconManager.extractFallbackFaviconUrl("http://mydev.local")).isNull()
    }

    @Test
    fun toImageModel_handlesNullBlankLocalAndRemotePaths() {
        assertThat(FaviconManager.toImageModel(null)).isNull()
        assertThat(FaviconManager.toImageModel("")).isNull()
        assertThat(FaviconManager.toImageModel("   ")).isNull()

        val httpModel = FaviconManager.toImageModel("https://example.com/icon.png")
        assertThat(httpModel).isEqualTo("https://example.com/icon.png")

        val fileModel = FaviconManager.toImageModel("/data/user/0/favicons/fav.png")
        assertThat(fileModel).isInstanceOf(java.io.File::class.java)
        assertThat((fileModel as java.io.File).absolutePath).isEqualTo("/data/user/0/favicons/fav.png")

        val fileUriModel = FaviconManager.toImageModel("file:///data/user/0/favicons/fav.png")
        assertThat(fileUriModel).isInstanceOf(java.io.File::class.java)
        assertThat((fileUriModel as java.io.File).absolutePath).isEqualTo("/data/user/0/favicons/fav.png")

        val assetUriModel = FaviconManager.toImageModel("file:///android_asset/icons/duckduckgo.svg")
        assertThat(assetUriModel).isInstanceOf(android.net.Uri::class.java)
        assertThat((assetUriModel as android.net.Uri).toString()).isEqualTo("file:///android_asset/icons/duckduckgo.svg")

        val assetSlashModel = FaviconManager.toImageModel("/android_asset/icons/google.svg")
        assertThat(assetSlashModel).isInstanceOf(android.net.Uri::class.java)
        assertThat((assetSlashModel as android.net.Uri).toString()).isEqualTo("file:///android_asset/icons/google.svg")
    }

    @Test
    fun persistFaviconForFavorite_copiesFileToPersistentLocation() = kotlinx.coroutines.test.runTest {
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        val source = java.io.File(context.filesDir, "temp_favicon.png").apply {
            writeBytes("mock_png_data".toByteArray())
        }

        val persistentPath = FaviconManager.persistFaviconForFavorite(context, source.absolutePath)
        assertThat(persistentPath).isNotNull()

        val destFile = java.io.File(persistentPath!!)
        assertThat(destFile.exists()).isTrue()
        assertThat(destFile.name).startsWith("fav_")
        assertThat(destFile.name).endsWith(".png")
        assertThat(destFile.readText()).isEqualTo("mock_png_data")
    }

    @Test
    fun saveFavicon_createsTimestampedFileAndCleansUpOlderTabFiles() = kotlinx.coroutines.test.runTest {
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        val bitmap = android.graphics.Bitmap.createBitmap(16, 16, android.graphics.Bitmap.Config.ARGB_8888)

        val path1 = FaviconManager.saveFavicon(context, "test_tab_1", bitmap)
        assertThat(path1).isNotNull()
        val file1 = java.io.File(path1!!)
        assertThat(file1.exists()).isTrue()
        assertThat(file1.name).matches("favicon_test_tab_1_\\d+\\.png")

        // Small delay to ensure timestamp advances
        kotlinx.coroutines.delay(10)

        val path2 = FaviconManager.saveFavicon(context, "test_tab_1", bitmap)
        assertThat(path2).isNotNull()
        assertThat(path2).isNotEqualTo(path1)

        val file2 = java.io.File(path2!!)
        assertThat(file2.exists()).isTrue()
        // Prior tab icon was deleted to avoid stale caching and disk bloat
        assertThat(file1.exists()).isFalse()
    }

    @Test
    fun deleteTabFavicons_removesAllFaviconsForTab() = kotlinx.coroutines.test.runTest {
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        val bitmap = android.graphics.Bitmap.createBitmap(16, 16, android.graphics.Bitmap.Config.ARGB_8888)

        val path = FaviconManager.saveFavicon(context, "test_tab_delete", bitmap)
        assertThat(path).isNotNull()
        val file = java.io.File(path!!)
        assertThat(file.exists()).isTrue()

        FaviconManager.deleteTabFavicons(context, "test_tab_delete")
        assertThat(file.exists()).isFalse()
    }

    @Test
    fun deleteTabFavicons_withoutContextParameter_removesFaviconsWhenInitialized() = kotlinx.coroutines.test.runTest {
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        FaviconManager.init(context)
        val bitmap = android.graphics.Bitmap.createBitmap(16, 16, android.graphics.Bitmap.Config.ARGB_8888)

        val path = FaviconManager.saveFavicon(context, "test_tab_no_ctx", bitmap)
        assertThat(path).isNotNull()
        val file = java.io.File(path!!)
        assertThat(file.exists()).isTrue()

        FaviconManager.deleteTabFavicons("test_tab_no_ctx")
        assertThat(file.exists()).isFalse()
    }

    @Test
    fun deleteAllFavicons_clearsEntireFaviconsDirectory() = kotlinx.coroutines.test.runTest {
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        FaviconManager.init(context)
        val bitmap = android.graphics.Bitmap.createBitmap(16, 16, android.graphics.Bitmap.Config.ARGB_8888)

        val path1 = FaviconManager.saveFavicon(context, "tab_1", bitmap)
        val path2 = FaviconManager.saveFavicon(context, "tab_2", bitmap)
        assertThat(java.io.File(path1!!).exists()).isTrue()
        assertThat(java.io.File(path2!!).exists()).isTrue()

        FaviconManager.deleteAllFavicons()
        assertThat(java.io.File(path1).exists()).isFalse()
        assertThat(java.io.File(path2).exists()).isFalse()
    }
}


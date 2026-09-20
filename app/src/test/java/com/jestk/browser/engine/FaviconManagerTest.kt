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
}


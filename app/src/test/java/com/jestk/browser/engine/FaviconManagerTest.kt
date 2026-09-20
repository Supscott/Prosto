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
    fun testCoilWithFilePathStringVsFile() {
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        val testFile = java.io.File(context.filesDir, "test.png")
        testFile.writeBytes(byteArrayOf(1, 2, 3))

        val imageLoader = coil.ImageLoader(context)
        val requestString = coil.request.ImageRequest.Builder(context).data(testFile.absolutePath).build()
        val requestFile = coil.request.ImageRequest.Builder(context).data(testFile).build()

        kotlinx.coroutines.runBlocking {
            val resultString = imageLoader.execute(requestString)
            println("resultString: ${resultString::class.java.simpleName}")
            if (resultString is coil.request.ErrorResult) {
                println("resultString error: ${resultString.throwable}")
            }
            val resultFile = imageLoader.execute(requestFile)
            println("resultFile: ${resultFile::class.java.simpleName}")
        }
    }
}

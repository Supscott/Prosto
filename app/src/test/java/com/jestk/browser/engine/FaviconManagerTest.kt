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

    @Test
    fun extractHost_normalizesAndSanitizesProperly() {
        assertThat(FaviconManager.extractHost("https://github.com/torvalds/linux")).isEqualTo("github.com")
        assertThat(FaviconManager.extractHost("http://www.google.com/search?q=kotlin")).isEqualTo("google.com")
        assertThat(FaviconManager.extractHost("duckduckgo.com")).isEqualTo("duckduckgo.com")
        assertThat(FaviconManager.extractHost("about:blank")).isNull()
        assertThat(FaviconManager.extractHost("http://localhost:8080")).isNull()
        assertThat(FaviconManager.extractHost("http://127.0.0.1")).isNull()
        assertThat(FaviconManager.extractHost("")).isNull()
        assertThat(FaviconManager.extractHost(null)).isNull()
    }

    @Test
    fun saveFavicon_withPageUrl_persistsAndIndexesDomainFavicon() = kotlinx.coroutines.test.runTest {
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        FaviconManager.init(context)
        val bitmap = android.graphics.Bitmap.createBitmap(16, 16, android.graphics.Bitmap.Config.ARGB_8888)

        val tabPath = FaviconManager.saveFavicon(context, "tab_host_test", bitmap, "https://github.com/trending")
        assertThat(tabPath).isNotNull()

        val cachedByUrl = FaviconManager.getCachedFaviconForUrl("https://github.com/stars")
        assertThat(cachedByUrl).isNotNull()
        assertThat(java.io.File(cachedByUrl!!).exists()).isTrue()
        assertThat(java.io.File(cachedByUrl).name).isEqualTo("domain_github.com.png")

        val cachedByDomain = FaviconManager.getCachedFaviconForDomain("github.com")
        assertThat(cachedByDomain).isEqualTo(cachedByUrl)
    }

    @Test
    fun cacheFaviconForDomain_savesAndIndexesDomainFaviconDirectly() = kotlinx.coroutines.test.runTest {
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        FaviconManager.init(context)
        val bitmap = android.graphics.Bitmap.createBitmap(16, 16, android.graphics.Bitmap.Config.ARGB_8888)

        val domainPath = FaviconManager.cacheFaviconForDomain("https://en.wikipedia.org/wiki/Android", bitmap, context)
        assertThat(domainPath).isNotNull()
        assertThat(java.io.File(domainPath!!).name).isEqualTo("domain_en.wikipedia.org.png")

        val cached = FaviconManager.getCachedFaviconForUrl("https://en.wikipedia.org/wiki/Kotlin")
        assertThat(cached).isEqualTo(domainPath)
    }

    @Test
    fun init_scansExistingDomainFaviconsIntoMemoryCache() = kotlinx.coroutines.test.runTest {
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        FaviconManager.deleteAllFavicons(context)
        FaviconManager.init(context)
        assertThat(FaviconManager.getCachedFaviconForDomain("example.com")).isNull()

        val dir = java.io.File(context.filesDir, "favicons").apply { if (!exists()) mkdirs() }
        val dummyFile = java.io.File(dir, "domain_example.com.png").apply {
            writeBytes("mock_png".toByteArray())
        }

        FaviconManager.init(context)
        val cached = FaviconManager.getCachedFaviconForDomain("example.com")
        assertThat(cached).isEqualTo(dummyFile.absolutePath)
    }

    @Test
    fun deleteTabFavicons_doesNotDeleteDomainFavicons() = kotlinx.coroutines.test.runTest {
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        FaviconManager.init(context)
        val bitmap = android.graphics.Bitmap.createBitmap(16, 16, android.graphics.Bitmap.Config.ARGB_8888)

        val tabPath = FaviconManager.saveFavicon(context, "tab_isolate", bitmap, "https://gitlab.com/repo")
        assertThat(tabPath).isNotNull()
        assertThat(java.io.File(tabPath!!).exists()).isTrue()

        val domainPath = FaviconManager.getCachedFaviconForDomain("gitlab.com")
        assertThat(domainPath).isNotNull()
        assertThat(java.io.File(domainPath!!).exists()).isTrue()

        FaviconManager.deleteTabFavicons(context, "tab_isolate")
        assertThat(java.io.File(tabPath).exists()).isFalse()
        assertThat(java.io.File(domainPath).exists()).isTrue()
        assertThat(FaviconManager.getCachedFaviconForDomain("gitlab.com")).isEqualTo(domainPath)
    }

    @Test
    fun deleteAllFavicons_clearsDomainCacheAndFiles() = kotlinx.coroutines.test.runTest {
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        FaviconManager.init(context)
        val bitmap = android.graphics.Bitmap.createBitmap(16, 16, android.graphics.Bitmap.Config.ARGB_8888)

        FaviconManager.saveFavicon(context, "tab_purge", bitmap, "https://purge-site.com")
        assertThat(FaviconManager.getCachedFaviconForDomain("purge-site.com")).isNotNull()

        FaviconManager.deleteAllFavicons(context)
        assertThat(FaviconManager.getCachedFaviconForDomain("purge-site.com")).isNull()
    }

    @Test
    fun isValidLocalFavicon_identifiesLocalFilesAndAssets() {
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        val dummyFile = java.io.File(context.filesDir, "test_valid.png").apply {
            writeBytes("data".toByteArray())
        }

        assertThat(FaviconManager.isValidLocalFavicon(dummyFile.absolutePath)).isTrue()
        assertThat(FaviconManager.isValidLocalFavicon("file://${dummyFile.absolutePath}")).isTrue()
        assertThat(FaviconManager.isValidLocalFavicon("/nonexistent/file.png")).isFalse()
        assertThat(FaviconManager.isValidLocalFavicon("file:///android_asset/icons/google.svg")).isTrue()
        assertThat(FaviconManager.isValidLocalFavicon("/android_asset/icons/google.svg")).isTrue()
        assertThat(FaviconManager.isValidLocalFavicon("https://example.com/icon.png")).isFalse()
        assertThat(FaviconManager.isValidLocalFavicon(null)).isFalse()
        assertThat(FaviconManager.isValidLocalFavicon("")).isFalse()
    }
}


package com.jestk.browser.engine

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.jestk.browser.domain.model.SearchEngine
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@RunWith(RobolectricTestRunner::class)
class SearchEngineIconDownloaderTest {

    private lateinit var context: Context

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
    }

    @After
    fun tearDown() {
        val dir = File(context.filesDir, "search_icons")
        if (dir.exists()) {
            dir.deleteRecursively()
        }
    }

    @Test
    fun getIconFile_returnsCorrectDestinationPath() {
        val file = SearchEngineIconDownloader.getIconFile(context, "brave")
        assertThat(file.name).isEqualTo("engine_brave.png")
        assertThat(file.parentFile?.name).isEqualTo("search_icons")
    }

    @Test
    fun downloadIconForEngine_whenFileAlreadyExists_returnsExistingPathWithoutNetwork() = runTest {
        val file = SearchEngineIconDownloader.getIconFile(context, "google")
        file.parentFile?.mkdirs()
        file.writeBytes(byteArrayOf(1, 2, 3, 4))

        val engine = SearchEngine.GOOGLE
        val result = SearchEngineIconDownloader.downloadIconForEngine(context, engine)

        assertThat(result).isEqualTo(file.absolutePath)
        assertThat(File(result!!).length()).isEqualTo(4)
    }

    @Test
    fun downloadIcons_withExistingFiles_mapsEnginesToLocalPaths() = runTest {
        val braveFile = SearchEngineIconDownloader.getIconFile(context, "brave")
        val kagiFile = SearchEngineIconDownloader.getIconFile(context, "kagi")
        braveFile.parentFile?.mkdirs()
        braveFile.writeBytes(byteArrayOf(1, 2, 3))
        kagiFile.writeBytes(byteArrayOf(4, 5, 6))

        val map = SearchEngineIconDownloader.downloadIcons(
            context,
            listOf(SearchEngine.BRAVE, SearchEngine.KAGI)
        )

        assertThat(map["brave"]).isEqualTo(braveFile.absolutePath)
        assertThat(map["kagi"]).isEqualTo(kagiFile.absolutePath)
    }
}

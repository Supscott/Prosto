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

    @Test
    fun downloadIconForEngine_fetchesOverHttpAndFollowsRedirect() = runTest {
        val serverSocket = java.net.ServerSocket(0)
        val port = serverSocket.localPort

        val thread = Thread {
            try {
                // Request 1: Redirect to /icon.png
                val client1 = serverSocket.accept()
                val reader1 = client1.getInputStream().bufferedReader()
                var line1 = reader1.readLine()
                while (!line1.isNullOrEmpty()) {
                    line1 = reader1.readLine()
                }
                val out1 = client1.getOutputStream()
                out1.write("HTTP/1.1 302 Found\r\nLocation: http://127.0.0.1:$port/icon.png\r\nContent-Length: 0\r\n\r\n".toByteArray())
                out1.flush()
                client1.close()

                // Request 2: Return icon bytes
                val client2 = serverSocket.accept()
                val reader2 = client2.getInputStream().bufferedReader()
                var line2 = reader2.readLine()
                while (!line2.isNullOrEmpty()) {
                    line2 = reader2.readLine()
                }
                val out2 = client2.getOutputStream()
                val body = byteArrayOf(10, 20, 30, 40)
                out2.write("HTTP/1.1 200 OK\r\nContent-Length: ${body.size}\r\nContent-Type: image/png\r\n\r\n".toByteArray())
                out2.write(body)
                out2.flush()
                client2.close()
            } catch (_: Exception) {}
        }
        thread.start()

        try {
            val engine = SearchEngine(
                id = "custom_test",
                displayName = "Test",
                searchUrlTemplate = "http://127.0.0.1:$port/search?q=%s",
                iconUrl = "http://127.0.0.1:$port/redirect",
            )
            val result = SearchEngineIconDownloader.downloadIconForEngine(context, engine)
            assertThat(result).isNotNull()
            val file = File(result!!)
            assertThat(file.exists()).isTrue()
            assertThat(file.readBytes()).isEqualTo(byteArrayOf(10, 20, 30, 40))
        } finally {
            serverSocket.close()
            thread.join(1000)
        }
    }
}

package com.jestk.browser.engine.adblock

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.testing.TestListenableWorkerBuilder
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@RunWith(RobolectricTestRunner::class)
class FilterListUpdateWorkerTest {
    private lateinit var context: Context
    private val server = MockWebServer()
    private val store = EngineStore(NativeBackend({ true }, { "v1" }, ListTextEngineFactory()))

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        File(context.filesDir, "adblock").deleteRecursively()
        context.getSharedPreferences(FilterListController.PREFS, Context.MODE_PRIVATE).edit().clear().commit()
        server.start()
        val controller = FilterListController(
            context = context,
            catalog = FilterListCatalog(
                listOf(
                    ListEntry("easylist", listOf("ads"), ProtectionPreset.STANDARD, server.url("/easylist.txt").toString()),
                    ListEntry("easyprivacy", listOf("privacy"), ProtectionPreset.STANDARD, server.url("/easyprivacy.txt").toString()),
                ),
            ),
            updater = FilterListUpdater(OkHttpClient(), File(context.filesDir, "adblock/lists"), requireHttps = false),
            storeProvider = { store },
            prefs = context.getSharedPreferences(FilterListController.PREFS, Context.MODE_PRIVATE),
            smoke = SmokeCheck(listOf("https://ads.example/a.js"), listOf("https://track.example/t.js"), listOf("https://clean.example/")),
        )
        controller.attach(store)
        FilterListServices.override = controller
    }

    @After
    fun tearDown() {
        FilterListServices.override = null
        server.shutdown()
    }

    private fun worker(attempt: Int = 0) =
        TestListenableWorkerBuilder<FilterListUpdateWorker>(context).setRunAttemptCount(attempt).build()

    @Test
    fun success_updatesAndSwapsTheEngines() = runBlocking {
        server.enqueue(MockResponse().setBody("! t\n||ads.example^\n"))
        server.enqueue(MockResponse().setBody("! t\n||track.example^\n"))

        val result = worker().doWork()

        assertThat(result).isEqualTo(ListenableWorker.Result.success())
        assertThat(store.current()).isNotNull()
    }

    @Test
    fun failure_retriesAFewTimesThenGivesUpUntilTheNextPeriod() = runBlocking {
        repeat(2) { server.enqueue(MockResponse().setResponseCode(503)) }
        assertThat(worker(attempt = 0).doWork()).isEqualTo(ListenableWorker.Result.retry())

        repeat(2) { server.enqueue(MockResponse().setResponseCode(503)) }
        assertThat(worker(attempt = 3).doWork()).isEqualTo(ListenableWorker.Result.success())
    }
}

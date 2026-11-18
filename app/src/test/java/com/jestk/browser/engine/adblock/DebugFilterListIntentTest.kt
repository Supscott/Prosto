package com.jestk.browser.engine.adblock

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
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
class DebugFilterListIntentTest {
    private lateinit var context: Context
    private lateinit var server: MockWebServer
    private lateinit var store: EngineStore
    private lateinit var controller: FilterListController
    private lateinit var updater: FilterListUpdater

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        File(context.filesDir, "adblock").deleteRecursively()
        context.getSharedPreferences(FilterListController.PREFS, Context.MODE_PRIVATE).edit().clear().commit()
        server = MockWebServer().apply {
            enqueue(MockResponse().setBody("[Adblock Plus 2.0]\n! Title: fx\n||fx.example^\n"))
            start()
        }
        store = EngineStore(NativeBackend(load = { true }, version = { "v1" }, factory = ListTextEngineFactory()))
        updater = FilterListUpdater(OkHttpClient(), File(context.filesDir, "adblock/lists"), requireHttps = false)
        controller = FilterListController(
            context = context,
            catalog = FilterListCatalog(emptyList()),
            updater = updater,
            storeProvider = { store },
            prefs = context.getSharedPreferences(FilterListController.PREFS, Context.MODE_PRIVATE),
            smoke = SmokeCheck(listOf("https://fx.example/a.js"), listOf("https://fx.example/a.js"), listOf("https://clean.example/x.png")),
            debugUpdater = updater,
        )
        controller.attach(store)
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun setDebuggable(on: Boolean) {
        val flags = context.applicationInfo.flags
        context.applicationInfo.flags = if (on) flags or ApplicationInfo.FLAG_DEBUGGABLE else flags and ApplicationInfo.FLAG_DEBUGGABLE.inv()
    }

    private fun intent(url: String) =
        Intent(Intent.ACTION_VIEW, Uri.parse("prosto-debug://add-list").buildUpon().appendQueryParameter("url", url).build())

    private fun extraIds() = controller.selection().extras.values.flatten().map { it.id }

    @Test
    fun debuggable_storesTheListAndSelectionIncludesIt() = runBlocking {
        setDebuggable(true)

        val ok = DebugFilterListIntent.handle(context, intent(server.url("/f.txt").toString()), controller)

        assertThat(ok).isTrue()
        assertThat(extraIds()).contains(FilterListController.DEBUG_LIST_ID)
    }

    @Test
    fun emptyUrl_removesTheList() = runBlocking {
        setDebuggable(true)
        DebugFilterListIntent.handle(context, intent(server.url("/f.txt").toString()), controller)

        DebugFilterListIntent.handle(context, intent(""), controller)

        assertThat(extraIds()).doesNotContain(FilterListController.DEBUG_LIST_ID)
        assertThat(updater.has(FilterListController.DEBUG_LIST_ID)).isFalse()
    }

    @Test
    fun nonDebuggable_ignoresTheIntent() = runBlocking {
        setDebuggable(false)

        val ok = DebugFilterListIntent.handle(context, intent(server.url("/f.txt").toString()), controller)

        assertThat(ok).isFalse()
        assertThat(server.requestCount).isEqualTo(0)
        assertThat(extraIds()).doesNotContain(FilterListController.DEBUG_LIST_ID)
    }
}

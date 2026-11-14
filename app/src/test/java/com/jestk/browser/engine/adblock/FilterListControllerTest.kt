package com.jestk.browser.engine.adblock

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@RunWith(RobolectricTestRunner::class)
class FilterListControllerTest {
    private lateinit var context: Context
    private lateinit var server: MockWebServer
    private lateinit var factory: ListTextEngineFactory
    private lateinit var store: EngineStore
    private lateinit var controller: FilterListController
    private lateinit var listsDir: File

    private val bodies = mutableMapOf<String, String>()

    private val smoke = SmokeCheck(
        blockedAds = listOf("https://ads.example/a.js"),
        blockedTrackers = listOf("https://track.example/t.js"),
        allowed = listOf("https://clean.example/x.png"),
    )

    private fun list(vararg hosts: String) = "[Adblock Plus 2.0]\n! Title: test\n" + hosts.joinToString("") { "||$it^\n" }

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        File(context.filesDir, "adblock").deleteRecursively()
        context.getSharedPreferences(FilterListController.PREFS, Context.MODE_PRIVATE).edit().clear().commit()
        server = MockWebServer().apply {
            dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    val body = bodies[request.path] ?: return MockResponse().setResponseCode(404)
                    return MockResponse().setBody(body)
                }
            }
            start()
        }
        bodies["/easylist.txt"] = list("ads.example")
        bodies["/easyprivacy.txt"] = list("track.example")
        bodies["/strict-ads.txt"] = list("strict-ads.example")
        bodies["/strict-privacy.txt"] = list("strict-track.example")
        factory = ListTextEngineFactory()
        store = EngineStore(NativeBackend(load = { true }, version = { "v1" }, factory = factory))
        listsDir = File(context.filesDir, "adblock/lists")
        controller = newController()
        controller.attach(store)
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun entry(id: String, engine: String, preset: ProtectionPreset) =
        ListEntry(id, listOf(engine), preset, server.url("/$id.txt").toString())

    private fun newController() = FilterListController(
        context = context,
        catalog = FilterListCatalog(
            listOf(
                entry("easylist", "ads", ProtectionPreset.STANDARD),
                entry("easyprivacy", "privacy", ProtectionPreset.STANDARD),
                entry("strict-ads", "ads", ProtectionPreset.STRICT),
                entry("strict-privacy", "privacy", ProtectionPreset.STRICT),
            ),
        ),
        updater = FilterListUpdater(OkHttpClient(), listsDir, requireHttps = false),
        storeProvider = { store },
        prefs = context.getSharedPreferences(FilterListController.PREFS, Context.MODE_PRIVATE),
        smoke = smoke,
    )

    private fun EngineSet.blocks(url: String) = ads.check(url, "https://site.test/", RequestType.SCRIPT).shouldBlock ||
        privacy.check(url, "https://site.test/", RequestType.SCRIPT).shouldBlock

    private fun publishBundled(): EngineSet = store.load(context)!!.also { store.swap(it) }

    @Test
    fun refresh_buildsSmokeChecksAndSwapsInTheDownloadedLists() = runBlocking {
        val old = publishBundled()

        val ok = controller.refresh()

        assertThat(ok).isTrue()
        val live = store.current()!!
        assertThat(live).isNotSameInstanceAs(old)
        assertThat(live.blocks("https://ads.example/a.js")).isTrue()
        assertThat(live.blocks("https://track.example/t.js")).isTrue()
        assertThat((old.ads as ListTextEngineFactory.TextMatcher).closed).isTrue()
        val state = controller.state.value
        assertThat(state.isUpdating).isFalse()
        assertThat(state.lastError).isNull()
        assertThat(state.lastUpdatedAt).isNotNull()
        assertThat(state.ruleCount).isEqualTo(live.rulesCount)
        assertThat(listsDir.listFiles().orEmpty().filter { it.name.endsWith(".prev") }).isEmpty()
    }

    @Test
    fun secondRefreshWithNothingNew_doesNotRebuild() = runBlocking {
        publishBundled()
        controller.refresh()
        val compiles = factory.compiles.get()

        controller.refresh()

        assertThat(factory.compiles.get()).isEqualTo(compiles)
    }

    @Test
    fun downloadedListsAreLoadedAtStartup() = runBlocking {
        controller.refresh()
        val restarted = EngineStore(NativeBackend(load = { true }, version = { "v1" }, factory = ListTextEngineFactory()))
        newController().attach(restarted)

        val set = restarted.load(context)!!

        assertThat(set.blocks("https://ads.example/a.js")).isTrue()
    }

    @Test
    fun failedSmokeCheck_keepsTheLiveEnginesAndDeletesTheBadFile() = runBlocking {
        controller.refresh()
        val live = store.current()!!
        bodies["/easylist.txt"] = list("something-else.example")

        val ok = controller.refresh()

        assertThat(ok).isFalse()
        assertThat(controller.state.value.lastError).contains("smoke check failed")
        assertThat(store.current()).isSameInstanceAs(live)
        assertThat((live.ads as ListTextEngineFactory.TextMatcher).closed).isFalse()
        assertThat(File(listsDir, "easylist.txt").readText()).contains("ads.example")
        assertThat(listsDir.listFiles().orEmpty().filter { it.name.endsWith(".prev") }).isEmpty()
    }

    @Test
    fun failedFirstDownload_deletesTheBadFile() = runBlocking {
        bodies["/easylist.txt"] = list("something-else.example")

        controller.refresh()

        assertThat(File(listsDir, "easylist.txt").exists()).isFalse()
        assertThat(File(listsDir, "easylist.meta").exists()).isFalse()
    }

    @Test
    fun invalidDownload_isReportedAndTheEngineStaysUntouched() = runBlocking {
        val old = publishBundled()
        bodies["/easylist.txt"] = "<html>captive portal</html>"

        val ok = controller.refresh()

        assertThat(ok).isFalse()
        assertThat(controller.state.value.lastError).contains("easylist")
        assertThat(store.current()).isSameInstanceAs(old)
    }

    @Test
    fun strict_isDownloadedOnDemandAndDroppedBackToStandard() = runBlocking {
        controller.refresh()
        assertThat(File(listsDir, "strict-ads.txt").exists()).isFalse()
        assertThat(store.current()!!.blocks("https://strict-ads.example/a.js")).isFalse()

        controller.setPreset(ProtectionPreset.STRICT)
        val ok = controller.downloadPreset(ProtectionPreset.STRICT)

        assertThat(ok).isTrue()
        assertThat(controller.state.value.strictReady).isTrue()
        assertThat(store.current()!!.blocks("https://strict-ads.example/a.js")).isTrue()
        assertThat(store.current()!!.blocks("https://strict-track.example/t.js")).isTrue()
        assertThat(store.current()!!.blocks("https://ads.example/a.js")).isTrue()

        controller.setPreset(ProtectionPreset.STANDARD)

        assertThat(File(listsDir, "strict-ads.txt").exists()).isFalse()
        assertThat(File(listsDir, "strict-privacy.txt").exists()).isFalse()
        assertThat(store.current()!!.blocks("https://strict-ads.example/a.js")).isFalse()
        assertThat(store.current()!!.blocks("https://ads.example/a.js")).isTrue()
        assertThat(File(context.filesDir, "adblock").listFiles().orEmpty().filter { "-strict-" in it.name }).isEmpty()
        assertThat(controller.state.value.strictReady).isFalse()
    }

    @Test
    fun strictWithAMissingList_behavesLikeStandard() = runBlocking {
        bodies.remove("/strict-privacy.txt")
        controller.setPreset(ProtectionPreset.STRICT)

        val ok = controller.refresh()

        assertThat(ok).isFalse()
        assertThat(controller.state.value.strictReady).isFalse()
        assertThat(controller.selection().preset).isEqualTo("standard")
        assertThat(store.current()!!.blocks("https://strict-ads.example/a.js")).isFalse()
        assertThat(store.current()!!.blocks("https://ads.example/a.js")).isTrue()
    }

    @Test
    fun presetIsPersisted() = runBlocking {
        controller.setPreset(ProtectionPreset.STRICT)

        assertThat(newController().preset()).isEqualTo(ProtectionPreset.STRICT)
        assertThat(controller.state.value.preset).isEqualTo(ProtectionPreset.STRICT)
    }
}

package com.jestk.browser.engine.adblock

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@RunWith(RobolectricTestRunner::class)
class EngineStoreSelectionTest {
    private lateinit var context: Context
    private val cacheDir get() = File(context.filesDir, "adblock")

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        cacheDir.deleteRecursively()
    }

    private fun store(factory: EngineFactory) = EngineStore(NativeBackend({ true }, { "v1" }, factory))

    private fun blobs() = cacheDir.listFiles().orEmpty().filter { it.name.endsWith(".bin") }.map { it.name }

    private fun extra(id: String, text: String) = ExtraList(id, text, id + text.length)

    @Test
    fun downloadedList_overridesTheBundledCopyWithTheSameId() {
        val factory = FakeEngineFactory()
        val extras = mapOf("ads" to listOf(extra("easylist", "||replaced.example^\n")))

        store(factory).load(context, ListSelection("standard", extras))!!

        val bundled = FilterAssets(context.assets, "standard").readById("ads")
        val ads = factory.compiledLists.first()
        assertThat(ads).hasSize(bundled.size)
        assertThat(ads).contains("||replaced.example^\n")
        assertThat(ads).doesNotContain(bundled.getValue("easylist"))
        assertThat(ads).containsAtLeastElementsIn(bundled.filterKeys { it != "easylist" }.values)
    }

    @Test
    fun strictAddsDownloadedListsOnTopOfTheBundledStandardLists() {
        val extras = mapOf("ads" to listOf(extra("ubo-annoyances", "||annoying.example^\n")))

        val set = store(ListTextEngineFactory()).load(context, ListSelection("strict", extras))!!

        assertThat(set.ads.check("https://annoying.example/a", "https://s.test/", RequestType.SCRIPT).shouldBlock).isTrue()
        assertThat(set.ads.check("https://pagead2.googlesyndication.com/x", "https://s.test/", RequestType.SCRIPT).shouldBlock).isTrue()
    }

    @Test
    fun presetsAndContentsHaveSeparateCacheKeys_andStandardSurvivesStrict() {
        val factory = FakeEngineFactory()
        val extras = mapOf("ads" to listOf(extra("ubo-annoyances", "||a.example^\n")))
        store(factory).load(context, ListSelection.bundled())
        store(factory).load(context, ListSelection("strict", extras))

        assertThat(blobs().filter { it.startsWith("ads-standard-") }).hasSize(1)
        assertThat(blobs().filter { it.startsWith("ads-strict-") }).hasSize(1)

        val changed = mapOf("ads" to listOf(extra("ubo-annoyances", "||b.example^\n!changed\n")))
        store(factory).load(context, ListSelection("strict", changed))

        assertThat(blobs().filter { it.startsWith("ads-strict-") }).hasSize(1)
        assertThat(blobs().filter { it.startsWith("ads-standard-") }).hasSize(1)
    }

    @Test
    fun cachedStrictBlob_isReusedWithoutCompiling() {
        val extras = mapOf("ads" to listOf(extra("ubo-annoyances", "||a.example^\n")))
        val selection = ListSelection("strict", extras)
        store(FakeEngineFactory()).load(context, selection)
        val factory = FakeEngineFactory()

        store(factory).load(context, selection)

        assertThat(factory.compiles).isEqualTo(0)
        assertThat(factory.restores).isEqualTo(3)
    }

    @Test
    fun purgeAndDeleteCache_removeTheBlobs() {
        val store = store(FakeEngineFactory())
        val extras = mapOf("ads" to listOf(extra("ubo-annoyances", "||a.example^\n")))
        val strict = ListSelection("strict", extras)
        store.load(context, ListSelection.bundled())
        store.load(context, strict)

        store.purge(context, strict)
        assertThat(blobs().filter { it.startsWith("ads-strict-") }).isEmpty()

        store.load(context, strict)
        store.deleteCache(context, "strict")
        assertThat(blobs().filter { "-strict-" in it }).isEmpty()
        assertThat(blobs().filter { it.startsWith("ads-standard-") }).hasSize(1)
    }

    @Test
    fun catalogAsset_listsEveryBundledStandardList() {
        val catalog = FilterListCatalog.load(context.assets)
        val assets = FilterAssets(context.assets, "standard")

        for (engine in listOf("ads", "privacy")) {
            // Hand-edited lists ("local": true in tools/filterlists/lists.json) ship only in the APK.
            val ids = assets.names(engine).map { it.removeSuffix(".txt") }.toSet() - LOCAL_LISTS
            val cataloged = catalog.forPreset(ProtectionPreset.STANDARD).filter { engine in it.engines }.map { it.id }.toSet()
            assertThat(cataloged).isEqualTo(ids)
        }
        assertThat(catalog.downloadOnly()).isNotEmpty()
        assertThat(catalog.entries.all { it.url.startsWith("https://") }).isTrue()
    }

    private companion object {
        val LOCAL_LISTS = setOf("prosto")
    }
}

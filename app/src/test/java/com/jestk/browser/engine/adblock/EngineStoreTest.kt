package com.jestk.browser.engine.adblock

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.util.concurrent.Executors

@RunWith(RobolectricTestRunner::class)
class EngineStoreTest {
    private lateinit var context: Context
    private val executor = Executors.newSingleThreadExecutor { Thread(it, "engine-store-test") }

    private val cacheDir get() = File(context.filesDir, "adblock")

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        cacheDir.deleteRecursively()
    }

    @After
    fun tearDown() {
        executor.shutdownNow()
    }

    private fun backend(factory: EngineFactory, version: String = "v1") =
        NativeBackend(load = { true }, version = { version }, factory = factory)

    private fun blobs(engine: String) = cacheDir.listFiles().orEmpty().filter { it.name.startsWith("$engine-") && it.name.endsWith(".bin") }

    @Test
    fun firstRun_compilesTheEnginesAndWritesTheBlobs() {
        val factory = FakeEngineFactory()

        val set = EngineStore(backend(factory)).load(context)

        assertThat(set).isNotNull()
        assertThat(factory.compiles).isEqualTo(3)
        assertThat(factory.restores).isEqualTo(0)
        assertThat(set!!.rulesCount).isGreaterThan(0)
        for (engine in listOf("ads", "privacy")) {
            val blob = blobs(engine).single()
            assertThat(blob.name).startsWith("$engine-standard-v1-")
            assertThat(blob.readBytes()).isEqualTo(FakeEngineFactory.BLOB)
        }
        assertThat(cacheDir.listFiles().orEmpty().filter { it.name.endsWith(".tmp") }).isEmpty()
    }

    @Test
    fun popupEngine_isCompiledFromTheAdsListsPopupRulesAsDocumentRules() {
        val factory = FakeEngineFactory()

        val set = EngineStore(backend(factory)).load(context)!!

        assertThat(set.popup).isNotNull()
        assertThat(blobs("popup")).hasSize(1)
        val popupLists = factory.compiledLists.last()
        assertThat(popupLists).hasSize(1)
        val rules = popupLists.single().lines().filter { it.isNotBlank() }
        assertThat(rules).isNotEmpty()
        assertThat(rules.all { "\$" + "document" in it }).isTrue()
        assertThat(rules.none { "popup" in it.substringAfterLast('$') }).isTrue()
    }

    @Test
    fun secondRun_loadsTheBlobWithoutCompilingAndKeepsTheRuleCount() {
        val first = EngineStore(backend(FakeEngineFactory())).load(context)!!
        val factory = FakeEngineFactory()

        val second = EngineStore(backend(factory)).load(context)!!

        assertThat(factory.compiles).isEqualTo(0)
        assertThat(factory.restores).isEqualTo(3)
        assertThat(second.rulesCount).isEqualTo(first.rulesCount)
    }

    @Test
    fun corruptBlob_isDeletedAndRecompiled() {
        EngineStore(backend(FakeEngineFactory())).load(context)
        blobs("ads").single().writeText("garbage")
        val factory = FakeEngineFactory()

        val set = EngineStore(backend(factory)).load(context)

        assertThat(set).isNotNull()
        assertThat(factory.compiles).isEqualTo(1)
        assertThat(factory.restores).isEqualTo(2)
        assertThat(blobs("ads").single().readBytes()).isEqualTo(FakeEngineFactory.BLOB)
    }

    @Test
    fun nativeVersionChange_recompilesAndDeletesTheStaleFiles() {
        EngineStore(backend(FakeEngineFactory(), "v1")).load(context)
        val factory = FakeEngineFactory()

        EngineStore(backend(factory, "v2")).load(context)

        assertThat(factory.compiles).isEqualTo(3)
        val names = cacheDir.listFiles().orEmpty().map { it.name }
        assertThat(names).isNotEmpty()
        assertThat(names.filter { "-v1-" in it }).isEmpty()
        assertThat(names.filter { "-v2-" in it }).isNotEmpty()
    }

    @Test
    fun failedEngine_publishesNothingAndClosesTheOther() {
        val factory = FakeEngineFactory(failAttempt = 2)

        val store = EngineStore(backend(factory))

        assertThat(store.load(context)).isNull()
        assertThat(store.current()).isNull()
        assertThat(factory.matchers).hasSize(1)
        assertThat(factory.matchers.single().closed).isTrue()
    }

    @Test
    fun swap_publishesTheNewSetAndClosesTheOldOne() {
        val store = EngineStore(backend(FakeEngineFactory()))
        val old = EngineSet(FakeContentMatcher(), FakeContentMatcher(), 1)
        val next = EngineSet(FakeContentMatcher(), FakeContentMatcher(), 2)

        store.swap(old)
        assertThat(store.current()).isSameInstanceAs(old)
        store.swap(next)

        assertThat(store.current()).isSameInstanceAs(next)
        assertThat((old.ads as FakeContentMatcher).closed).isTrue()
        assertThat((old.privacy as FakeContentMatcher).closed).isTrue()
        assertThat((next.ads as FakeContentMatcher).closed).isFalse()
    }

    @Test
    fun swappingTheSameSetKeepsItOpen() {
        val store = EngineStore(backend(FakeEngineFactory()))
        val set = EngineSet(FakeContentMatcher(), FakeContentMatcher(), 1)

        store.swap(set)
        store.swap(set)

        assertThat((set.ads as FakeContentMatcher).closed).isFalse()
    }

    @Test
    fun initialize_neverRunsOnTheCallingThread() = runBlocking {
        val factory = FakeEngineFactory()
        val store = EngineStore(backend(factory))
        val scope = CoroutineScope(SupervisorJob())

        store.initialize(context, scope, executor.asCoroutineDispatcher()).join()

        assertThat(store.current()).isNotNull()
        assertThat(factory.threads).isNotEmpty()
        assertThat(factory.threads.map { it.name }.toSet()).containsExactly("engine-store-test")
        assertThat(factory.threads).doesNotContain(Thread.currentThread())
    }

    @Test
    fun listsHash_followsTheListAssets() {
        val assets = FilterAssets(context.assets, "standard")

        assertThat(assets.listsHash("ads")).hasLength(16)
        assertThat(assets.listsHash("ads")).isEqualTo(assets.listsHash("ads"))
        assertThat(assets.listsHash("ads")).isNotEqualTo(assets.listsHash("privacy"))
    }

    @Test
    fun countRules_skipsCommentsHeadersAndBlankLines() {
        val text = "[Adblock Plus 2.0]\n! comment\n\n||ads.example^\n##.banner\n"

        assertThat(FilterAssets.countRules(listOf(text, "||x^"))).isEqualTo(3)
    }
}

package com.jestk.browser.engine.adblock

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import java.io.File
import java.util.concurrent.TimeUnit

/** What the settings screen shows (CB-15). [lastUpdatedAt] is the newest download check, null if never. */
internal data class ListUpdateState(
    val lastUpdatedAt: Long? = null,
    val ruleCount: Int = 0,
    val isUpdating: Boolean = false,
    val lastError: String? = null,
    val preset: ProtectionPreset = ProtectionPreset.STANDARD,
    val strictReady: Boolean = false,
)

/** A URL pair per engine the freshly built engines must get right before they replace the live ones. */
internal class SmokeCheck(
    private val blockedAds: List<String>,
    private val blockedTrackers: List<String>,
    private val allowed: List<String>,
    private val sourceUrl: String = "https://example.org/",
) {
    /** Blocks at least one known ad and one known tracker URL and lets every clean URL through. */
    fun passes(set: EngineSet): Boolean =
        runCatching {
            blockedAds.any { blocks(set.ads, it) } &&
                blockedTrackers.any { blocks(set.privacy, it) } &&
                allowed.none { blocks(set.ads, it) || blocks(set.privacy, it) }
        }.getOrDefault(false)

    private fun blocks(matcher: ContentMatcher, url: String) =
        matcher.check(url, sourceUrl, RequestType.SCRIPT).shouldBlock

    companion object {
        val DEFAULT = SmokeCheck(
            blockedAds = listOf(
                "https://pagead2.googlesyndication.com/pagead/js/adsbygoogle.js",
                "https://securepubads.g.doubleclick.net/tag/js/gpt.js",
            ),
            blockedTrackers = listOf(
                "https://sb.scorecardresearch.com/beacon.js",
                "https://www.google-analytics.com/analytics.js",
            ),
            allowed = listOf(
                "https://en.wikipedia.org/static/apple-touch/wikipedia.png",
                "https://www.mozilla.org/media/css/protocol.css",
            ),
        )
    }
}

/**
 * Keeps the filter lists fresh and swaps the engines atomically. One refresh at a time. Per
 * refresh: conditional downloads, then (only if a list changed or the effective list set differs
 * from the published one) build a new [EngineSet] off the caller thread, run the [SmokeCheck],
 * publish it through [EngineStore.swap]. Any failure keeps the live engines and rolls the changed
 * files back. Resources and scriptlets are not updated remotely: they depend on the native
 * library and the app version, so they only change with an app update.
 */
internal class FilterListController(
    private val context: Context,
    private val catalog: FilterListCatalog,
    private val updater: FilterListUpdater,
    private val storeProvider: () -> EngineStore,
    private val prefs: SharedPreferences,
    private val smoke: SmokeCheck = SmokeCheck.DEFAULT,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    private val mutex = Mutex()
    private val mutableState = MutableStateFlow(ListUpdateState(preset = preset()))

    @Volatile
    private var publishedFingerprint: String? = null

    val state: StateFlow<ListUpdateState> = mutableState.asStateFlow()

    fun preset(): ProtectionPreset = ProtectionPreset.fromId(prefs.getString(KEY_PRESET, null))

    /** Registers this controller with [store]: startup loads the downloaded lists, swaps update the rule count. */
    fun attach(store: EngineStore) {
        store.selectionProvider = ::selectionForStore
        store.onSwap = { set -> if (set != null) mutableState.update { it.copy(ruleCount = set.rulesCount) } }
    }

    /** Called by the store when it loads the engines; remembers what is published. */
    fun selectionForStore(): ListSelection = selection().also { publishedFingerprint = it.fingerprint }

    /** The lists the engines should be built from now. Strict applies only once all its lists are on disk. */
    fun selection(): ListSelection {
        val strictReady = isStrictReady()
        val effective = if (preset() == ProtectionPreset.STRICT && strictReady) ProtectionPreset.STRICT else ProtectionPreset.STANDARD
        val texts = HashMap<String, StoredList?>()
        val extras = HashMap<String, MutableList<ExtraList>>()
        for (entry in catalog.forPreset(effective)) {
            val stored = texts.getOrPut(entry.id) { updater.read(entry.id) } ?: continue
            for (engine in entry.engines) {
                extras.getOrPut(engine) { ArrayList() } += ExtraList(entry.id, stored.text, stored.digest)
            }
        }
        return ListSelection(effective.id, extras)
    }

    /** Persists the protection level. Dropping Strict deletes its lists and cached engines and rebuilds. */
    suspend fun setPreset(preset: ProtectionPreset) {
        mutex.withLock {
            prefs.edit().putString(KEY_PRESET, preset.id).apply()
            publishState { it.copy(preset = preset) }
            if (preset == ProtectionPreset.STANDARD) {
                withContext(dispatcher) {
                    updater.remove(catalog.downloadOnly().map { it.id })
                    storeProvider().deleteCache(context, ProtectionPreset.STRICT.id)
                    rebuildIfNeeded(emptyList())
                }
                publishState { it.copy(strictReady = false, lastUpdatedAt = updater.lastFetchedAt()) }
            }
        }
    }

    /** Downloads the lists of [preset] (the Strict ones are not in the APK) and rebuilds. Returns true on success. */
    suspend fun downloadPreset(preset: ProtectionPreset): Boolean = run(catalog.forPreset(preset), force = false)

    /** Refreshes every list active at the current protection level. Returns true when nothing failed. */
    suspend fun refresh(force: Boolean = false): Boolean = run(catalog.forPreset(preset()), force)

    private suspend fun run(entries: List<ListEntry>, force: Boolean): Boolean =
        mutex.withLock {
            publishState { it.copy(isUpdating = true) }
            val error = withContext(dispatcher) {
                val result = updater.update(entries, force)
                val rebuildError = rebuildIfNeeded(result.changed)
                result.failures.entries.joinToString("; ") { "${it.key}: ${it.value}" }.ifEmpty { null } ?: rebuildError
            }
            publishState {
                it.copy(isUpdating = false, lastError = error, lastUpdatedAt = updater.lastFetchedAt(), strictReady = isStrictReady())
            }
            error == null
        }

    /** Builds, verifies and publishes new engines when needed; returns an error text on failure. */
    private fun rebuildIfNeeded(changed: List<String>): String? {
        val selection = selection()
        if (selection.fingerprint == publishedFingerprint) {
            updater.commit()
            return null
        }
        val store = storeProvider()
        val set = runCatching { store.load(context, selection) }.getOrNull()
        if (set == null || !smoke.passes(set)) {
            set?.close()
            store.purge(context, selection)
            updater.rollback(changed)
            return if (set == null) "engines failed to build" else "smoke check failed"
        }
        store.swap(set)
        updater.commit()
        publishedFingerprint = selection.fingerprint
        mutableState.update { it.copy(ruleCount = set.rulesCount) }
        return null
    }

    private fun isStrictReady(): Boolean = catalog.downloadOnly().let { lists -> lists.isNotEmpty() && lists.all { updater.has(it.id) } }

    private fun publishState(transform: (ListUpdateState) -> ListUpdateState) {
        mutableState.update(transform)
    }

    companion object {
        private const val KEY_PRESET = "preset"
        const val PREFS = "adblock_lists"

        fun create(context: Context, storeProvider: () -> EngineStore): FilterListController {
            val app = context.applicationContext
            val client = OkHttpClient.Builder()
                .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .build()
            val dir = File(app.filesDir, "adblock/lists")
            return FilterListController(
                context = app,
                catalog = FilterListCatalog.load(app.assets),
                updater = FilterListUpdater(client, dir),
                storeProvider = storeProvider,
                prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE),
            )
        }

        private const val TIMEOUT_SECONDS = 30L
    }
}

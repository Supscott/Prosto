package com.jestk.browser.engine.adblock

import android.content.Context
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.io.File
import java.security.MessageDigest
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.concurrent.atomic.AtomicReference

/**
 * Owns the published [EngineSet]. Engines are compiled from the bundled lists once and cached in
 * `filesDir/adblock/<engine>-<preset>-<nativeVersion>-<listsHash>.bin` (plus a `.rules` file with
 * the rule count). `listsHash` is [FilterAssets.listsHash]. Resources are not part of the blob, so
 * they are passed again on every load.
 */
internal class EngineStore(
    private val backend: NativeBackend,
    private val preset: String = DEFAULT_PRESET,
    private val log: (String) -> Unit = {},
) {
    private val current = AtomicReference<EngineSet?>(null)

    /** Chooses the lists to build from (downloaded lists, preset); null means the bundled lists only. */
    @Volatile
    var selectionProvider: (() -> ListSelection)? = null

    /** Called after every [swap] with the published set (null when cleared). */
    @Volatile
    var onSwap: ((EngineSet?) -> Unit)? = null

    /** The engines to use, or null while they are not ready (callers fall back to the host list). */
    fun current(): EngineSet? = current.get()

    /** Loads or compiles the engines on [dispatcher] and publishes them. Never runs on the caller. */
    fun initialize(context: Context, scope: CoroutineScope, dispatcher: CoroutineDispatcher): Job =
        scope.launch(dispatcher) { swap(load(context)) }

    /** Publishes [next] and closes the previous set (handles close under their write lock). */
    fun swap(next: EngineSet?) {
        val old = current.getAndSet(next)
        if (old != null && old !== next) old.close()
        onSwap?.invoke(next)
    }

    /** Blocking: reads the cache or compiles. Returns null unless both engines are available. */
    fun load(context: Context, selection: ListSelection = selectionProvider?.invoke() ?: ListSelection.bundled(preset)): EngineSet? {
        val started = System.nanoTime()
        val assets = FilterAssets(context.assets, bundledPreset(selection))
        val resources = readResources(context)
        val dir = File(context.filesDir, "adblock").apply { mkdirs() }
        // The APK update time joins the key: lists ship inside the APK, so a new build recompiles
        // once even when a list edit keeps the asset size (and so the lists hash) unchanged.
        val version = cacheVersion(context)
        // Lists read for one engine are reused by an engine derived from them (popup from ads).
        val readLists = HashMap<String, List<String>>()
        val ads = loadEngine(dir, assets, resources, version, ENGINE_ADS, selection, readLists = readLists) ?: return null
        val privacy = loadEngine(dir, assets, resources, version, ENGINE_PRIVACY, selection)
        if (privacy == null) {
            ads.matcher.close()
            return null
        }
        val popup = loadEngine(dir, assets, null, version, ENGINE_POPUP, selection, ENGINE_ADS, PopupRules::extract, readLists)
        log("engines ready in ${(System.nanoTime() - started) / NANOS_PER_MS} ms")
        return EngineSet(ads.matcher, privacy.matcher, ads.rules + privacy.rules, popup?.matcher)
    }

    private class Loaded(val matcher: ContentMatcher, val rules: Int)

    private fun loadEngine(
        dir: File,
        assets: FilterAssets,
        resources: String?,
        version: String,
        engine: String,
        selection: ListSelection,
        source: String = engine,
        transform: (List<String>) -> List<String> = { it },
        readLists: MutableMap<String, List<String>> = HashMap(),
    ): Loaded? {
        val extras = selection.extras[source].orEmpty()
        val base = cacheBase(engine, source, selection.preset, version, assets, extras)
        val bin = File(dir, "$base.bin")
        val rulesFile = File(dir, "$base.rules")
        if (bin.exists()) {
            val restored = runCatching { backend.factory.restore(bin.readBytes(), resources) }.getOrNull()
            if (restored != null) {
                deleteStale(dir, engine, selection.preset, version, base)
                log("$engine loaded from cache")
                return Loaded(restored, rulesFile.readText().trim().toIntOrNull() ?: 0)
            }
            bin.delete()
            rulesFile.delete()
        }
        val sourceLists = readLists.getOrPut(source) {
            val merged = assets.readById(source, skip = extras.map { it.id }.toSet()) + extras.associate { it.id to it.text }
            merged.toSortedMap().values.toList()
        }
        val lists = transform(sourceLists)
        if (lists.isEmpty()) return null
        val compiled = runCatching { backend.factory.compile(lists, resources) }.getOrNull() ?: return null
        val rules = FilterAssets.countRules(lists)
        compiled.serialized?.let { writeAtomically(bin, rulesFile, it, rules) }
        deleteStale(dir, engine, selection.preset, version, base)
        log("$engine compiled")
        return Loaded(compiled.matcher, rules)
    }

    private fun writeAtomically(bin: File, rulesFile: File, bytes: ByteArray, rules: Int) {
        runCatching {
            rulesFile.writeText(rules.toString())
            val tmp = File(bin.parentFile, "${bin.name}.tmp")
            tmp.writeBytes(bytes)
            Files.move(tmp.toPath(), bin.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        }
    }

    /**
     * Removes cache files of [engine] that cannot be used any more: other contents of the same
     * preset and anything from another native/APK version. Another preset's current files stay, so
     * a Strict/Standard switch back finds its blob.
     */
    private fun deleteStale(dir: File, engine: String, presetId: String, version: String, base: String) {
        dir.listFiles()?.forEach { file ->
            val name = file.name
            if (!name.startsWith("$engine-") || name.startsWith("$base.")) return@forEach
            if (name.startsWith("$engine-$presetId-") || "-$version-" !in name) file.delete()
        }
    }

    /** Deletes the cached blobs of every engine for [presetId] (used when the Strict lists are dropped). */
    fun deleteCache(context: Context, presetId: String) {
        File(context.filesDir, "adblock").listFiles()?.forEach { file ->
            if (ENGINES.any { file.name.startsWith("$it-$presetId-") }) file.delete()
        }
    }

    /** Deletes the cached blobs [load] would use for [selection] (a set that failed its smoke check). */
    fun purge(context: Context, selection: ListSelection) {
        val assets = FilterAssets(context.assets, bundledPreset(selection))
        val dir = File(context.filesDir, "adblock")
        val version = cacheVersion(context)
        for (engine in ENGINES) {
            val source = if (engine == ENGINE_POPUP) ENGINE_ADS else engine
            val base = cacheBase(engine, source, selection.preset, version, assets, selection.extras[source].orEmpty())
            File(dir, "$base.bin").delete()
            File(dir, "$base.rules").delete()
        }
    }

    private fun bundledPreset(selection: ListSelection) =
        if (selection.preset == STRICT_PRESET) DEFAULT_PRESET else selection.preset

    private fun cacheVersion(context: Context) =
        "${backend.version()}-${apkUpdateTime(context)}".replace(UNSAFE_NAME, "_")

    private fun cacheBase(engine: String, source: String, presetId: String, version: String, assets: FilterAssets, extras: List<ExtraList>): String {
        val bundledHash = assets.listsHash(source)
        val hash = if (extras.isEmpty()) {
            bundledHash
        } else {
            val digest = MessageDigest.getInstance("SHA-256")
            digest.update(bundledHash.toByteArray())
            extras.sortedBy { it.id }.forEach { digest.update("\n${it.id}:${it.digest}".toByteArray()) }
            digest.digest().joinToString("") { "%02x".format(it) }.take(HASH_LENGTH)
        }
        return "$engine-$presetId-$version-$hash"
    }

    private fun apkUpdateTime(context: Context): Long =
        runCatching {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(context.packageName, 0).lastUpdateTime
        }.getOrDefault(0L)

    private fun readResources(context: Context): String? =
        runCatching {
            context.assets.open(RESOURCES_ASSET).use { String(it.readBytes(), Charsets.UTF_8) }
        }.getOrNull()

    companion object {
        const val DEFAULT_PRESET = "standard"
        const val STRICT_PRESET = "strict"
        private const val HASH_LENGTH = 16
        const val ENGINE_ADS = "ads"
        const val ENGINE_PRIVACY = "privacy"
        const val ENGINE_POPUP = "popup"
        private val ENGINES = listOf(ENGINE_ADS, ENGINE_PRIVACY, ENGINE_POPUP)
        private const val RESOURCES_ASSET = "adblock/resources.json"
        private val UNSAFE_NAME = Regex("[^A-Za-z0-9._-]")
        private const val NANOS_PER_MS = 1_000_000L
    }
}

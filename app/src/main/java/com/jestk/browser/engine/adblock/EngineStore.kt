package com.jestk.browser.engine.adblock

import android.content.Context
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.io.File
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

    /** The engines to use, or null while they are not ready (callers fall back to the host list). */
    fun current(): EngineSet? = current.get()

    /** Loads or compiles the engines on [dispatcher] and publishes them. Never runs on the caller. */
    fun initialize(context: Context, scope: CoroutineScope, dispatcher: CoroutineDispatcher): Job =
        scope.launch(dispatcher) { swap(load(context)) }

    /** Publishes [next] and closes the previous set (handles close under their write lock). */
    fun swap(next: EngineSet?) {
        val old = current.getAndSet(next)
        if (old != null && old !== next) old.close()
    }

    /** Blocking: reads the cache or compiles. Returns null unless both engines are available. */
    fun load(context: Context): EngineSet? {
        val started = System.nanoTime()
        val assets = FilterAssets(context.assets, preset)
        val resources = readResources(context)
        val dir = File(context.filesDir, "adblock").apply { mkdirs() }
        // The APK update time joins the key: lists ship inside the APK, so a new build recompiles
        // once even when a list edit keeps the asset size (and so the lists hash) unchanged.
        val version = "${backend.version()}-${apkUpdateTime(context)}".replace(UNSAFE_NAME, "_")
        val ads = loadEngine(dir, assets, resources, version, ENGINE_ADS) ?: return null
        val privacy = loadEngine(dir, assets, resources, version, ENGINE_PRIVACY)
        if (privacy == null) {
            ads.matcher.close()
            return null
        }
        log("engines ready in ${(System.nanoTime() - started) / NANOS_PER_MS} ms")
        return EngineSet(ads.matcher, privacy.matcher, ads.rules + privacy.rules)
    }

    private class Loaded(val matcher: ContentMatcher, val rules: Int)

    private fun loadEngine(
        dir: File,
        assets: FilterAssets,
        resources: String?,
        version: String,
        engine: String,
    ): Loaded? {
        val base = "$engine-$preset-$version-${assets.listsHash(engine)}"
        val bin = File(dir, "$base.bin")
        val rulesFile = File(dir, "$base.rules")
        if (bin.exists()) {
            val restored = runCatching { backend.factory.restore(bin.readBytes(), resources) }.getOrNull()
            if (restored != null) {
                deleteStale(dir, engine, base)
                log("$engine loaded from cache")
                return Loaded(restored, rulesFile.readText().trim().toIntOrNull() ?: 0)
            }
            bin.delete()
            rulesFile.delete()
        }
        val lists = assets.read(engine)
        if (lists.isEmpty()) return null
        val compiled = runCatching { backend.factory.compile(lists, resources) }.getOrNull() ?: return null
        val rules = FilterAssets.countRules(lists)
        compiled.serialized?.let { writeAtomically(bin, rulesFile, it, rules) }
        deleteStale(dir, engine, base)
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

    private fun deleteStale(dir: File, engine: String, base: String) {
        dir.listFiles()?.forEach { file ->
            if (file.name.startsWith("$engine-") && !file.name.startsWith("$base.")) file.delete()
        }
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
        const val ENGINE_ADS = "ads"
        const val ENGINE_PRIVACY = "privacy"
        private const val RESOURCES_ASSET = "adblock/resources.json"
        private val UNSAFE_NAME = Regex("[^A-Za-z0-9._-]")
        private const val NANOS_PER_MS = 1_000_000L
    }
}

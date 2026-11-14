package com.jestk.browser.engine.adblock

import android.content.res.AssetManager
import java.io.FileNotFoundException
import java.security.MessageDigest

/** Reads the bundled filter lists of one preset: `assets/filters/<preset>/<engine>/<id>.txt`. */
internal class FilterAssets(private val assets: AssetManager, private val preset: String) {
    private fun dir(engine: String) = "filters/$preset/$engine"

    fun names(engine: String): List<String> =
        (assets.list(dir(engine)) ?: emptyArray()).filter { it.endsWith(".txt") }.sorted()

    /** The decoded text of every list of [engine], in name order. */
    fun read(engine: String): List<String> =
        names(engine).map { name -> assets.open("${dir(engine)}/$name").use { String(it.readBytes(), Charsets.UTF_8) } }

    /** Bundled lists of [engine] by list id (file name without `.txt`), skipping the ids in [skip]. */
    fun readById(engine: String, skip: Set<String> = emptySet()): Map<String, String> =
        names(engine).filter { it.removeSuffix(".txt") !in skip }.associate { name ->
            name.removeSuffix(".txt") to assets.open("${dir(engine)}/$name").use { String(it.readBytes(), Charsets.UTF_8) }
        }

    /**
     * sha256 over `<name>:<size>` lines of the sorted list assets of [engine]. Cheap: sizes come
     * from the asset file descriptor, so nothing is decompressed when the assets are stored raw.
     */
    fun listsHash(engine: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        for (name in names(engine)) {
            digest.update("$name:${size("${dir(engine)}/$name")}\n".toByteArray(Charsets.UTF_8))
        }
        return digest.digest().joinToString("") { "%02x".format(it) }.take(HASH_LENGTH)
    }

    private fun size(path: String): Long =
        try {
            assets.openFd(path).use { it.length }
        } catch (_: FileNotFoundException) {
            assets.open(path).use { stream ->
                val buffer = ByteArray(BUFFER)
                var total = 0L
                while (true) {
                    val n = stream.read(buffer)
                    if (n < 0) break
                    total += n
                }
                total
            }
        }

    companion object {
        private const val HASH_LENGTH = 16
        private const val BUFFER = 64 * 1024

        /** Counts rule lines: not blank, not a `!` comment, not the `[Adblock ...]` header. */
        fun countRules(lists: List<String>): Int {
            var count = 0
            for (text in lists) {
                text.lineSequence().forEach { line ->
                    if (line.isNotBlank() && !line.startsWith("!") && !line.startsWith("[")) count++
                }
            }
            return count
        }
    }
}

package com.jestk.browser.engine.adblock

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.Properties

/** HTTP validators and fetch time of a stored list. */
internal data class ListMeta(val etag: String?, val lastModified: String?, val fetchedAt: Long)

/** A stored list: its text and a content digest (part of the engine cache key). */
internal class StoredList(val id: String, val text: String, val digest: String, val meta: ListMeta)

internal class UpdateResult(val changed: List<String>, val unchanged: List<String>, val failures: Map<String, String>)

/**
 * Downloads filter lists into `<dir>/<id>.txt` (+ `<id>.meta` with the validators) using conditional
 * GETs. HTTPS only, no cross-scheme redirects, size capped, body must look like a filter list.
 * A changed list keeps its previous copy as `.prev` until [commit]; [rollback] restores it, so a
 * list that fails the engine smoke check never stays installed.
 * [requireHttps] is only turned off by tests that talk to a plain-HTTP mock server.
 */
internal class FilterListUpdater(
    client: OkHttpClient,
    private val dir: File,
    private val clock: () -> Long = System::currentTimeMillis,
    private val maxBytes: Int = MAX_BYTES,
    private val requireHttps: Boolean = true,
) {
    private val client = client.newBuilder().followSslRedirects(false).build()

    fun update(entries: List<ListEntry>, force: Boolean = false): UpdateResult {
        dir.mkdirs()
        val changed = ArrayList<String>()
        val unchanged = ArrayList<String>()
        val failures = LinkedHashMap<String, String>()
        for (entry in entries) {
            try {
                if (fetchOne(entry, force)) changed += entry.id else unchanged += entry.id
            } catch (e: IOException) {
                failures[entry.id] = e.message ?: e.javaClass.simpleName
            } catch (e: IllegalArgumentException) {
                failures[entry.id] = e.message ?: "invalid list"
            } catch (e: java.net.URISyntaxException) {
                failures[entry.id] = e.message ?: "invalid include"
            }
        }
        return UpdateResult(changed, unchanged, failures)
    }

    fun has(id: String): Boolean = file(id).exists()

    fun read(id: String): StoredList? {
        val file = file(id)
        if (!file.exists()) return null
        val bytes = file.readBytes()
        return StoredList(id, String(bytes, Charsets.UTF_8), sha256(bytes), meta(id) ?: ListMeta(null, null, 0L))
    }

    /** The newest fetch time over the stored lists, or null when nothing was downloaded. */
    fun lastFetchedAt(): Long? =
        dir.listFiles().orEmpty().filter { it.name.endsWith(META_SUFFIX) }
            .mapNotNull { meta(it.name.removeSuffix(META_SUFFIX))?.fetchedAt }.maxOrNull()

    fun remove(ids: Collection<String>) {
        for (id in ids) {
            file(id).delete()
            metaFile(id).delete()
            prev(file(id)).delete()
            prev(metaFile(id)).delete()
        }
    }

    /** Restores the previous copy of every id (or deletes the file when there was none). */
    fun rollback(ids: Collection<String>) {
        for (id in ids) {
            for (target in listOf(file(id), metaFile(id))) {
                val backup = prev(target)
                if (backup.exists()) move(backup, target) else target.delete()
            }
        }
    }

    /** Drops the previous copies: the new lists are live. */
    fun commit() {
        dir.listFiles().orEmpty().filter { it.name.endsWith(PREV_SUFFIX) }.forEach { it.delete() }
    }

    private fun fetchOne(entry: ListEntry, force: Boolean): Boolean {
        val url = entry.url
        require(!requireHttps || url.startsWith("https://")) { "${entry.id}: url is not HTTPS" }
        val existing = if (file(entry.id).exists() && !force) meta(entry.id) else null
        val request = Request.Builder().url(url).header("User-Agent", USER_AGENT).apply {
            existing?.etag?.let { header("If-None-Match", it) }
            existing?.lastModified?.let { header("If-Modified-Since", it) }
        }.build()
        client.newCall(request).execute().use { response ->
            checkScheme(response)
            if (response.code == NOT_MODIFIED && existing != null) {
                writeMeta(entry.id, existing.copy(fetchedAt = clock()))
                return false
            }
            if (!response.isSuccessful) throw IOException("${entry.id}: HTTP ${response.code}")
            val raw = readCapped(response)
            require(looksLikeFilterList(raw)) { "${entry.id}: response is not a filter list" }
            val text = FilterListPreprocessor(requireHttps) { include -> fetchInclude(include) }.preprocess(raw, url)
            require(text.toByteArray(Charsets.UTF_8).size <= maxBytes) { "${entry.id}: list too large" }
            val meta = ListMeta(response.header("ETag"), response.header("Last-Modified"), clock())
            if (file(entry.id).exists() && file(entry.id).readText(Charsets.UTF_8) == text) {
                writeMeta(entry.id, meta)
                return false
            }
            install(entry.id, text, meta)
            return true
        }
    }

    private fun fetchInclude(url: String): String {
        val request = Request.Builder().url(url).header("User-Agent", USER_AGENT).build()
        client.newCall(request).execute().use { response ->
            checkScheme(response)
            if (!response.isSuccessful) throw IOException("include $url: HTTP ${response.code}")
            return readCapped(response)
        }
    }

    private fun checkScheme(response: Response) {
        require(!requireHttps || response.request.url.isHttps) { "redirected to a non-HTTPS URL" }
    }

    private fun readCapped(response: Response): String {
        val body = response.body ?: throw IOException("empty body")
        val declared = body.contentLength()
        require(declared <= maxBytes) { "list too large" }
        val out = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(BUFFER)
        body.byteStream().use { stream ->
            while (true) {
                val n = stream.read(buffer)
                if (n < 0) break
                out.write(buffer, 0, n)
                require(out.size() <= maxBytes) { "list too large" }
            }
        }
        return String(out.toByteArray(), Charsets.UTF_8)
    }

    /** A filter list starts with a header or `!` comments; an HTML page does not. */
    private fun looksLikeFilterList(text: String): Boolean {
        val head = text.trimStart().take(HEAD_CHARS).lowercase()
        if (head.startsWith("<") || "<html" in head || "<!doctype" in head) return false
        val first = text.lineSequence().firstOrNull { it.isNotBlank() }?.trim() ?: return false
        return first.startsWith("!") || first.startsWith("[adblock", ignoreCase = true) || first.startsWith("[ublock", ignoreCase = true)
    }

    private fun install(id: String, text: String, meta: ListMeta) {
        val target = file(id)
        val metaTarget = metaFile(id)
        if (target.exists() && !prev(target).exists()) move(target, prev(target))
        if (metaTarget.exists() && !prev(metaTarget).exists()) move(metaTarget, prev(metaTarget))
        val tmp = File(dir, "$id.tmp")
        tmp.writeText(text, Charsets.UTF_8)
        move(tmp, target)
        writeMeta(id, meta)
    }

    private fun writeMeta(id: String, meta: ListMeta) {
        val props = Properties()
        meta.etag?.let { props.setProperty("etag", it) }
        meta.lastModified?.let { props.setProperty("lastModified", it) }
        props.setProperty("fetchedAt", meta.fetchedAt.toString())
        val tmp = File(dir, "$id.meta.tmp")
        tmp.outputStream().use { props.store(it, null) }
        move(tmp, metaFile(id))
    }

    private fun meta(id: String): ListMeta? =
        runCatching {
            val props = Properties()
            metaFile(id).inputStream().use { props.load(it) }
            ListMeta(props.getProperty("etag"), props.getProperty("lastModified"), props.getProperty("fetchedAt").toLong())
        }.getOrNull()

    private fun file(id: String) = File(dir, "$id.txt")

    private fun metaFile(id: String) = File(dir, "$id$META_SUFFIX")

    private fun prev(file: File) = File(file.parentFile, file.name + PREV_SUFFIX)

    private fun move(from: File, to: File) {
        Files.move(from.toPath(), to.toPath(), StandardCopyOption.REPLACE_EXISTING)
    }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }.take(DIGEST_LENGTH)

    companion object {
        const val MAX_BYTES = 10 * 1024 * 1024
        private const val USER_AGENT = "Prosto/filter-list-updater"
        private const val NOT_MODIFIED = 304
        private const val META_SUFFIX = ".meta"
        private const val PREV_SUFFIX = ".prev"
        private const val HEAD_CHARS = 200
        private const val BUFFER = 64 * 1024
        private const val DIGEST_LENGTH = 16
    }
}

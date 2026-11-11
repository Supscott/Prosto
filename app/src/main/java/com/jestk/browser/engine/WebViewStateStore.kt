package com.jestk.browser.engine

import android.content.Context
import android.os.Bundle
import android.util.Log
import android.webkit.WebView
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

/**
 * Keeps each tab's WebView state (back/forward history, scroll position, form data) across
 * WebView eviction and process death, like Chrome's tab restore.
 *
 * `WebView.restoreState` reloads the current entry as a history navigation, which reuses the
 * HTTP cache instead of revalidating every resource, and Back keeps working in a tab that was
 * evicted from the pool. State is held in memory and mirrored to disk; all disk work runs on one
 * thread, so a delete always lands after a pending write of the same tab.
 */
object WebViewStateStore {
    private const val TAG = "WebViewStateStore"

    /** Larger states (huge form data, long histories) are not worth the disk and parse time. */
    private const val MAX_STATE_BYTES = 512 * 1024
    private const val FORMAT_VERSION = 1

    private val memory = ConcurrentHashMap<String, Bundle>()
    private val diskExecutor = Executors.newSingleThreadExecutor { runnable -> Thread(runnable, "WebViewStateStore") }

    @Volatile
    private var directory: File? = null

    fun init(context: Context) {
        if (directory == null) directory = File(context.applicationContext.filesDir, "webview_state")
    }

    /** Saves the state of [webView] for [tabId] when it shows a page. Main thread only. */
    fun save(tabId: String, webView: WebView) {
        val bundle = Bundle()
        val history = try {
            webView.saveState(bundle)
        } catch (t: Throwable) {
            Log.w(TAG, "saveState failed for tab $tabId", t)
            null
        }
        if (history == null || history.size == 0) return
        memory[tabId] = bundle
        // Chromium stores its state as byte arrays; anything else stays in memory only.
        val entries = bundle.keySet().associateWith { key -> bundle.getByteArray(key) }
        if (entries.values.any { it == null } || entries.values.sumOf { it!!.size } > MAX_STATE_BYTES) return
        val dir = directory ?: return
        diskExecutor.execute { write(File(dir, fileName(tabId)), entries.mapValues { it.value!! }) }
    }

    /**
     * Restores the saved state of [tabId] into a fresh [webView]; returns false when there is
     * none or it cannot be applied, and the caller then loads the tab URL. Main thread only.
     */
    fun restore(tabId: String, webView: WebView): Boolean {
        val bundle = memory[tabId] ?: readBundle(tabId) ?: return false
        return try {
            val history = webView.restoreState(bundle)
            history != null && history.size > 0
        } catch (t: Throwable) {
            Log.w(TAG, "restoreState failed for tab $tabId", t)
            false
        }
    }

    private const val MAX_CLOSED_STATES = 3
    private val closedTabIds = ArrayDeque<String>()

    /**
     * The tab was closed. Its state stays for "undo close" ([move]) while it is among the last
     * [MAX_CLOSED_STATES] closed tabs; older ones are deleted.
     */
    @Synchronized
    fun onTabClosed(tabId: String) {
        closedTabIds.remove(tabId)
        closedTabIds.addLast(tabId)
        while (closedTabIds.size > MAX_CLOSED_STATES) delete(closedTabIds.removeFirst())
    }

    /** A closed tab was reopened as [toTabId]: it takes over the saved history. */
    @Synchronized
    fun move(fromTabId: String, toTabId: String) {
        closedTabIds.remove(fromTabId)
        val bundle = memory.remove(fromTabId) ?: readBundle(fromTabId)?.also { memory.remove(fromTabId) } ?: return
        memory[toTabId] = bundle
        val dir = directory ?: return
        diskExecutor.execute { File(dir, fileName(fromTabId)).renameTo(File(dir, fileName(toTabId))) }
    }

    /** The tab is gone for good: its history must not come back. */
    fun delete(tabId: String) {
        memory.remove(tabId)
        val dir = directory ?: return
        diskExecutor.execute { File(dir, fileName(tabId)).delete() }
    }

    fun deleteAll() {
        memory.clear()
        val dir = directory ?: return
        diskExecutor.execute { dir.listFiles()?.forEach { it.delete() } }
    }

    /** Drops saved states of tabs that no longer exist (closed while the process was gone). */
    fun retainOnly(tabIds: Set<String>) {
        memory.keys.retainAll(tabIds)
        val dir = directory ?: return
        val keep = tabIds.mapTo(HashSet()) { fileName(it) }
        diskExecutor.execute { dir.listFiles()?.filter { it.name !in keep }?.forEach { it.delete() } }
    }

    /** Reads synchronously on the caller: restoring happens right before the tab's first load. */
    private fun readBundle(tabId: String): Bundle? {
        val file = directory?.let { File(it, fileName(tabId)) } ?: return null
        if (!file.isFile || file.length() > MAX_STATE_BYTES + 4096) return null
        val entries = readEntries(file) ?: run {
            file.delete()
            return null
        }
        return Bundle().apply { entries.forEach { (key, bytes) -> putByteArray(key, bytes) } }
            .also { memory[tabId] = it }
    }

    /** Null for a missing, foreign or truncated file. */
    internal fun readEntries(file: File): Map<String, ByteArray>? = try {
        DataInputStream(file.inputStream().buffered()).use { input ->
            if (input.readInt() != FORMAT_VERSION) return null
            val count = input.readInt()
            if (count !in 0..64) return null
            buildMap {
                repeat(count) {
                    val key = input.readUTF()
                    val size = input.readInt()
                    if (size !in 0..MAX_STATE_BYTES) return null
                    val bytes = ByteArray(size)
                    input.readFully(bytes)
                    put(key, bytes)
                }
            }
        }
    } catch (t: Throwable) {
        Log.w(TAG, "Failed to read saved state ${file.name}", t)
        null
    }

    internal fun write(file: File, entries: Map<String, ByteArray>) {
        try {
            file.parentFile?.mkdirs()
            val temp = File(file.parentFile, file.name + ".tmp")
            DataOutputStream(temp.outputStream().buffered()).use { output ->
                output.writeInt(FORMAT_VERSION)
                output.writeInt(entries.size)
                entries.forEach { (key, bytes) ->
                    output.writeUTF(key)
                    output.writeInt(bytes.size)
                    output.write(bytes)
                }
            }
            if (!temp.renameTo(file)) temp.delete()
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to write saved state ${file.name}", t)
        }
    }

    private fun fileName(tabId: String): String = tabId.replace(Regex("[^A-Za-z0-9_-]"), "_") + ".state"
}

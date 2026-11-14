package com.jestk.browser.engine.adblock

import android.content.res.AssetManager
import org.json.JSONObject

/** Protection level. Standard lists ship in the APK; Strict adds lists that are downloaded on demand. */
internal enum class ProtectionPreset(val id: String) {
    STANDARD("standard"),
    STRICT("strict"),
    ;

    companion object {
        fun fromId(id: String?): ProtectionPreset = entries.firstOrNull { it.id == id } ?: STANDARD
    }
}

/** One filter list: where it comes from and which engines it feeds. */
internal data class ListEntry(
    val id: String,
    val engines: List<String>,
    val preset: ProtectionPreset,
    val url: String,
)

/**
 * The list metadata shipped as `assets/adblock/lists.json`, generated from
 * `tools/filterlists/lists.json` by `tools/update_filterlists.py`.
 */
internal class FilterListCatalog(val entries: List<ListEntry>) {
    /** Lists active at [preset]: Strict includes every Standard list. */
    fun forPreset(preset: ProtectionPreset): List<ListEntry> =
        entries.filter { it.preset == ProtectionPreset.STANDARD || it.preset == preset }

    /** Lists that are not bundled in the APK. */
    fun downloadOnly(): List<ListEntry> = entries.filter { it.preset == ProtectionPreset.STRICT }

    companion object {
        const val ASSET = "adblock/lists.json"
        private val SAFE_ID = Regex("[a-z0-9][a-z0-9-]*")

        fun load(assets: AssetManager): FilterListCatalog =
            parse(assets.open(ASSET).use { String(it.readBytes(), Charsets.UTF_8) })

        fun parse(json: String): FilterListCatalog {
            val array = JSONObject(json).getJSONArray("lists")
            val entries = (0 until array.length()).map { index ->
                val item = array.getJSONObject(index)
                val engines = item.getJSONArray("engine")
                val id = item.getString("id")
                require(SAFE_ID.matches(id)) { "unsafe list id: $id" }
                ListEntry(
                    id = id,
                    engines = (0 until engines.length()).map { engines.getString(it) },
                    preset = ProtectionPreset.fromId(item.getString("preset")),
                    url = item.getString("url"),
                )
            }
            return FilterListCatalog(entries)
        }
    }
}

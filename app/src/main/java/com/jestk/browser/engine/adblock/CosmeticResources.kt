package com.jestk.browser.engine.adblock

import org.json.JSONArray
import org.json.JSONObject

data class CosmeticResources(
    val hideSelectors: List<String>,
    val exceptions: Set<String>,
    val procedural: List<String>,
    val generichide: Boolean,
    val scriptlets: List<List<String>>,
) {
    companion object {
        val EMPTY = CosmeticResources(emptyList(), emptySet(), emptyList(), false, emptyList())

        /** Parses the native JSON; malformed input yields [EMPTY]. */
        fun parse(json: String): CosmeticResources = try {
            val o = JSONObject(json)
            CosmeticResources(
                hideSelectors = o.optJSONArray("hide").strings(),
                exceptions = o.optJSONArray("exceptions").strings().toSet(),
                procedural = o.optJSONArray("procedural").strings(),
                generichide = o.optBoolean("generichide", false),
                scriptlets = o.optJSONArray("scriptlets")?.let { arr ->
                    (0 until arr.length()).mapNotNull { i ->
                        arr.optJSONArray(i).strings().takeIf { it.isNotEmpty() }
                    }
                } ?: emptyList(),
            )
        } catch (_: Exception) {
            EMPTY
        }

        private fun JSONArray?.strings(): List<String> =
            if (this == null) emptyList() else (0 until length()).map { optString(it) }
    }
}

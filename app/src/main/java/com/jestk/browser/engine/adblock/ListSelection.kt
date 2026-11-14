package com.jestk.browser.engine.adblock

/** A downloaded list that joins (or overrides, by [id]) the bundled lists of an engine. */
internal class ExtraList(val id: String, val text: String, val digest: String)

/**
 * What an engine set is built from: the preset label (part of the cache key) and, per engine, the
 * downloaded lists. Bundled Standard lists are always the base; an extra with the same id replaces
 * the bundled copy, any other id is added (the Strict lists).
 */
internal class ListSelection(val preset: String, val extras: Map<String, List<ExtraList>> = emptyMap()) {
    /** Identifies the list contents; two selections with the same fingerprint build the same engines. */
    val fingerprint: String =
        preset + extras.entries.sortedBy { it.key }.joinToString("|") { (engine, lists) ->
            "$engine=" + lists.sortedBy { it.id }.joinToString(",") { "${it.id}:${it.digest}" }
        }

    companion object {
        fun bundled(preset: String = EngineStore.DEFAULT_PRESET) = ListSelection(preset)
    }
}

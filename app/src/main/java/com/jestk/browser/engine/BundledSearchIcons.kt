package com.jestk.browser.engine

import androidx.annotation.DrawableRes
import com.jestk.browser.R

/**
 * Icons of the default search engines, shipped as `drawable-nodpi` PNGs so showing them needs no
 * network request. A stored reference looks like `bundled:engine/google`.
 */
object BundledSearchIcons {
    private const val PREFIX = "bundled:"

    private val resources: Map<String, Int> =
        mapOf(
            "bundled:engine/google" to R.drawable.ic_engine_google,
            "bundled:engine/duckduckgo" to R.drawable.ic_engine_duckduckgo,
            "bundled:engine/brave" to R.drawable.ic_engine_brave,
            "bundled:engine/kagi" to R.drawable.ic_engine_kagi,
            "bundled:engine/ecosia" to R.drawable.ic_engine_ecosia,
            "bundled:engine/startpage" to R.drawable.ic_engine_startpage,
        )

    fun isBundledRef(value: String?): Boolean = value != null && value.startsWith(PREFIX)

    /** The drawable behind a bundled reference, or null for an unknown reference. */
    @DrawableRes
    fun resIdOf(value: String?): Int? = value?.let { resources[it] }
}

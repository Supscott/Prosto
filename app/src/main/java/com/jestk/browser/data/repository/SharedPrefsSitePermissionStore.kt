package com.jestk.browser.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.jestk.browser.domain.model.SiteResource
import com.jestk.browser.domain.repository.SitePermissionStore

/** [SitePermissionStore] kept in SharedPreferences: one string set of resource names per origin. */
class SharedPrefsSitePermissionStore(private val prefs: SharedPreferences) : SitePermissionStore {
    constructor(context: Context) : this(
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE),
    )

    override fun isAllowed(origin: String, resource: SiteResource): Boolean =
        read(origin).contains(resource)

    override fun allow(origin: String, resources: Set<SiteResource>) {
        if (origin.isEmpty() || resources.isEmpty()) return
        val merged = read(origin) + resources
        prefs.edit().putStringSet(origin, merged.map { it.name }.toSet()).apply()
    }

    override fun clear(origin: String) {
        prefs.edit().remove(origin).apply()
    }

    override fun clearAll() {
        prefs.edit().clear().apply()
    }

    private fun read(origin: String): Set<SiteResource> =
        prefs.getStringSet(origin, null).orEmpty()
            .mapNotNull { name -> SiteResource.entries.firstOrNull { it.name == name } }
            .toSet()

    private companion object {
        const val PREFS_NAME = "site_permissions"
    }
}

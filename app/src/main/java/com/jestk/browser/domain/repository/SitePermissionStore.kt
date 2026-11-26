package com.jestk.browser.domain.repository

import com.jestk.browser.domain.model.SiteResource

/**
 * The camera / microphone grants the user chose "Allow always" for. Keys are origins
 * (scheme + host + port, see `siteOriginKey`); each origin holds a set of resources.
 */
interface SitePermissionStore {
    fun isAllowed(origin: String, resource: SiteResource): Boolean

    /** Adds [resources] to what [origin] is allowed to use. */
    fun allow(origin: String, resources: Set<SiteResource>)

    /** Forgets every grant of [origin]. */
    fun clear(origin: String)

    fun clearAll()
}

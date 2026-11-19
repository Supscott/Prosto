package com.jestk.browser.engine

import com.jestk.browser.domain.model.Tab
import com.jestk.browser.domain.model.Workspace

/**
 * The WebView profiles of incognito tabs, one per incognito session.
 *
 * Chromium cannot delete a profile that this process has loaded (see [ProfileCleanup]). So when
 * the last incognito tab closes, [endSession] wipes the session's cookies and web storage at once
 * and moves to a fresh profile name, so a later incognito tab never sees the earlier session's
 * data. [ProfileCleanup.start] deletes the profile directories on the next app start, before any
 * WebView loads them. [WebViewPool] never keeps a spare WebView or warms a renderer for an
 * incognito profile.
 */
object IncognitoProfile {
    // Also matches the plain "incognito" profile of early debug builds, so their cleanup deletes it.
    private const val PREFIX = "incognito"

    @Volatile
    private var session = 0

    /** The profile of the current incognito session. */
    val name: String get() = "$PREFIX-$session"

    fun isIncognito(profileName: String?): Boolean = profileName?.startsWith(PREFIX) == true

    /** Ends the current session: its data is wiped now and the next incognito tab gets a new profile. */
    fun endSession() {
        val ended = name
        session++
        onMainThread { wipeProfile(ended) }
    }
}

/**
 * The WebView profile a tab must use: [IncognitoProfile.name] for an incognito [tab], otherwise
 * the profile of the workspace [workspaceId].
 */
fun profileNameFor(tab: Tab?, workspaces: List<Workspace>, workspaceId: String): String =
    if (tab?.isIncognito == true) {
        IncognitoProfile.name
    } else {
        workspaces.find { it.id == workspaceId }?.profileName ?: workspaceId
    }

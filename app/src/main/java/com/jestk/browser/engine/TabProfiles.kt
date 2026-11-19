package com.jestk.browser.engine

import androidx.webkit.ProfileStore
import androidx.webkit.WebViewFeature
import com.jestk.browser.domain.model.Tab
import com.jestk.browser.domain.model.Workspace

/**
 * Name of the WebView profile shared by all incognito tabs; it is deleted with the last one.
 * [WebViewPool] never keeps a spare WebView or warms a renderer for it: a live WebView would
 * block deleting the profile.
 */
const val INCOGNITO_PROFILE_NAME = "incognito"

/**
 * Deletes an incognito profile left behind by a process that died with incognito tabs open.
 * Call it on app start, before any WebView can use the profile.
 */
fun deleteLeftoverIncognitoProfile() {
    runCatching {
        if (WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)) {
            ProfileStore.getInstance().deleteProfile(INCOGNITO_PROFILE_NAME)
        }
    }
}

/**
 * The WebView profile a tab must use: [INCOGNITO_PROFILE_NAME] for an incognito [tab], otherwise
 * the profile of the workspace [workspaceId].
 */
fun profileNameFor(tab: Tab?, workspaces: List<Workspace>, workspaceId: String): String =
    if (tab?.isIncognito == true) {
        INCOGNITO_PROFILE_NAME
    } else {
        workspaces.find { it.id == workspaceId }?.profileName ?: workspaceId
    }

package com.jestk.browser.ui.viewmodel

import com.jestk.browser.domain.model.OverlayType
import com.jestk.browser.domain.model.ScreenType
import com.jestk.browser.domain.model.SitePermissionChoice
import com.jestk.browser.domain.model.SitePermissionPrompt
import com.jestk.browser.domain.model.SiteResource
import com.jestk.browser.domain.repository.SitePermissionStore
import com.jestk.browser.engine.SitePermissionRequest

/** Android runtime permissions (CAMERA, RECORD_AUDIO), implemented by the Activity. */
internal interface SiteOsPermissions {
    fun isHeld(resource: SiteResource): Boolean

    /** Asks for [missing]; [onResult] gets the subset the user granted. */
    fun request(missing: Set<SiteResource>, onResult: (granted: Set<SiteResource>) -> Unit)
}

/**
 * Decides what happens to a page's camera / microphone request and holds the one request that
 * waits for the user (the prompt) or for the system permission dialog.
 *
 * Rules: a request from a tab that is not the visible active tab is denied; so is a request that
 * arrives while another one is pending. A remembered grant ("Allow always", never in an
 * incognito tab) is granted without a prompt. A resource is granted only when the matching
 * Android permission is held or has just been granted; "Allow always" stores only those.
 * Every method runs on the main thread, where WebView delivers its callbacks.
 */
internal class SitePermissionController(
    private val store: SitePermissionStore?,
    private val getState: () -> BrowserUiState,
    private val updateUiState: ((BrowserUiState) -> BrowserUiState) -> Unit,
) {
    private class Pending(
        val request: SitePermissionRequest,
        val tabId: String,
        val resources: Set<SiteResource>,
        val canRemember: Boolean,
    ) {
        var awaitingOs = false
    }

    private var pending: Pending? = null

    /** Set by the Activity; a replaced bridge cannot report back, so an open system dialog is denied. */
    var osPermissions: SiteOsPermissions? = null
        set(value) {
            if (field !== value) {
                pending?.takeIf { it.awaitingOs }?.let { denyAndClear(it) }
            }
            field = value
        }

    fun handle(event: SitePermissionUiEvent) {
        when (event) {
            is OnSitePermissionRequest -> onRequest(event.tabId, event.request)
            is OnSitePermissionCanceled -> onCanceled(event.request)
            is ResolveSitePermissionPrompt -> resolve(event.choice)
        }
    }

    fun onRequest(tabId: String, request: SitePermissionRequest) {
        val state = getState()
        val tab = state.tabs.find { it.id == tabId }
        val hidden = state.activeTabId != tabId ||
            state.activeOverlay != OverlayType.NONE || state.activeScreen != ScreenType.NONE
        if (tab == null || hidden || request.resources.isEmpty() || request.origin.isEmpty() || pending != null) {
            request.deny()
            return
        }
        val canRemember = !tab.isIncognito && store != null
        val entry = Pending(request, tabId, request.resources, canRemember)
        pending = entry
        if (canRemember && request.resources.all { store?.isAllowed(request.origin, it) == true }) {
            grantWithOsPermissions(entry, remember = false)
            return
        }
        updateUiState {
            it.copy(
                sitePermissionPrompt = SitePermissionPrompt(tabId, request.host, request.resources, canRemember),
            )
        }
    }

    /** The page withdrew the request (navigation, tab closed): drop the prompt without answering. */
    fun onCanceled(request: SitePermissionRequest) {
        val entry = pending?.takeIf { it.request == request } ?: return
        clear(entry)
    }

    fun resolve(choice: SitePermissionChoice) {
        val entry = pending?.takeIf { !it.awaitingOs } ?: return
        updateUiState { it.copy(sitePermissionPrompt = null) }
        when (choice) {
            SitePermissionChoice.BLOCK -> denyAndClear(entry)
            SitePermissionChoice.ALLOW_ONCE -> grantWithOsPermissions(entry, remember = false)
            SitePermissionChoice.ALLOW_ALWAYS -> grantWithOsPermissions(entry, remember = entry.canRemember)
        }
    }

    /** Denies the open prompt when its tab is gone or is no longer the active one. */
    fun onStateChanged(state: BrowserUiState) {
        val entry = pending?.takeIf { !it.awaitingOs } ?: return
        if (state.tabs.none { it.id == entry.tabId } || state.activeTabId != entry.tabId) {
            updateUiState { it.copy(sitePermissionPrompt = null) }
            denyAndClear(entry)
        }
    }

    private fun grantWithOsPermissions(entry: Pending, remember: Boolean) {
        val os = osPermissions
        if (os == null) {
            denyAndClear(entry)
            return
        }
        val missing = entry.resources.filterNot(os::isHeld).toSet()
        if (missing.isEmpty()) {
            finish(entry, entry.resources, remember)
            return
        }
        entry.awaitingOs = true
        os.request(missing) { granted ->
            finish(entry, entry.resources.filter { it !in missing || it in granted }.toSet(), remember)
        }
    }

    private fun finish(entry: Pending, allowed: Set<SiteResource>, remember: Boolean) {
        if (pending !== entry) return
        pending = null
        if (allowed.isEmpty()) {
            entry.request.deny()
            return
        }
        entry.request.grant(allowed)
        if (remember) store?.allow(entry.request.origin, allowed)
    }

    private fun denyAndClear(entry: Pending) {
        if (pending !== entry) return
        pending = null
        entry.request.deny()
    }

    private fun clear(entry: Pending) {
        if (pending !== entry) return
        pending = null
        updateUiState { it.copy(sitePermissionPrompt = null) }
    }
}

package com.jestk.browser.ui.viewmodel.events

import android.util.Log
import com.jestk.browser.domain.model.BlockedAdNavigationPrompt
import com.jestk.browser.domain.model.InsecureHttpPrompt
import com.jestk.browser.domain.model.InsecureNavigationTarget
import com.jestk.browser.domain.model.OverlayType
import com.jestk.browser.domain.model.PopupBlockedPrompt
import com.jestk.browser.engine.WindowTransportHandler
import com.jestk.browser.ui.viewmodel.AllowBlockedDocument
import com.jestk.browser.ui.viewmodel.AllowPopupsForSite
import com.jestk.browser.ui.viewmodel.BrowserUiEvent
import com.jestk.browser.ui.viewmodel.BrowserUiState
import com.jestk.browser.ui.viewmodel.DismissPopupBlocked
import com.jestk.browser.ui.viewmodel.ExternalAppDismissReason
import com.jestk.browser.ui.viewmodel.GoBackFromBlockedDocument
import com.jestk.browser.ui.viewmodel.NavigationCommand
import com.jestk.browser.ui.viewmodel.SafetyUiEvent
import com.jestk.browser.ui.viewmodel.ShowPopupBlocked
import com.jestk.browser.ui.viewmodel.SiteAllowlistUiEvent
import com.jestk.browser.ui.viewmodel.SitePermissionController
import com.jestk.browser.ui.viewmodel.SitePermissionUiEvent
import com.jestk.browser.ui.viewmodel.TabCoordinator
import com.jestk.browser.ui.viewmodel.withDocumentAllowed
import com.jestk.browser.util.UrlSecurityUtils

private const val TAG = "BrowserViewModel"

internal class SafetyEventHandler(
    private val tabCoordinator: TabCoordinator,
    private val getCurrentState: () -> BrowserUiState,
    private val updateUiState: ((BrowserUiState) -> BrowserUiState) -> Unit,
    private val loadUrl: (String) -> Unit,
    private val emitNavigation: (NavigationCommand) -> Unit = {},
    private val allowPopupsForSite: (openerUrl: String) -> Unit = WindowTransportHandler.sessionPopupAllowlist::allow,
    private val siteAllowlistEvents: SiteAllowlistEventHandler? = null,
    private val sitePermissions: SitePermissionController? = null,
) {
    fun handle(event: SafetyUiEvent) {
        when (event) {
            is BrowserUiEvent.OnExternalAppOffered -> {
                tabCoordinator.externalAppPrompts.handleExternalAppOffered(event.tabId, event.prompt, event.userInitiated)
            }

            is BrowserUiEvent.DismissExternalAppPrompt -> {
                tabCoordinator.externalAppPrompts.dismissExternalAppPrompt(event.reason)
            }

            is BrowserUiEvent.OpenExternalApp -> {
                tabCoordinator.externalAppPrompts.dismissExternalAppPrompt(ExternalAppDismissReason.TRANSIENT)
            }

            is BrowserUiEvent.ShowInsecureHttpWarning -> {
                Log.d(TAG, "Showing insecure HTTP warning for ${event.url}")
                updateUiState {
                    it.copy(
                        insecureHttpPrompt = InsecureHttpPrompt(
                            url = event.url,
                            tabId = event.tabId,
                            target = event.target,
                        ),
                        activeOverlay = OverlayType.NONE,
                        isMenuExpanded = false,
                    )
                }
            }

            is BrowserUiEvent.ConfirmInsecureHttpNavigation -> {
                val prompt = getCurrentState().insecureHttpPrompt
                if (prompt != null) {
                    val url = prompt.url
                    val host = UrlSecurityUtils.extractHost(url)
                    updateUiState {
                        it.copy(
                            insecureHttpPrompt = null,
                            allowedInsecureHosts = if (host.isNotEmpty()) it.allowedInsecureHosts + host else it.allowedInsecureHosts,
                        )
                    }
                    when (prompt.target) {
                        InsecureNavigationTarget.NEW_TAB -> {
                            tabCoordinator.opening.openNewTab(url)
                        }
                        InsecureNavigationTarget.BACKGROUND_TAB -> {
                            tabCoordinator.opening.openTabInBackground(url)
                        }
                        InsecureNavigationTarget.ACTIVE_TAB -> {
                            if (prompt.tabId != null && prompt.tabId != getCurrentState().activeTabId) {
                                tabCoordinator.activation.selectTab(prompt.tabId)
                            }
                            loadUrl(url)
                        }
                    }
                }
            }

            is BrowserUiEvent.DismissInsecureHttpWarning -> {
                updateUiState { state ->
                    state.copy(
                        insecureHttpPrompt = null,
                        activeOverlay = if (state.isHome) OverlayType.HOME else state.activeOverlay,
                    )
                }
            }

            is BrowserUiEvent.ShowBlockedAdNavigation -> {
                Log.d(TAG, "Showing blocked ad navigation for ${event.url}")
                updateUiState {
                    it.copy(
                        blockedAdNavigation = BlockedAdNavigationPrompt(
                            url = event.url,
                            tabId = event.tabId,
                        ),
                        activeOverlay = OverlayType.NONE,
                        isMenuExpanded = false,
                    )
                }
            }

            // The block page is the tab's current history entry and Back steps over block pages, so
            // one step back lands on the page the user came from. With nothing behind it the tab closes.
            is GoBackFromBlockedDocument -> {
                val tab = getCurrentState().tabs.find { it.id == event.tabId }
                if (tab?.canGoBack == true) {
                    if (event.tabId != getCurrentState().activeTabId) tabCoordinator.activation.selectTab(event.tabId)
                    emitNavigation(NavigationCommand.GoBack)
                } else if (tab != null) {
                    tabCoordinator.closing.closeTab(event.tabId)
                }
            }

            // Loading the block page's own URL again replaces its history entry with the real page.
            is AllowBlockedDocument -> {
                updateUiState {
                    it.copy(documentAllowedHostsByTab = it.documentAllowedHostsByTab.withDocumentAllowed(event.tabId, event.url))
                }
                if (event.tabId != getCurrentState().activeTabId) tabCoordinator.activation.selectTab(event.tabId)
                tabCoordinator.pageEvents.navigateToUrl(event.url, null)
            }

            is BrowserUiEvent.AllowBlockedAdNavigation -> {
                val prompt = getCurrentState().blockedAdNavigation
                if (prompt != null) {
                    updateUiState {
                        it.copy(
                            blockedAdNavigation = null,
                            allowedAdNavigationUrls = it.allowedAdNavigationUrls + prompt.url,
                        )
                    }
                    if (prompt.tabId != getCurrentState().activeTabId) {
                        tabCoordinator.activation.selectTab(prompt.tabId)
                    }
                    tabCoordinator.pageEvents.navigateToUrl(prompt.url, null)
                }
            }

            is ShowPopupBlocked -> updateUiState { it.copy(popupBlocked = PopupBlockedPrompt(event.openerUrl)) }

            is AllowPopupsForSite -> {
                getCurrentState().popupBlocked?.let {
                    allowPopupsForSite(it.openerUrl)
                    siteAllowlistEvents?.allowFromPopup(it.openerUrl)
                }
                updateUiState { it.copy(popupBlocked = null) }
            }

            is SiteAllowlistUiEvent -> siteAllowlistEvents?.handle(event)

            is SitePermissionUiEvent -> sitePermissions?.handle(event)

            is DismissPopupBlocked -> updateUiState { it.copy(popupBlocked = null) }

            is BrowserUiEvent.DismissBlockedAdNavigation -> {
                val prompt = getCurrentState().blockedAdNavigation
                updateUiState { it.copy(blockedAdNavigation = null) }
                if (prompt != null) {
                    val tab = getCurrentState().tabs.find { it.id == prompt.tabId }
                    val isEmptyPopup = tab == null || tab.url.isBlank() || tab.url == "about:blank"
                    if (isEmptyPopup && tab != null) {
                        tabCoordinator.closing.closeTab(prompt.tabId)
                    }
                }
            }
        }
    }
}

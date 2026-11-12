package com.jestk.browser.ui.viewmodel.events

import androidx.compose.ui.geometry.Offset
import com.jestk.browser.domain.model.OverlayType
import com.jestk.browser.domain.model.ScreenType
import com.jestk.browser.ui.viewmodel.BrowserUiEvent
import com.jestk.browser.ui.viewmodel.BrowserUiState
import com.jestk.browser.ui.viewmodel.OverlayUiEvent
import com.jestk.browser.ui.viewmodel.SearchCoordinator
import com.jestk.browser.ui.viewmodel.TabCoordinator

internal class OverlayEventHandler(
    private val tabCoordinator: TabCoordinator,
    private val searchCoordinator: SearchCoordinator,
    private val getCurrentState: () -> BrowserUiState,
    private val updateUiState: ((BrowserUiState) -> BrowserUiState) -> Unit,
    private val dispatch: (BrowserUiEvent) -> Unit,
    private val handleWebScroll: (Float, Int, Int) -> Unit,
) {
    fun handle(event: OverlayUiEvent) {
        when (event) {
            is BrowserUiEvent.OpenOverlay -> {
                updateUiState { it.copy(activeOverlay = event.overlay) }
            }

            is BrowserUiEvent.DismissOverlay -> {
                updateUiState {
                    if (it.activeScreen != ScreenType.NONE) {
                        it.copy(activeScreen = ScreenType.NONE)
                    } else {
                        it.copy(activeOverlay = OverlayType.NONE, isSearchHistoryExpanded = false)
                    }
                }
            }

            is BrowserUiEvent.OpenHome -> {
                updateUiState { it.copy(activeOverlay = OverlayType.HOME) }
            }

            is BrowserUiEvent.OpenSearch -> {
                val current = getCurrentState()
                val isHome =
                    current.activeOverlay == OverlayType.HOME ||
                        current.currentUrl.isEmpty() ||
                        current.currentUrl == "about:blank"
                if (event.openInNewTab && !isHome) {
                    val existingHomeTab =
                        current.tabs.firstOrNull {
                            it.isHomeTab
                        }
                    if (existingHomeTab != null) {
                        tabCoordinator.activation.selectTab(existingHomeTab.id)
                        val prefill = event.prefilledUrl ?: ""
                        updateUiState {
                            it.copy(
                                activeOverlay = OverlayType.SEARCH,
                                searchQuery = prefill,
                                isSearchHistoryExpanded = false,
                                isLoadingMoreHistory = false,
                                hasMoreHistory = true,
                            )
                        }
                        searchCoordinator.updateHistorySuggestions(prefill)
                    } else {
                        tabCoordinator.opening.openNewTabForSearch { searchCoordinator.updateHistorySuggestions("") }
                    }
                } else {
                    val prefill = event.prefilledUrl ?: if (isHome) "" else current.currentUrl
                    updateUiState {
                        it.copy(
                            activeOverlay = OverlayType.SEARCH,
                            searchQuery = prefill,
                            isSearchHistoryExpanded = false,
                            isLoadingMoreHistory = false,
                            hasMoreHistory = true,
                        )
                    }
                    searchCoordinator.updateHistorySuggestions(prefill)
                }
            }

            is BrowserUiEvent.OpenTabGrid -> {
                updateUiState { it.copy(activeOverlay = OverlayType.TAB_GRID) }
            }

            is BrowserUiEvent.OpenSettings -> {
                updateUiState {
                    it.copy(activeScreen = ScreenType.SETTINGS, activeOverlay = OverlayType.NONE)
                }
            }

            is BrowserUiEvent.OpenDownloads -> {
                updateUiState {
                    it.copy(activeScreen = ScreenType.DOWNLOADS, activeOverlay = OverlayType.NONE)
                }
            }

            is BrowserUiEvent.OnWebScroll -> {
                handleWebScroll(event.deltaY, event.scrollY, event.viewportHeight)
            }

            is BrowserUiEvent.SetBottomBarVisibility -> {
                updateUiState { it.copy(isBottomBarVisible = event.visible) }
            }

            is BrowserUiEvent.ToggleMenu -> {
                val current = getCurrentState()
                val target = event.expanded ?: !current.isMenuExpanded
                updateUiState { it.copy(isMenuExpanded = target) }
            }

            is BrowserUiEvent.SetMenuExpanded -> {
                updateUiState { it.copy(isMenuExpanded = event.expanded) }
            }

            is BrowserUiEvent.ShowLinkContextMenu -> {
                val anchor =
                    if (event.anchorX != null && event.anchorY != null) {
                        Offset(event.anchorX, event.anchorY)
                    } else {
                        null
                    }
                updateUiState {
                    it.copy(
                        linkContextMenuUrl = event.url,
                        contextMenuAnchor = anchor,
                    )
                }
            }

            is BrowserUiEvent.DismissLinkContextMenu -> {
                updateUiState {
                    it.copy(
                        linkContextMenuUrl = null,
                        contextMenuAnchor = null,
                    )
                }
            }

            is BrowserUiEvent.ShowImageContextMenu -> {
                val anchor =
                    if (event.anchorX != null && event.anchorY != null) {
                        Offset(event.anchorX, event.anchorY)
                    } else {
                        null
                    }
                updateUiState {
                    it.copy(
                        selectedImageUrl = event.imageUrl,
                        imageContextMenuLinkUrl = event.linkUrl,
                        contextMenuAnchor = anchor,
                    )
                }
            }

            is BrowserUiEvent.DismissImageContextMenu -> {
                updateUiState {
                    it.copy(
                        selectedImageUrl = null,
                        imageContextMenuLinkUrl = null,
                        contextMenuAnchor = null,
                    )
                }
            }

            is BrowserUiEvent.CopyImageUrl -> {
                updateUiState {
                    it.copy(
                        selectedImageUrl = null,
                        imageContextMenuLinkUrl = null,
                        contextMenuAnchor = null,
                    )
                }
            }

            is BrowserUiEvent.DownloadImage -> {
                updateUiState {
                    it.copy(
                        selectedImageUrl = null,
                        imageContextMenuLinkUrl = null,
                        contextMenuAnchor = null,
                    )
                }
            }

            is BrowserUiEvent.PreviewImageInNewTab -> {
                updateUiState {
                    it.copy(
                        selectedImageUrl = null,
                        imageContextMenuLinkUrl = null,
                        contextMenuAnchor = null,
                    )
                }
                dispatch(BrowserUiEvent.OpenNewTab(event.imageUrl))
            }
        }
    }
}

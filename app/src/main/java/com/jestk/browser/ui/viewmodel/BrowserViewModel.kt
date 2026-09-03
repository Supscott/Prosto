package com.jestk.browser.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jestk.browser.domain.model.OverlayType
import com.jestk.browser.domain.model.RecentTabItem
import com.jestk.browser.domain.model.Tab
import com.jestk.browser.domain.repository.FavoriteRepository
import com.jestk.browser.domain.repository.HistoryRepository
import com.jestk.browser.domain.repository.RecentTabRepository
import com.jestk.browser.domain.repository.SettingsRepository
import com.jestk.browser.domain.repository.TabRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class BrowserViewModel(
    private val tabRepository: TabRepository,
    private val historyRepository: HistoryRepository,
    private val favoriteRepository: FavoriteRepository,
    private val settingsRepository: SettingsRepository,
    private val recentTabRepository: RecentTabRepository? = null,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : ViewModel() {

    private val tabOperationMutex = Mutex()

    private val _uiState = MutableStateFlow(BrowserUiState())
    val uiState: StateFlow<BrowserUiState> = _uiState.asStateFlow()

    private val _navigationCommands = MutableSharedFlow<NavigationCommand>(extraBufferCapacity = 16)
    val navigationCommands: SharedFlow<NavigationCommand> = _navigationCommands.asSharedFlow()

    init {
        viewModelScope.launch(ioDispatcher) {
            restoreSession()
            observeDatabaseFlows()
        }
    }

    private suspend fun restoreSession() {
        val initialTabs = tabRepository.getInitialTabs()
        val initialSettings = settingsRepository.getCurrentSettings()

        val activeTab: Tab = if (initialTabs.isEmpty()) {
            val newTab = tabRepository.createTab(url = "", title = "Home")
            tabRepository.setActiveTab(newTab.id)
            newTab.copy(isActive = true)
        } else {
            val found = initialTabs.find { it.isActive }
                ?: initialTabs.maxByOrNull { it.lastActiveTimestamp }
                ?: initialTabs.first()
            tabRepository.setActiveTab(found.id)
            found.copy(isActive = true)
        }

        val currentTabs = tabRepository.getInitialTabs()
        val tabsWithActive = if (currentTabs.isEmpty()) listOf(activeTab) else currentTabs

        val hasWebUrl = activeTab.url.isNotEmpty() && activeTab.url != "about:blank"
        _uiState.update { state ->
            state.copy(
                tabs = tabsWithActive,
                activeTabId = activeTab.id,
                currentUrl = activeTab.url,
                pageTitle = activeTab.title,
                canGoBack = false,
                canGoForward = false,
                isLoading = hasWebUrl,
                loadingProgress = if (hasWebUrl) 0.15f else 0f,
                settings = initialSettings,
                activeOverlay = if (hasWebUrl) OverlayType.NONE else OverlayType.HOME
            )
        }
    }

    private fun observeDatabaseFlows() {
        viewModelScope.launch(ioDispatcher) {
            tabRepository.getAllTabs().collectLatest { tabs ->
                _uiState.update { state ->
                    val activeId = tabs.firstOrNull { it.isActive }?.id
                        ?: if (tabs.any { it.id == state.activeTabId }) {
                            state.activeTabId
                        } else {
                            tabs.firstOrNull()?.id
                        }
                    val currentTab = tabs.find { it.id == activeId }
                    state.copy(
                        tabs = tabs,
                        activeTabId = activeId,
                        currentUrl = currentTab?.url ?: state.currentUrl,
                        pageTitle = currentTab?.title ?: state.pageTitle,
                        canGoBack = currentTab?.canGoBack ?: state.canGoBack,
                        canGoForward = currentTab?.canGoForward ?: state.canGoForward
                    )
                }
            }
        }

        if (recentTabRepository != null) {
            viewModelScope.launch(ioDispatcher) {
                recentTabRepository.getRecentTabs(10).collectLatest { recents ->
                    _uiState.update { it.copy(recentTabs = recents) }
                }
            }
        } else {
            viewModelScope.launch(ioDispatcher) {
                tabRepository.getRecentTabs(6).collectLatest { recents ->
                    _uiState.update { state ->
                        state.copy(
                            recentTabs = recents.map { tab ->
                                RecentTabItem(
                                    url = tab.url,
                                    title = tab.title,
                                    snapshotPath = tab.snapshotPath,
                                    faviconUrl = tab.faviconUrl,
                                    lastVisitedTimestamp = tab.lastActiveTimestamp
                                )
                            }
                        )
                    }
                }
            }
        }

        viewModelScope.launch(ioDispatcher) {
            favoriteRepository.getFavorites().collectLatest { favorites ->
                _uiState.update { it.copy(favorites = favorites) }
            }
        }

        viewModelScope.launch(ioDispatcher) {
            settingsRepository.getSettings().collectLatest { settings ->
                _uiState.update { it.copy(settings = settings) }
            }
        }
    }

    fun onEvent(event: BrowserUiEvent) {
        when (event) {
            is BrowserUiEvent.OpenNewTab -> openNewTab(event.url)
            is BrowserUiEvent.OpenRecentTab -> openRecentTab(event.item)
            is BrowserUiEvent.SelectTab -> selectTab(event.tabId)
            is BrowserUiEvent.CloseTab -> closeTab(event.tabId)
            is BrowserUiEvent.CloseAllTabs -> closeAllTabs()
            is BrowserUiEvent.ReorderTabs -> reorderTabs(event.orderedTabs)
            is BrowserUiEvent.SwipeNextTab -> switchAdjacentTab(1)
            is BrowserUiEvent.SwipePrevTab -> switchAdjacentTab(-1)

            is BrowserUiEvent.OpenOverlay -> _uiState.update { it.copy(activeOverlay = event.overlay) }
            is BrowserUiEvent.DismissOverlay -> _uiState.update { it.copy(activeOverlay = OverlayType.NONE) }
            is BrowserUiEvent.OpenHome -> _uiState.update { it.copy(activeOverlay = OverlayType.HOME) }
            is BrowserUiEvent.OpenSearch -> {
                val prefill = event.prefilledUrl ?: _uiState.value.currentUrl
                _uiState.update { it.copy(activeOverlay = OverlayType.SEARCH, searchQuery = prefill) }
                updateHistorySuggestions(prefill)
            }
            is BrowserUiEvent.OpenTabGrid -> _uiState.update { it.copy(activeOverlay = OverlayType.TAB_GRID) }
            is BrowserUiEvent.OpenSettings -> _uiState.update { it.copy(activeOverlay = OverlayType.SETTINGS) }

            is BrowserUiEvent.LoadUrl -> loadUrl(event.url)
            is BrowserUiEvent.SubmitSearch -> submitSearch(event.query)
            is BrowserUiEvent.UpdateSearchQuery -> {
                _uiState.update { it.copy(searchQuery = event.query) }
                updateHistorySuggestions(event.query)
            }
            is BrowserUiEvent.GoBack -> handleGoBack()
            is BrowserUiEvent.GoForward -> _navigationCommands.tryEmit(NavigationCommand.GoForward)
            is BrowserUiEvent.Reload -> _navigationCommands.tryEmit(NavigationCommand.Reload)
            is BrowserUiEvent.StopLoading -> {
                _navigationCommands.tryEmit(NavigationCommand.StopLoading)
                _uiState.update { it.copy(isLoading = false) }
            }

            is BrowserUiEvent.OnPageStarted -> handlePageStarted(event.tabId, event.url)
            is BrowserUiEvent.OnPageFinished -> handlePageFinished(event.tabId, event.url, event.title)
            is BrowserUiEvent.OnProgressChanged -> handleProgressChanged(event.tabId, event.progress)
            is BrowserUiEvent.OnNavigationStateChanged -> handleNavState(event.tabId, event.canGoBack, event.canGoForward)
            is BrowserUiEvent.OnSnapshotCaptured -> handleSnapshot(event.tabId, event.snapshotPath)
            is BrowserUiEvent.OnFaviconReceived -> Unit
            is BrowserUiEvent.UpdateFaviconUrl -> handleUpdateFavicon(event.tabId, event.faviconUrl)

            is BrowserUiEvent.AddCurrentTabToFavorites -> addCurrentToFavorites()
            is BrowserUiEvent.AddFavorite -> viewModelScope.launch(ioDispatcher) {
                favoriteRepository.addFavorite(event.title, event.url)
            }
            is BrowserUiEvent.RemoveFavorite -> viewModelScope.launch(ioDispatcher) {
                favoriteRepository.deleteFavorite(event.id)
            }
            is BrowserUiEvent.ToggleFavoritesExpanded -> _uiState.update { it.copy(isFavoritesExpanded = !it.isFavoritesExpanded) }
            is BrowserUiEvent.DeleteHistoryItem -> viewModelScope.launch(ioDispatcher) {
                historyRepository.deleteHistory(event.id)
            }
            is BrowserUiEvent.ClearAllHistory -> viewModelScope.launch(ioDispatcher) {
                historyRepository.clearHistory()
            }
            is BrowserUiEvent.SelectHistorySuggestion -> loadUrl(event.item.url)

            is BrowserUiEvent.SetThemeMode -> {
                _uiState.update { it.copy(settings = it.settings.copy(themeMode = event.mode)) }
                viewModelScope.launch(ioDispatcher) {
                    settingsRepository.setThemeMode(event.mode)
                }
            }
            is BrowserUiEvent.SetSearchEngine -> {
                _uiState.update { it.copy(settings = it.settings.copy(searchEngine = event.engine)) }
                viewModelScope.launch(ioDispatcher) {
                    settingsRepository.setSearchEngine(event.engine)
                }
            }
            is BrowserUiEvent.SetAdBlockerEnabled -> {
                _uiState.update { it.copy(settings = it.settings.copy(isAdBlockerEnabled = event.enabled)) }
                viewModelScope.launch(ioDispatcher) {
                    settingsRepository.setAdBlockerEnabled(event.enabled)
                }
            }

            is BrowserUiEvent.OnWebScroll -> handleWebScroll(event.deltaY)
            is BrowserUiEvent.SetBottomBarVisibility -> _uiState.update { it.copy(isBottomBarVisible = event.visible) }

            is BrowserUiEvent.SetVideoPlaying -> _uiState.update { it.copy(isVideoPlaying = event.isPlaying) }
            is BrowserUiEvent.SetFullscreenCustomView -> _uiState.update {
                it.copy(fullscreenCustomView = event.view, isFullscreenCustomView = event.isFullscreen)
            }

            is BrowserUiEvent.HandleBackPress -> handleBackNavigation()
        }
    }

    private fun openNewTab(url: String) {
        viewModelScope.launch(ioDispatcher) {
            tabOperationMutex.withLock {
                val title = if (url.isEmpty() || url == "about:blank") "Home" else "Loading..."
                val created = tabRepository.createTab(url = url, title = title)
                tabRepository.setActiveTab(created.id)
                val currentTabs = _uiState.value.tabs.map { it.copy(isActive = false) }
                val updatedTabs = currentTabs + created.copy(isActive = true)
                _uiState.update {
                    it.copy(
                        tabs = updatedTabs,
                        activeTabId = created.id,
                        currentUrl = url,
                        pageTitle = title,
                        isLoading = url.isNotEmpty(),
                        activeOverlay = if (url.isEmpty() || url == "about:blank") OverlayType.HOME else OverlayType.NONE
                    )
                }
                if (url.isNotEmpty() && url != "about:blank") {
                    _navigationCommands.tryEmit(NavigationCommand.LoadUrl(url))
                }
            }
        }
    }

    private fun openRecentTab(item: RecentTabItem) {
        val existingOpenTab = _uiState.value.tabs.firstOrNull { it.url == item.url }
        if (existingOpenTab != null) {
            selectTab(existingOpenTab.id)
            _uiState.update { it.copy(activeOverlay = OverlayType.NONE) }
        } else {
            val currentTab = _uiState.value.activeTab
            if (currentTab != null && (currentTab.url.isEmpty() || currentTab.url == "about:blank")) {
                loadUrl(item.url)
                _uiState.update { it.copy(activeOverlay = OverlayType.NONE) }
            } else {
                openNewTab(item.url)
                _uiState.update { it.copy(activeOverlay = OverlayType.NONE) }
            }
        }
    }

    private fun selectTab(tabId: String) {
        val selected = _uiState.value.tabs.find { it.id == tabId }
        if (selected != null) {
            _uiState.update {
                it.copy(
                    tabs = it.tabs.map { t -> t.copy(isActive = t.id == tabId) },
                    activeTabId = tabId,
                    currentUrl = selected.url,
                    pageTitle = selected.title,
                    canGoBack = selected.canGoBack,
                    canGoForward = selected.canGoForward,
                    activeOverlay = if (selected.url.isEmpty() || selected.url == "about:blank") OverlayType.HOME else OverlayType.NONE
                )
            }
        }
        viewModelScope.launch(ioDispatcher) {
            tabOperationMutex.withLock {
                tabRepository.setActiveTab(tabId)
            }
        }
    }

    private fun closeTab(tabId: String) {
        viewModelScope.launch(ioDispatcher) {
            tabOperationMutex.withLock {
                tabRepository.closeTab(tabId)
                val remainingTabs = _uiState.value.tabs.filter { it.id != tabId }
                if (remainingTabs.isEmpty()) {
                    val newTab = tabRepository.createTab(url = "", title = "Home")
                    tabRepository.setActiveTab(newTab.id)
                    _uiState.update {
                        it.copy(
                            tabs = listOf(newTab.copy(isActive = true)),
                            activeTabId = newTab.id,
                            currentUrl = "",
                            pageTitle = "Home",
                            activeOverlay = OverlayType.HOME
                        )
                    }
                } else {
                    val nextActive = if (remainingTabs.any { it.id == _uiState.value.activeTabId }) {
                        remainingTabs.first { it.id == _uiState.value.activeTabId }
                    } else {
                        remainingTabs.first()
                    }
                    tabRepository.setActiveTab(nextActive.id)
                    _uiState.update {
                        it.copy(
                            tabs = remainingTabs.map { t -> t.copy(isActive = t.id == nextActive.id) },
                            activeTabId = nextActive.id,
                            currentUrl = nextActive.url,
                            pageTitle = nextActive.title,
                            canGoBack = nextActive.canGoBack,
                            canGoForward = nextActive.canGoForward,
                            activeOverlay = if (nextActive.url.isEmpty() || nextActive.url == "about:blank") OverlayType.HOME else OverlayType.NONE
                        )
                    }
                }
            }
        }
    }

    private fun closeAllTabs() {
        viewModelScope.launch(ioDispatcher) {
            tabOperationMutex.withLock {
                tabRepository.closeAllTabs()
                val newTab = tabRepository.createTab(url = "", title = "Home")
                tabRepository.setActiveTab(newTab.id)
                _uiState.update {
                    it.copy(
                        tabs = listOf(newTab.copy(isActive = true)),
                        activeTabId = newTab.id,
                        currentUrl = "",
                        pageTitle = "Home",
                        activeOverlay = OverlayType.HOME
                    )
                }
            }
        }
    }

    private fun switchAdjacentTab(delta: Int) {
        val currentTabs = _uiState.value.tabs
        if (currentTabs.size <= 1) return
        val currentIndex = _uiState.value.activeTabIndex
        val targetIndex = (currentIndex + delta).coerceIn(0, currentTabs.lastIndex)
        if (targetIndex != currentIndex) {
            selectTab(currentTabs[targetIndex].id)
        }
    }

    private fun reorderTabs(orderedTabs: List<Tab>) {
        viewModelScope.launch(ioDispatcher) {
            tabRepository.reorderTabs(orderedTabs)
        }
    }

    private fun loadUrl(rawUrl: String) {
        val trimmed = rawUrl.trim()
        val finalUrl = when {
            trimmed.isEmpty() -> ""
            trimmed == "about:blank" -> ""
            trimmed.startsWith("http://") || trimmed.startsWith("https://") -> trimmed
            trimmed.startsWith("localhost") || (trimmed.contains(".") && !trimmed.contains(" ")) || (trimmed.contains(":") && !trimmed.contains(" ")) -> "https://$trimmed"
            else -> _uiState.value.settings.searchEngine.buildSearchUrl(trimmed)
        }

        val activeId = _uiState.value.activeTabId ?: return
        if (finalUrl.isEmpty()) {
            _uiState.update {
                it.copy(
                    currentUrl = "",
                    pageTitle = "Home",
                    isLoading = false,
                    activeOverlay = OverlayType.HOME
                )
            }
            viewModelScope.launch(ioDispatcher) {
                tabRepository.updateTabDetails(activeId, "", "Home", canGoBack = false, canGoForward = false)
            }
            return
        }

        _uiState.update {
            it.copy(
                currentUrl = finalUrl,
                isLoading = true,
                loadingProgress = 0.1f,
                activeOverlay = OverlayType.NONE
            )
        }
        val currentTab = _uiState.value.tabs.firstOrNull { it.id == activeId }
        if (currentTab?.faviconUrl.isNullOrEmpty()) {
            val fallbackFavicon = com.jestk.browser.engine.FaviconManager.extractFallbackFaviconUrl(finalUrl)
            if (fallbackFavicon != null) {
                handleUpdateFavicon(activeId, fallbackFavicon)
            }
        }
        viewModelScope.launch(ioDispatcher) {
            tabRepository.updateTabDetails(activeId, finalUrl, finalUrl, canGoBack = false, canGoForward = false)
        }
        _navigationCommands.tryEmit(NavigationCommand.LoadUrl(finalUrl))
    }

    private fun submitSearch(query: String) {
        loadUrl(query)
    }

    private fun updateHistorySuggestions(query: String) {
        if (query.isBlank()) {
            _uiState.update { it.copy(searchHistorySuggestions = emptyList()) }
            return
        }
        viewModelScope.launch(ioDispatcher) {
            historyRepository.searchHistory(query).collectLatest { results ->
                _uiState.update { it.copy(searchHistorySuggestions = results) }
            }
        }
    }

    private fun handlePageStarted(tabId: String, url: String) {
        if (_uiState.value.activeTabId == tabId) {
            _uiState.update { it.copy(currentUrl = url, isLoading = true, loadingProgress = 0.15f) }
        }
        val currentTab = _uiState.value.tabs.firstOrNull { it.id == tabId }
        if (currentTab?.faviconUrl.isNullOrEmpty()) {
            val fallbackFavicon = com.jestk.browser.engine.FaviconManager.extractFallbackFaviconUrl(url)
            if (fallbackFavicon != null) {
                handleUpdateFavicon(tabId, fallbackFavicon)
            }
        }
    }

    private fun handlePageFinished(tabId: String, url: String, title: String) {
        val displayTitle = title.ifEmpty { url }
        if (_uiState.value.activeTabId == tabId) {
            _uiState.update {
                it.copy(
                    currentUrl = url,
                    pageTitle = displayTitle,
                    isLoading = false,
                    loadingProgress = 1.0f
                )
            }
        }
        viewModelScope.launch(ioDispatcher) {
            val tab = tabRepository.getTabById(tabId)
            tabRepository.updateTabDetails(
                id = tabId,
                url = url,
                title = displayTitle,
                canGoBack = tab?.canGoBack ?: _uiState.value.canGoBack,
                canGoForward = tab?.canGoForward ?: false
            )
            if (url.isNotEmpty() && !url.startsWith("about:")) {
                historyRepository.recordVisit(url, displayTitle)
                recentTabRepository?.recordRecentTab(
                    url = url,
                    title = displayTitle,
                    snapshotPath = tab?.snapshotPath,
                    faviconUrl = tab?.faviconUrl
                )
            }
        }
    }

    private fun handleProgressChanged(tabId: String, progress: Int) {
        if (_uiState.value.activeTabId == tabId) {
            _uiState.update {
                it.copy(
                    loadingProgress = (progress / 100f).coerceIn(0f, 1f),
                    isLoading = progress < 100
                )
            }
        }
    }

    private fun handleNavState(tabId: String, canGoBack: Boolean, canGoForward: Boolean) {
        if (_uiState.value.activeTabId == tabId) {
            _uiState.update { it.copy(canGoBack = canGoBack, canGoForward = canGoForward) }
        }
        viewModelScope.launch(ioDispatcher) {
            val tab = tabRepository.getTabById(tabId)
            if (tab != null) {
                tabRepository.updateTabDetails(
                    id = tabId,
                    url = tab.url,
                    title = tab.title,
                    canGoBack = canGoBack,
                    canGoForward = canGoForward
                )
            }
        }
    }

    private fun handleSnapshot(tabId: String, path: String) {
        viewModelScope.launch(ioDispatcher) {
            tabRepository.updateSnapshot(tabId, path)
            val tab = tabRepository.getTabById(tabId)
            if (tab != null && tab.url.isNotEmpty() && !tab.url.startsWith("about:")) {
                recentTabRepository?.updateSnapshot(tab.url, path)
            }
        }
    }

    private fun handleUpdateFavicon(tabId: String, faviconUrl: String) {
        _uiState.update { state ->
            state.copy(
                tabs = state.tabs.map { tab ->
                    if (tab.id == tabId) tab.copy(faviconUrl = faviconUrl) else tab
                }
            )
        }
        viewModelScope.launch(ioDispatcher) {
            tabRepository.updateFavicon(tabId, faviconUrl)
            val tab = tabRepository.getTabById(tabId)
            if (tab != null && tab.url.isNotEmpty() && !tab.url.startsWith("about:")) {
                recentTabRepository?.updateFavicon(tab.url, faviconUrl)
            }
        }
    }

    private fun addCurrentToFavorites() {
        val current = _uiState.value
        if (current.currentUrl.isNotEmpty() && !current.currentUrl.startsWith("about:")) {
            viewModelScope.launch(ioDispatcher) {
                favoriteRepository.addFavorite(
                    title = current.pageTitle.ifEmpty { current.currentUrl },
                    url = current.currentUrl
                )
            }
        }
    }

    private fun handleWebScroll(deltaY: Float) {
        if (deltaY > 10f && _uiState.value.isBottomBarVisible) {
            _uiState.update { it.copy(isBottomBarVisible = false) }
        } else if (deltaY < -10f && !_uiState.value.isBottomBarVisible) {
            _uiState.update { it.copy(isBottomBarVisible = true) }
        }
    }

    private fun handleGoBack() {
        _navigationCommands.tryEmit(NavigationCommand.GoBack)
    }

    private fun handleBackNavigation() {
        val current = _uiState.value
        val isDismissibleOverlay = current.activeOverlay != OverlayType.NONE && current.activeOverlay != OverlayType.HOME
        if (isDismissibleOverlay) {
            _uiState.update { it.copy(activeOverlay = OverlayType.NONE) }
            return
        }

        if (current.canGoBack) {
            _navigationCommands.tryEmit(NavigationCommand.GoBack)
            return
        }

        // Web history is exhausted!
        if (current.tabs.size > 1) {
            current.activeTabId?.let { closeTab(it) }
        } else {
            val isCurrentTabBlankOrHome = current.currentUrl.isEmpty() ||
                    current.currentUrl == "about:blank" ||
                    current.activeOverlay == OverlayType.HOME

            if (!isCurrentTabBlankOrHome) {
                // If it was on a website, close website and return to clean Home tab
                current.activeTabId?.let { tabId ->
                    _uiState.update {
                        it.copy(
                            currentUrl = "",
                            pageTitle = "Home",
                            canGoBack = false,
                            canGoForward = false,
                            activeOverlay = OverlayType.HOME
                        )
                    }
                    viewModelScope.launch(ioDispatcher) {
                        tabRepository.updateTabDetails(tabId, "", "Home", canGoBack = false, canGoForward = false)
                    }
                }
            } else {
                // History exhausted and already on clean Home tab: exit app cleanly without crashing
                _navigationCommands.tryEmit(NavigationCommand.ExitApp)
            }
        }
    }
}

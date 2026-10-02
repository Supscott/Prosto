package com.jestk.browser.ui.viewmodel

import android.content.Context
import android.net.Uri
import androidx.compose.ui.geometry.Offset
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jestk.browser.domain.model.DownloadItem
import com.jestk.browser.domain.model.OverlayType
import com.jestk.browser.domain.model.RecentTabItem
import com.jestk.browser.domain.model.ScreenType
import com.jestk.browser.domain.model.SearchEngine
import com.jestk.browser.domain.model.Tab
import com.jestk.browser.domain.repository.DownloadRepository
import com.jestk.browser.domain.repository.FavoriteRepository
import com.jestk.browser.domain.repository.HistoryRepository
import com.jestk.browser.domain.repository.RecentTabRepository
import com.jestk.browser.domain.repository.SearchEngineRepository
import com.jestk.browser.domain.repository.SettingsRepository
import com.jestk.browser.domain.repository.TabRepository
import com.jestk.browser.engine.FaviconManager
import com.jestk.browser.engine.WebViewPool
import com.jestk.browser.engine.download.HoriDownloadManager
import com.jestk.browser.util.DefaultBrowserHelper
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
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
import java.io.File

data object DismissDownloadSnackbar : BrowserUiEvent

data class DownloadStarted(
    val item: DownloadItem,
) : BrowserUiEvent

class BrowserViewModel(
    private val tabRepository: TabRepository,
    private val historyRepository: HistoryRepository,
    private val favoriteRepository: FavoriteRepository,
    private val settingsRepository: SettingsRepository,
    private val recentTabRepository: RecentTabRepository? = null,
    private val downloadRepository: DownloadRepository? = null,
    private val downloadManager: HoriDownloadManager? = null,
    private val searchEngineRepository: SearchEngineRepository? = null,
    private val context: Context? = null,
    private val webViewPool: WebViewPool? = null,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {
    private val tabOperationMutex = Mutex()
    private val tabThemeColors = java.util.concurrent.ConcurrentHashMap<String, Int>()
    private var historyJob: Job? = null

    companion object {
        private const val HISTORY_PAGE_SIZE = 20
    }

    private val _uiState = MutableStateFlow(BrowserUiState())
    val uiState: StateFlow<BrowserUiState> = _uiState.asStateFlow()

    private val _navigationCommands = MutableSharedFlow<NavigationCommand>(extraBufferCapacity = 16)
    val navigationCommands: SharedFlow<NavigationCommand> = _navigationCommands.asSharedFlow()

    init {
        webViewPool?.onDownloadStarted = { item -> onDownloadStarted(item) }
        viewModelScope.launch(ioDispatcher) {
            restoreSession()
            observeDatabaseFlows()
        }
    }

    override fun onCleared() {
        super.onCleared()
        webViewPool?.onDownloadStarted = null
    }

    private suspend fun restoreSession() {
        tabOperationMutex.withLock {
            val initialTabs = tabRepository.getInitialTabs()
            val initialSettings = settingsRepository.getCurrentSettings()

            // Prepopulate tabThemeColors cache from DB-persisted colors
            initialTabs.forEach { tab ->
                tab.themeColor?.let { color -> tabThemeColors[tab.id] = color }
            }

            val currentEngines = if (searchEngineRepository != null) {
                searchEngineRepository.ensureDefaultEnginesSeeded()
                val engines = searchEngineRepository.getAllSearchEngines()
                if (engines.isNotEmpty()) engines else SearchEngine.DEFAULT_ENGINES
            } else {
                SearchEngine.DEFAULT_ENGINES
            }
            val resolvedEngine = currentEngines.firstOrNull { it.id == initialSettings.searchEngine.id }
                ?: currentEngines.first()
            val effectiveSettings = initialSettings.copy(searchEngine = resolvedEngine)

            // If tabs were already populated in _uiState (e.g. openNewTab ran first during quick user interaction)
            val currentUiTabs = _uiState.value.tabs
            if (currentUiTabs.isNotEmpty()) {
                val currentActiveTab = _uiState.value.activeTab ?: currentUiTabs.first()
                webViewPool?.onTabActivated(currentActiveTab.id, false)
                val hasWebUrl = currentActiveTab.url.isNotEmpty() && currentActiveTab.url != "about:blank"
                val initialThemeColor = if (hasWebUrl) (tabThemeColors[currentActiveTab.id] ?: currentActiveTab.themeColor) else null
                _uiState.update { state ->
                    state.copy(
                        settings = effectiveSettings,
                        searchEngines = currentEngines,
                        activeThemeColor = initialThemeColor ?: state.activeThemeColor,
                        activeOverlay = if (hasWebUrl) OverlayType.NONE else OverlayType.HOME,
                    )
                }
                return@withLock
            }

            val activeTab: Tab =
                if (initialTabs.isEmpty()) {
                    val newTab = tabRepository.createTab(url = "", title = "Home")
                    tabRepository.setActiveTab(newTab.id)
                    newTab.copy(isActive = true)
                } else {
                    val found =
                        initialTabs.find { it.isActive }
                            ?: initialTabs.maxByOrNull { it.lastActiveTimestamp }
                            ?: initialTabs.first()
                    tabRepository.setActiveTab(found.id)
                    found.copy(isActive = true)
                }

            val currentTabs = tabRepository.getInitialTabs()
            val tabsWithActive = if (currentTabs.isEmpty()) listOf(activeTab) else currentTabs

            tabsWithActive.forEach { tab ->
                tab.themeColor?.let { color -> tabThemeColors[tab.id] = color }
            }

            webViewPool?.onTabActivated(activeTab.id, false)
            val hasWebUrl = activeTab.url.isNotEmpty() && activeTab.url != "about:blank"
            val initialThemeColor = if (hasWebUrl) (tabThemeColors[activeTab.id] ?: activeTab.themeColor) else null
            val menuExpandedInitial = effectiveSettings.isMenuExpandedOnWeb

            _uiState.update { state ->
                state.copy(
                    tabs = tabsWithActive,
                    activeTabId = activeTab.id,
                    currentUrl = activeTab.url,
                    pageTitle = activeTab.title,
                    canGoBack = false,
                    canGoForward = false,
                    activeThemeColor = initialThemeColor,
                    isLoading = hasWebUrl,
                    loadingProgress = if (hasWebUrl) 0.15f else 0f,
                    settings = effectiveSettings,
                    searchEngines = currentEngines,
                    isMenuExpanded = menuExpandedInitial,
                    activeOverlay = if (hasWebUrl) OverlayType.NONE else OverlayType.HOME,
                )
            }
        }
    }

    private fun observeDatabaseFlows() {
        viewModelScope.launch(ioDispatcher) {
            tabRepository.getAllTabs().collectLatest { tabs ->
                _uiState.update { state ->
                    val activeId =
                        tabs.firstOrNull { it.isActive }?.id
                            ?: if (tabs.any { it.id == state.activeTabId }) {
                                state.activeTabId
                            } else {
                                tabs.firstOrNull()?.id
                            }
                    val enrichedTabs =
                        tabs.map { tab ->
                            val cached = tabThemeColors[tab.id] ?: state.tabs.find { it.id == tab.id }?.themeColor
                            if (cached != null) {
                                tabThemeColors[tab.id] = cached
                                tab.copy(themeColor = cached)
                            } else {
                                tab
                            }
                        }
                    val currentTab = enrichedTabs.find { it.id == activeId }
                    val isHome = currentTab?.url.isNullOrEmpty() || currentTab?.url == "about:blank"
                    val activeColor = if (activeId != null) tabThemeColors[activeId] else null
                    state.copy(
                        tabs = enrichedTabs,
                        activeTabId = activeId,
                        currentUrl = currentTab?.url ?: state.currentUrl,
                        pageTitle = currentTab?.title ?: state.pageTitle,
                        canGoBack = currentTab?.canGoBack ?: state.canGoBack,
                        canGoForward = currentTab?.canGoForward ?: state.canGoForward,
                        activeThemeColor = if (isHome) null else (activeColor ?: state.activeThemeColor),
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
                            recentTabs =
                                recents.map { tab ->
                                    RecentTabItem(
                                        url = tab.url,
                                        title = tab.title,
                                        snapshotPath = tab.snapshotPath,
                                        faviconUrl = tab.faviconUrl,
                                        lastVisitedTimestamp = tab.lastActiveTimestamp,
                                    )
                                },
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
                _uiState.update { state ->
                    val resolvedEngine = state.searchEngines.firstOrNull {
                        it.id.equals(settings.searchEngine.id, ignoreCase = true) ||
                            it.name.equals(settings.searchEngine.id, ignoreCase = true) ||
                            it.displayName.equals(settings.searchEngine.displayName, ignoreCase = true)
                    } ?: settings.searchEngine
                    state.copy(settings = settings.copy(searchEngine = resolvedEngine))
                }
            }
        }

        if (searchEngineRepository != null) {
            viewModelScope.launch(ioDispatcher) {
                searchEngineRepository.getSearchEngines().collectLatest { engines ->
                    _uiState.update { state ->
                        val currentSelected = state.settings.searchEngine
                        val updatedSelected = engines.firstOrNull { it.id == currentSelected.id }
                            ?: engines.firstOrNull()
                            ?: currentSelected
                        state.copy(
                            searchEngines = engines,
                            settings = state.settings.copy(searchEngine = updatedSelected)
                        )
                    }
                }
            }
        }

        if (downloadRepository != null) {
            viewModelScope.launch(ioDispatcher) {
                var isFirstCollection = true
                var previousDownloads = emptyList<DownloadItem>()
                downloadRepository.getAllDownloads().collectLatest { downloads ->
                    if (isFirstCollection) {
                        isFirstCollection = false
                        previousDownloads = downloads
                        _uiState.update { it.copy(downloads = downloads) }
                        return@collectLatest
                    }

                    val previousIds = previousDownloads.map { it.id }.toSet()
                    val newDownloads = downloads.filter { it.id !in previousIds }
                    previousDownloads = downloads

                    val latestNewDownload = newDownloads.maxByOrNull { it.timestamp }
                    if (latestNewDownload != null) {
                        _uiState.update { current ->
                            current.copy(
                                downloads = downloads,
                                lastDownloadNotification = latestNewDownload,
                                showDownloadSnackbar = true,
                                activeDownloadIds = current.activeDownloadIds + latestNewDownload.id,
                            )
                        }
                    } else {
                        _uiState.update { it.copy(downloads = downloads) }
                    }
                }
            }
        }

        if (downloadManager != null) {
            viewModelScope.launch(ioDispatcher) {
                downloadManager.activeDownloads.collectLatest { progressMap ->
                    _uiState.update { current ->
                        current.copy(
                            downloadProgressMap = progressMap,
                            activeDownloadIds = progressMap.keys
                        )
                    }
                }
            }

            viewModelScope.launch(ioDispatcher) {
                downloadManager.completedDownloads.collectLatest { completedItem ->
                    _uiState.update { current ->
                        current.copy(
                            lastDownloadNotification = completedItem,
                            showDownloadSnackbar = true
                        )
                    }
                }
            }
        }
    }

    fun onDownloadStarted(item: DownloadItem) {
        _uiState.update {
            it.copy(
                lastDownloadNotification = item,
                showDownloadSnackbar = true,
                activeDownloadIds = it.activeDownloadIds + item.id,
            )
        }
    }

    fun dismissDownloadSnackbar() {
        _uiState.update {
            it.copy(showDownloadSnackbar = false)
        }
    }

    fun updateDefaultBrowserStatus(isDefault: Boolean) {
        _uiState.update {
            it.copy(isDefaultBrowser = isDefault)
        }
    }

    fun checkDefaultBrowser(context: Context) {
        val isDefault = DefaultBrowserHelper.isDefaultBrowser(context)
        updateDefaultBrowserStatus(isDefault)
    }

    fun onEvent(event: BrowserUiEvent) {
        when (event) {
            is BrowserUiEvent.OpenNewTab -> {
                openNewTab(event.url)
            }

            is BrowserUiEvent.OpenRecentTab -> {
                openRecentTab(event.item)
            }

            is BrowserUiEvent.SelectTab -> {
                selectTab(event.tabId)
            }

            is BrowserUiEvent.CloseTab -> {
                closeTab(event.tabId)
            }

            is BrowserUiEvent.CloseAllTabs -> {
                closeAllTabs()
            }

            is BrowserUiEvent.ReorderTabs -> {
                reorderTabs(event.orderedTabs)
            }

            is BrowserUiEvent.SwipeNextTab -> {
                switchAdjacentTab(1)
            }

            is BrowserUiEvent.SwipePrevTab -> {
                switchAdjacentTab(-1)
            }

            is BrowserUiEvent.UndoCloseTab -> {
                undoCloseTab()
            }

            is BrowserUiEvent.DismissUndoTabCloseSnackbar -> {
                _uiState.update {
                    it.copy(
                        showUndoTabCloseSnackbar = false,
                        recentlyClosedTab = null,
                    )
                }
            }

            is DismissDownloadSnackbar -> {
                dismissDownloadSnackbar()
            }

            is DownloadStarted -> {
                onDownloadStarted(event.item)
            }

            is BrowserUiEvent.OpenOverlay -> {
                _uiState.update { it.copy(activeOverlay = event.overlay) }
            }

            is BrowserUiEvent.DismissOverlay -> {
                _uiState.update {
                    if (it.activeScreen != ScreenType.NONE) {
                        it.copy(activeScreen = ScreenType.NONE)
                    } else {
                        it.copy(activeOverlay = OverlayType.NONE, isSearchHistoryExpanded = false)
                    }
                }
            }

            is BrowserUiEvent.OpenHome -> {
                _uiState.update { it.copy(activeOverlay = OverlayType.HOME) }
            }

            is BrowserUiEvent.OpenSearch -> {
                val current = _uiState.value
                val isHome =
                    current.activeOverlay == OverlayType.HOME ||
                        current.currentUrl.isEmpty() ||
                        current.currentUrl == "about:blank"
                if (event.openInNewTab && !isHome) {
                    openNewTabForSearch()
                } else {
                    val prefill = event.prefilledUrl ?: if (isHome) "" else current.currentUrl
                    _uiState.update {
                        it.copy(
                            activeOverlay = OverlayType.SEARCH,
                            searchQuery = prefill,
                            isSearchHistoryExpanded = false,
                            isLoadingMoreHistory = false,
                            hasMoreHistory = true,
                        )
                    }
                    updateHistorySuggestions(prefill)
                }
            }

            is BrowserUiEvent.OpenTabGrid -> {
                _uiState.update { it.copy(activeOverlay = OverlayType.TAB_GRID) }
            }

            is BrowserUiEvent.OpenSettings -> {
                _uiState.update {
                    it.copy(activeScreen = ScreenType.SETTINGS, activeOverlay = OverlayType.NONE)
                }
            }

            is BrowserUiEvent.OpenDownloads -> {
                _uiState.update {
                    it.copy(activeScreen = ScreenType.DOWNLOADS, activeOverlay = OverlayType.NONE)
                }
            }

            is BrowserUiEvent.PauseDownload -> {
                downloadManager?.pauseDownload(event.id)
            }

            is BrowserUiEvent.ResumeDownload -> {
                downloadManager?.resumeDownload(event.id)
            }

            is BrowserUiEvent.CancelDownload -> {
                downloadManager?.cancelDownload(event.id)
                _uiState.update { current ->
                    val shouldDismissSnackbar = current.lastDownloadNotification?.id == event.id
                    current.copy(
                        activeDownloadIds = current.activeDownloadIds - event.id,
                        downloadProgressMap = current.downloadProgressMap - event.id,
                        showDownloadSnackbar = if (shouldDismissSnackbar) false else current.showDownloadSnackbar,
                        lastDownloadNotification = if (shouldDismissSnackbar) null else current.lastDownloadNotification,
                    )
                }
            }

            is BrowserUiEvent.RetryDownload -> {
                downloadManager?.retryDownload(event.id)
            }

            is BrowserUiEvent.DeleteDownload -> {
                viewModelScope.launch(ioDispatcher) {
                    if (downloadManager != null) {
                        downloadManager.deleteDownload(event.id, event.deleteFile)
                    } else {
                        downloadRepository?.deleteDownload(event.id, event.deleteFile)
                    }
                    _uiState.update { current ->
                        val shouldDismissSnackbar = current.lastDownloadNotification?.id == event.id
                        current.copy(
                            activeDownloadIds = current.activeDownloadIds - event.id,
                            downloadProgressMap = current.downloadProgressMap - event.id,
                            showDownloadSnackbar = if (shouldDismissSnackbar) false else current.showDownloadSnackbar,
                            lastDownloadNotification = if (shouldDismissSnackbar) null else current.lastDownloadNotification,
                        )
                    }
                }
            }

            is BrowserUiEvent.LoadUrl -> {
                loadUrl(event.url)
            }

            is BrowserUiEvent.SubmitSearch -> {
                submitSearch(event.query)
            }

            is BrowserUiEvent.UpdateSearchQuery -> {
                _uiState.update { it.copy(searchQuery = event.query) }
                updateHistorySuggestions(event.query)
            }

            is BrowserUiEvent.GoBack -> {
                handleGoBack()
            }

            is BrowserUiEvent.GoForward -> {
                _navigationCommands.tryEmit(NavigationCommand.GoForward)
            }

            is BrowserUiEvent.Reload -> {
                _navigationCommands.tryEmit(NavigationCommand.Reload)
            }

            is BrowserUiEvent.StopLoading -> {
                _navigationCommands.tryEmit(NavigationCommand.StopLoading)
                _uiState.update { it.copy(isLoading = false) }
            }

            is BrowserUiEvent.SwitchSearchEngineForQuery -> {
                switchSearchEngineForQuery(event.targetEngine, event.query)
            }

            is BrowserUiEvent.OnPageStarted -> {
                handlePageStarted(event.tabId, event.url)
            }

            is BrowserUiEvent.OnPageFinished -> {
                handlePageFinished(event.tabId, event.url, event.title)
            }

            is BrowserUiEvent.OnProgressChanged -> {
                handleProgressChanged(event.tabId, event.progress)
            }

            is BrowserUiEvent.OnNavigationStateChanged -> {
                handleNavState(event.tabId, event.canGoBack, event.canGoForward)
            }

            is BrowserUiEvent.OnSnapshotCaptured -> {
                handleSnapshot(event.tabId, event.snapshotPath)
            }

            is BrowserUiEvent.OnFaviconReceived -> {
                Unit
            }

            is BrowserUiEvent.UpdateFaviconUrl -> {
                handleUpdateFavicon(event.tabId, event.faviconUrl)
            }

            is BrowserUiEvent.OnThemeColorReceived -> {
                handleThemeColor(event.tabId, event.color)
            }

            is BrowserUiEvent.ShowAddFavoriteDialog -> {
                _uiState.update { it.copy(showAddFavoriteDialog = true) }
            }

            is BrowserUiEvent.DismissAddFavoriteDialog -> {
                _uiState.update { it.copy(showAddFavoriteDialog = false) }
            }

            is BrowserUiEvent.AddCurrentTabToFavorites -> {
                addCurrentToFavorites()
            }

            is BrowserUiEvent.AddFavorite -> {
                viewModelScope.launch(ioDispatcher) {
                    val trimmed = event.url.trim()
                    val normalizedUrl = when {
                        trimmed.startsWith("http://") || trimmed.startsWith("https://") -> trimmed
                        else -> "https://$trimmed"
                    }
                    val derivedTitle = event.title.trim().ifBlank {
                        normalizedUrl.removePrefix("https://").removePrefix("http://").removePrefix("www.").substringBefore('/')
                    }
                    val icon = FaviconManager.extractFallbackFaviconUrl(normalizedUrl)
                    favoriteRepository.addFavorite(derivedTitle, normalizedUrl, icon)
                }
            }

            is BrowserUiEvent.RemoveFavorite -> {
                viewModelScope.launch(ioDispatcher) {
                    favoriteRepository.deleteFavorite(event.id)
                }
            }

            is BrowserUiEvent.ToggleFavoritesExpanded -> {
                _uiState.update { it.copy(isFavoritesExpanded = !it.isFavoritesExpanded) }
            }

            is BrowserUiEvent.DeleteHistoryItem -> {
                _uiState.update { state ->
                    state.copy(
                        searchHistorySuggestions = state.searchHistorySuggestions.filterNot { it.id == event.id }
                    )
                }
                viewModelScope.launch(ioDispatcher) {
                    historyRepository.deleteHistory(event.id)
                }
            }

            is BrowserUiEvent.ClearAllHistory -> {
                _uiState.update {
                    it.copy(
                        recentTabs = emptyList(),
                        searchHistorySuggestions = emptyList(),
                        isLoadingMoreHistory = false,
                        hasMoreHistory = false,
                    )
                }
                viewModelScope.launch(ioDispatcher) {
                    historyRepository.clearHistory()
                    if (recentTabRepository != null) {
                        recentTabRepository.clearRecentTabs()
                    } else {
                        tabRepository.clearRecentTabs()
                    }
                }
            }

            is BrowserUiEvent.SelectHistorySuggestion -> {
                loadUrl(event.item.url)
            }

            is BrowserUiEvent.ToggleSearchHistoryExpanded -> {
                val target = !_uiState.value.isSearchHistoryExpanded
                _uiState.update {
                    it.copy(
                        isSearchHistoryExpanded = target,
                        isLoadingMoreHistory = false,
                        hasMoreHistory = true,
                    )
                }
                updateHistorySuggestions(_uiState.value.searchQuery)
            }

            is BrowserUiEvent.SetSearchHistoryExpanded -> {
                _uiState.update {
                    it.copy(
                        isSearchHistoryExpanded = event.expanded,
                        isLoadingMoreHistory = false,
                        hasMoreHistory = true,
                    )
                }
                updateHistorySuggestions(_uiState.value.searchQuery)
            }

            is BrowserUiEvent.LoadMoreSearchHistory -> {
                loadMoreSearchHistory()
            }

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

            is BrowserUiEvent.ShowAddSearchEngineDialog -> {
                _uiState.update {
                    it.copy(
                        showAddEditSearchEngineDialog = true,
                        editingSearchEngine = event.engineToEdit
                    )
                }
            }

            is BrowserUiEvent.DismissAddSearchEngineDialog -> {
                _uiState.update {
                    it.copy(
                        showAddEditSearchEngineDialog = false,
                        editingSearchEngine = null
                    )
                }
            }

            is BrowserUiEvent.SaveSearchEngine -> {
                saveSearchEngine(
                    id = event.id,
                    title = event.title,
                    shortcut = event.shortcut,
                    urlTemplate = event.urlTemplate
                )
            }

            is BrowserUiEvent.DeleteSearchEngine -> {
                deleteSearchEngine(event.id)
            }

            is BrowserUiEvent.ReorderSearchEngines -> {
                reorderSearchEngines(event.orderedEngines)
            }

            is BrowserUiEvent.OnWebScroll -> {
                handleWebScroll(event.deltaY, event.scrollY, event.viewportHeight)
            }

            is BrowserUiEvent.SetBottomBarVisibility -> {
                _uiState.update { it.copy(isBottomBarVisible = event.visible) }
            }

            is BrowserUiEvent.ToggleMenu -> {
                val current = _uiState.value
                val isHome =
                    current.activeOverlay == OverlayType.HOME || current.currentUrl.isEmpty() || current.currentUrl == "about:blank"
                val target = event.expanded ?: !current.isMenuExpanded
                _uiState.update { it.copy(isMenuExpanded = target) }
                if (!isHome) {
                    viewModelScope.launch(ioDispatcher) {
                        settingsRepository.setMenuExpandedOnWeb(target)
                    }
                }
            }

            is BrowserUiEvent.SetMenuExpanded -> {
                val current = _uiState.value
                val isHome =
                    current.activeOverlay == OverlayType.HOME || current.currentUrl.isEmpty() || current.currentUrl == "about:blank"
                _uiState.update { it.copy(isMenuExpanded = event.expanded) }
                if (!isHome) {
                    viewModelScope.launch(ioDispatcher) {
                        settingsRepository.setMenuExpandedOnWeb(event.expanded)
                    }
                }
            }

            is BrowserUiEvent.SetVideoPlaying -> {
                _uiState.update { it.copy(isVideoPlaying = event.isPlaying) }
                _uiState.value.activeTabId?.let { activeId ->
                    webViewPool?.onTabActivated(activeId, event.isPlaying)
                }
            }

            is BrowserUiEvent.SetFullscreenCustomView -> {
                _uiState.update {
                    it.copy(
                        fullscreenCustomView = event.view,
                        isFullscreenCustomView = event.isFullscreen,
                        fullscreenRequestedOrientation = event.requestedOrientation,
                    )
                }
            }

            is BrowserUiEvent.ExitFullscreen -> {
                _navigationCommands.tryEmit(NavigationCommand.ExitFullscreen)
                _uiState.update {
                    it.copy(
                        isFullscreenCustomView = false,
                        fullscreenCustomView = null,
                        fullscreenRequestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED,
                    )
                }
            }

            is BrowserUiEvent.HandleBackPress -> {
                handleBackNavigation()
            }

            is BrowserUiEvent.ShowLinkContextMenu -> {
                val anchor = if (event.anchorX != null && event.anchorY != null) {
                    Offset(event.anchorX, event.anchorY)
                } else {
                    null
                }
                _uiState.update {
                    it.copy(
                        linkContextMenuUrl = event.url,
                        contextMenuAnchor = anchor,
                    )
                }
            }

            is BrowserUiEvent.DismissLinkContextMenu -> {
                _uiState.update {
                    it.copy(
                        linkContextMenuUrl = null,
                        contextMenuAnchor = null,
                    )
                }
            }

            is BrowserUiEvent.OpenTabInBackground -> {
                openTabInBackground(event.url)
            }

            is BrowserUiEvent.UpdateDefaultBrowserStatus -> {
                updateDefaultBrowserStatus(event.isDefault)
            }

            is BrowserUiEvent.CheckDefaultBrowser -> {
                checkDefaultBrowser(event.context)
            }

            is BrowserUiEvent.ShowImageContextMenu -> {
                val anchor = if (event.anchorX != null && event.anchorY != null) {
                    Offset(event.anchorX, event.anchorY)
                } else {
                    null
                }
                _uiState.update {
                    it.copy(
                        selectedImageUrl = event.imageUrl,
                        imageContextMenuLinkUrl = event.linkUrl,
                        contextMenuAnchor = anchor,
                    )
                }
            }

            is BrowserUiEvent.DismissImageContextMenu -> {
                _uiState.update {
                    it.copy(
                        selectedImageUrl = null,
                        imageContextMenuLinkUrl = null,
                        contextMenuAnchor = null,
                    )
                }
            }

            is BrowserUiEvent.CopyImageUrl -> {
                _uiState.update {
                    it.copy(
                        selectedImageUrl = null,
                        imageContextMenuLinkUrl = null,
                        contextMenuAnchor = null,
                    )
                }
            }

            is BrowserUiEvent.DownloadImage -> {
                _uiState.update {
                    it.copy(
                        selectedImageUrl = null,
                        imageContextMenuLinkUrl = null,
                        contextMenuAnchor = null,
                    )
                }
            }

            is BrowserUiEvent.PreviewImageInNewTab -> {
                _uiState.update {
                    it.copy(
                        selectedImageUrl = null,
                        imageContextMenuLinkUrl = null,
                        contextMenuAnchor = null,
                    )
                }
                openNewTab(event.imageUrl)
            }
        }
    }

    private fun openNewTabForSearch() {
        viewModelScope.launch(ioDispatcher) {
            tabOperationMutex.withLock {
                val created = tabRepository.createTab(url = "", title = "Search")
                tabRepository.setActiveTab(created.id)
                webViewPool?.onTabActivated(created.id, false)
                val baseTabs =
                    if (_uiState.value.tabs.isEmpty()) {
                        tabRepository.getInitialTabs().filter { it.id != created.id }
                    } else {
                        _uiState.value.tabs.filter { it.id != created.id }
                    }
                val currentTabs = baseTabs.map { it.copy(isActive = false) }
                val updatedTabs = (currentTabs + created.copy(isActive = true)).distinctBy { it.id }
                _uiState.update {
                    it.copy(
                        tabs = updatedTabs,
                        activeTabId = created.id,
                        currentUrl = "",
                        pageTitle = "Search",
                        isLoading = false,
                        activeThemeColor = null,
                        activeOverlay = OverlayType.SEARCH,
                        searchQuery = "",
                        isSearchHistoryExpanded = false,
                        canGoBack = false,
                        canGoForward = false,
                    )
                }
                updateHistorySuggestions("")
            }
        }
    }

    private fun openNewTab(url: String) {
        viewModelScope.launch(ioDispatcher) {
            tabOperationMutex.withLock {
                val title = if (url.isEmpty() || url == "about:blank") "Home" else "Loading..."
                val created = tabRepository.createTab(url = url, title = title)
                tabRepository.setActiveTab(created.id)
                webViewPool?.onTabActivated(created.id, false)
                val baseTabs =
                    if (_uiState.value.tabs.isEmpty()) {
                        tabRepository.getInitialTabs().filter { it.id != created.id }
                    } else {
                        _uiState.value.tabs.filter { it.id != created.id }
                    }
                val currentTabs = baseTabs.map { it.copy(isActive = false) }
                val updatedTabs = (currentTabs + created.copy(isActive = true)).distinctBy { it.id }
                val isHome = url.isEmpty() || url == "about:blank"
                val targetMenuExpanded = _uiState.value.settings.isMenuExpandedOnWeb
                _uiState.update {
                    it.copy(
                        tabs = updatedTabs,
                        activeTabId = created.id,
                        currentUrl = url,
                        pageTitle = title,
                        isLoading = url.isNotEmpty(),
                        activeThemeColor = null,
                        isMenuExpanded = targetMenuExpanded,
                        activeOverlay = if (isHome) OverlayType.HOME else OverlayType.NONE,
                        linkContextMenuUrl = null,
                        selectedImageUrl = null,
                        isBottomBarVisible = true,
                    )
                }
                if (url.isNotEmpty() && url != "about:blank") {
                    _navigationCommands.tryEmit(NavigationCommand.LoadUrl(url))
                }
            }
        }
    }

    private fun openTabInBackground(url: String) {
        viewModelScope.launch(ioDispatcher) {
            tabOperationMutex.withLock {
                val title = if (url.isEmpty() || url == "about:blank") "Home" else "Loading..."
                val created = tabRepository.createTab(url = url, title = title)
                val baseTabs =
                    if (_uiState.value.tabs.isEmpty()) {
                        tabRepository.getInitialTabs().filter { it.id != created.id }
                    } else {
                        _uiState.value.tabs.filter { it.id != created.id }
                    }
                val updatedTabs = (baseTabs + created.copy(isActive = false)).distinctBy { it.id }
                _uiState.update {
                    it.copy(
                        tabs = updatedTabs,
                        linkContextMenuUrl = null,
                        selectedImageUrl = null,
                    )
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
        webViewPool?.onTabActivated(tabId, _uiState.value.isVideoPlaying)
        val selected = _uiState.value.tabs.find { it.id == tabId }
        if (selected != null) {
            val isHome = selected.url.isEmpty() || selected.url == "about:blank"
            val cachedColor = tabThemeColors[tabId] ?: selected.themeColor
            val targetMenuExpanded = _uiState.value.settings.isMenuExpandedOnWeb
            _uiState.update { state ->
                val updatedTabs =
                    state.tabs.map { t ->
                        val c = tabThemeColors[t.id] ?: t.themeColor
                        t.copy(isActive = t.id == tabId, themeColor = c)
                    }
                state.copy(
                    tabs = updatedTabs,
                    activeTabId = tabId,
                    currentUrl = selected.url,
                    pageTitle = selected.title,
                    canGoBack = selected.canGoBack,
                    canGoForward = selected.canGoForward,
                    activeThemeColor = if (isHome) null else cachedColor,
                    isMenuExpanded = targetMenuExpanded,
                    activeOverlay = if (isHome) OverlayType.HOME else OverlayType.NONE,
                    isBottomBarVisible = true,
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
        tabThemeColors.remove(tabId)
        webViewPool?.removeWebView(tabId)
        val closingTab = _uiState.value.tabs.find { it.id == tabId }
        viewModelScope.launch(ioDispatcher) {
            context?.let { ctx ->
                try {
                    File(ctx.filesDir, "snapshots/tab_$tabId.jpg").delete()
                    FaviconManager.deleteTabFavicons(ctx, tabId)
                } catch (_: Throwable) {
                }
            }
            tabOperationMutex.withLock {
                val currentTabs = _uiState.value.tabs
                val tabToSave = closingTab ?: tabRepository.getTabById(tabId)
                val closedIdx = currentTabs.indexOfFirst { it.id == tabId }
                val wasActive = _uiState.value.activeTabId == tabId
                tabRepository.closeTab(tabId)
                val remainingTabs = currentTabs.filter { it.id != tabId }
                if (remainingTabs.isEmpty()) {
                    val newTab = tabRepository.createTab(url = "", title = "Home")
                    tabRepository.setActiveTab(newTab.id)
                    webViewPool?.onTabActivated(newTab.id, false)
                    _uiState.update {
                        it.copy(
                            tabs = listOf(newTab.copy(isActive = true)),
                            activeTabId = newTab.id,
                            currentUrl = "",
                            pageTitle = "Home",
                            activeThemeColor = null,
                            activeOverlay = if (it.activeOverlay == OverlayType.TAB_GRID) OverlayType.TAB_GRID else OverlayType.HOME,
                            recentlyClosedTab = tabToSave,
                            showUndoTabCloseSnackbar = tabToSave != null,
                        )
                    }
                } else {
                    val nextActive =
                        if (!wasActive && remainingTabs.any { it.id == _uiState.value.activeTabId }) {
                            remainingTabs.first { it.id == _uiState.value.activeTabId }
                        } else if (closedIdx > 0) {
                            currentTabs[closedIdx - 1]
                        } else {
                            remainingTabs.first()
                        }
                    val isNextHome = nextActive.url.isEmpty() || nextActive.url == "about:blank"
                    val nextColor = tabThemeColors[nextActive.id] ?: nextActive.themeColor
                    tabRepository.setActiveTab(nextActive.id)
                    webViewPool?.onTabActivated(nextActive.id, false)
                    _uiState.update {
                        it.copy(
                            tabs = remainingTabs.map { t -> t.copy(isActive = t.id == nextActive.id) },
                            activeTabId = nextActive.id,
                            currentUrl = nextActive.url,
                            pageTitle = nextActive.title,
                            canGoBack = nextActive.canGoBack,
                            canGoForward = nextActive.canGoForward,
                            activeThemeColor = if (isNextHome) null else nextColor,
                            activeOverlay =
                                if (it.activeOverlay == OverlayType.TAB_GRID) {
                                    OverlayType.TAB_GRID
                                } else if (isNextHome) {
                                    OverlayType.HOME
                                } else {
                                    OverlayType.NONE
                                },
                            recentlyClosedTab = tabToSave,
                            showUndoTabCloseSnackbar = tabToSave != null,
                        )
                    }
                }
            }
        }
    }

    private fun undoCloseTab() {
        val tabToRestore = _uiState.value.recentlyClosedTab ?: return
        viewModelScope.launch(ioDispatcher) {
            tabOperationMutex.withLock {
                val created =
                    tabRepository.createTab(
                        url = tabToRestore.url,
                        title = tabToRestore.title,
                        isIncognito = tabToRestore.isIncognito,
                    )
                tabToRestore.snapshotPath?.let { tabRepository.updateSnapshot(created.id, it) }
                tabToRestore.faviconUrl?.let { tabRepository.updateFavicon(created.id, it) }
                tabToRestore.themeColor?.let {
                    tabThemeColors[created.id] = it
                    tabRepository.updateThemeColor(created.id, it)
                }
                tabRepository.setActiveTab(created.id)

                val restoredTab =
                    created.copy(
                        isActive = true,
                        snapshotPath = tabToRestore.snapshotPath,
                        faviconUrl = tabToRestore.faviconUrl,
                        themeColor = tabToRestore.themeColor,
                        canGoBack = tabToRestore.canGoBack,
                        canGoForward = tabToRestore.canGoForward,
                    )

                val updatedTabs =
                    (_uiState.value.tabs.map { it.copy(isActive = false) } + restoredTab)
                        .distinctBy { it.id }

                val isHome = restoredTab.url.isEmpty() || restoredTab.url == "about:blank"
                val themeColor = if (isHome) null else (tabThemeColors[created.id] ?: restoredTab.themeColor)

                _uiState.update { state ->
                    state.copy(
                        tabs = updatedTabs,
                        activeTabId = created.id,
                        currentUrl = restoredTab.url,
                        pageTitle = restoredTab.title,
                        canGoBack = restoredTab.canGoBack,
                        canGoForward = restoredTab.canGoForward,
                        activeThemeColor = themeColor,
                        recentlyClosedTab = null,
                        showUndoTabCloseSnackbar = false,
                        activeOverlay =
                            if (state.activeOverlay == OverlayType.TAB_GRID) {
                                OverlayType.TAB_GRID
                            } else if (isHome) {
                                OverlayType.HOME
                            } else {
                                OverlayType.NONE
                            },
                    )
                }

                if (restoredTab.url.isNotEmpty() && restoredTab.url != "about:blank") {
                    _navigationCommands.tryEmit(NavigationCommand.LoadUrl(restoredTab.url))
                }
            }
        }
    }

    private fun closeAllTabs() {
        tabThemeColors.clear()
        webViewPool?.clearAll()
        viewModelScope.launch(ioDispatcher) {
            context?.let { ctx ->
                try {
                    File(ctx.filesDir, "snapshots").listFiles()?.forEach { it.delete() }
                    File(ctx.filesDir, "favicons").listFiles()?.forEach { it.delete() }
                } catch (_: Throwable) {
                }
            }
            tabOperationMutex.withLock {
                tabRepository.closeAllTabs()
                val newTab = tabRepository.createTab(url = "", title = "Home")
                tabRepository.setActiveTab(newTab.id)
                webViewPool?.onTabActivated(newTab.id, false)
                _uiState.update {
                    it.copy(
                        tabs = listOf(newTab.copy(isActive = true)),
                        activeTabId = newTab.id,
                        currentUrl = "",
                        pageTitle = "Home",
                        activeThemeColor = null,
                        activeOverlay = if (it.activeOverlay == OverlayType.TAB_GRID) OverlayType.TAB_GRID else OverlayType.HOME,
                    )
                }
            }
        }
    }

    private fun switchAdjacentTab(delta: Int) {
        val currentTabs = _uiState.value.tabs
        if (currentTabs.isEmpty() || currentTabs.size <= 1 || currentTabs.lastIndex <= 0) return
        val currentIndex = _uiState.value.activeTabIndex
        val maxIndex = currentTabs.lastIndex
        if (maxIndex < 0) return
        val targetIndex = (currentIndex + delta).coerceIn(0, maxIndex)
        if (targetIndex in currentTabs.indices && targetIndex != currentIndex) {
            selectTab(currentTabs[targetIndex].id)
        }
    }

    private fun reorderTabs(orderedTabs: List<Tab>) {
        viewModelScope.launch(ioDispatcher) {
            tabRepository.reorderTabs(orderedTabs)
        }
    }

    private fun isSameDomain(url1: String?, url2: String?): Boolean {
        if (url1.isNullOrBlank() || url2.isNullOrBlank()) return false
        if (url1 == "about:blank" || url2 == "about:blank") return false
        return try {
            val cleanUrl1 = if (url1.startsWith("http://") || url1.startsWith("https://")) url1 else "https://$url1"
            val cleanUrl2 = if (url2.startsWith("http://") || url2.startsWith("https://")) url2 else "https://$url2"
            val uri1 = Uri.parse(cleanUrl1)
            val uri2 = Uri.parse(cleanUrl2)
            val host1 = uri1.host?.lowercase()?.removePrefix("www.")
            val host2 = uri2.host?.lowercase()?.removePrefix("www.")
            !host1.isNullOrBlank() && host1 == host2
        } catch (_: Throwable) {
            false
        }
    }

    private fun loadUrl(rawUrl: String) {
        val trimmed = rawUrl.trim()
        var matchedShortcutEngine: SearchEngine? = null
        val finalUrl =
            when {
                trimmed.isEmpty() -> {
                    ""
                }

                trimmed == "about:blank" -> {
                    ""
                }

                trimmed.startsWith("http://") || trimmed.startsWith("https://") -> {
                    trimmed
                }

                trimmed.startsWith("localhost") || (trimmed.contains(".") && !trimmed.contains(" ")) ||
                    (trimmed.contains(":") && !trimmed.contains(" ")) -> {
                    "https://$trimmed"
                }

                else -> {
                    val shortcutMatch = Regex("""^([a-zA-Z0-9_-]+)\s+(.+)$""").find(trimmed)
                    val shortcutEngine = if (shortcutMatch != null) {
                        val prefix = shortcutMatch.groupValues[1]
                        _uiState.value.searchEngines.firstOrNull { it.shortcut?.equals(prefix, ignoreCase = true) == true }
                    } else null

                    if (shortcutEngine != null) {
                        matchedShortcutEngine = shortcutEngine
                        viewModelScope.launch(ioDispatcher) {
                            settingsRepository.setSearchEngine(shortcutEngine)
                        }
                        val searchTargetQuery = shortcutMatch!!.groupValues[2].trim()
                        shortcutEngine.buildSearchUrl(searchTargetQuery)
                    } else {
                        _uiState.value.settings.searchEngine.buildSearchUrl(trimmed)
                    }
                }
            }

        val activeId = _uiState.value.activeTabId ?: return
        webViewPool?.onTabActivated(activeId, false)
        if (finalUrl.isEmpty()) {
            _uiState.update {
                val updatedTabs =
                    it.tabs.map { tab ->
                        if (tab.id == activeId) tab.copy(url = "", title = "Home", faviconUrl = null) else tab
                    }
                it.copy(
                    tabs = updatedTabs,
                    currentUrl = "",
                    pageTitle = "Home",
                    isLoading = false,
                    isMenuExpanded = it.settings.isMenuExpandedOnWeb,
                    activeOverlay = OverlayType.HOME,
                )
            }
            viewModelScope.launch(ioDispatcher) {
                tabRepository.updateTabDetails(activeId, "", "Home", canGoBack = false, canGoForward = false)
                tabRepository.updateFavicon(activeId, null)
            }
            return
        }

        val currentTab = _uiState.value.tabs.find { it.id == activeId }
        val sameDomain = currentTab != null && isSameDomain(currentTab.url, finalUrl)
        val newFavicon = if (sameDomain) currentTab?.faviconUrl else null

        _uiState.update {
            val updatedTabs =
                it.tabs.map { tab ->
                    if (tab.id == activeId) tab.copy(url = finalUrl, title = finalUrl, faviconUrl = newFavicon) else tab
                }
            it.copy(
                tabs = updatedTabs,
                currentUrl = finalUrl,
                settings = if (matchedShortcutEngine != null) it.settings.copy(searchEngine = matchedShortcutEngine) else it.settings,
                isLoading = true,
                loadingProgress = 0.1f,
                activeThemeColor = tabThemeColors[activeId] ?: it.tabs.find { tab -> tab.id == activeId }?.themeColor,
                isVideoPlaying = false,
                isMenuExpanded = it.settings.isMenuExpandedOnWeb,
                activeOverlay = OverlayType.NONE,
            )
        }
        viewModelScope.launch(ioDispatcher) {
            tabRepository.updateTabDetails(activeId, finalUrl, finalUrl, canGoBack = false, canGoForward = false)
            if (!sameDomain) {
                tabRepository.updateFavicon(activeId, null)
            }
        }
        _navigationCommands.tryEmit(NavigationCommand.LoadUrl(finalUrl))
    }

    private fun submitSearch(query: String) {
        loadUrl(query)
    }

    private fun switchSearchEngineForQuery(targetEngine: SearchEngine, explicitQuery: String?) {
        val currentCandidate = _uiState.value.currentUrl.ifEmpty { _uiState.value.activeTab?.url.orEmpty() }
        val currentMatch = SearchEngine.findMatchingEngineAndQuery(currentCandidate, _uiState.value.searchEngines)
        val query = explicitQuery ?: currentMatch?.second ?: return

        if (currentMatch?.first?.id == targetEngine.id) return

        val activeId = _uiState.value.activeTabId
        val matchingTab = _uiState.value.tabs
            .filter { tab ->
                if (tab.id == activeId) return@filter false
                val tabMatch = SearchEngine.findMatchingEngineAndQuery(tab.url, _uiState.value.searchEngines)
                tabMatch != null &&
                    tabMatch.first.id == targetEngine.id &&
                    tabMatch.second.equals(query, ignoreCase = true)
            }
            .maxByOrNull { it.lastActiveTimestamp }

        if (matchingTab != null) {
            selectTab(matchingTab.id)
        } else {
            val targetUrl = targetEngine.buildSearchUrl(query)
            openNewTab(targetUrl)
        }
    }

    private fun saveSearchEngine(
        id: String?,
        title: String,
        shortcut: String?,
        urlTemplate: String
    ) {
        val trimmedTitle = title.trim()
        val trimmedShortcut = shortcut?.trim()?.takeIf { it.isNotBlank() }
        val trimmedUrl = urlTemplate.trim()

        val normalizedUrl = when {
            trimmedUrl.startsWith("http://") || trimmedUrl.startsWith("https://") -> trimmedUrl
            else -> "https://$trimmedUrl"
        }

        val finalUrl = if (normalizedUrl.contains("%s")) {
            normalizedUrl
        } else {
            "$normalizedUrl%s"
        }

        val currentEngines = _uiState.value.searchEngines
        val existing = id?.let { engineId -> currentEngines.firstOrNull { it.id == engineId } }

        val iconUrl = if (existing != null && existing.isBuiltIn && existing.searchUrlTemplate == finalUrl) {
            existing.iconUrl
        } else {
            FaviconManager.extractFallbackFaviconUrl(finalUrl)
                ?: existing?.iconUrl
                ?: "file:///android_asset/icons/google.svg"
        }

        val engineToSave = if (existing != null) {
            existing.copy(
                displayName = trimmedTitle,
                searchUrlTemplate = finalUrl,
                shortcut = trimmedShortcut,
                iconUrl = iconUrl
            )
        } else {
            SearchEngine(
                id = java.util.UUID.randomUUID().toString(),
                displayName = trimmedTitle,
                searchUrlTemplate = finalUrl,
                iconUrl = iconUrl,
                shortcut = trimmedShortcut,
                orderPosition = currentEngines.size,
                isBuiltIn = false
            )
        }

        val updatedList = if (existing != null) {
            currentEngines.map { if (it.id == engineToSave.id) engineToSave else it }
        } else {
            currentEngines + engineToSave
        }

        _uiState.update { state ->
            val updatedSettings = if (state.settings.searchEngine.id == engineToSave.id) {
                state.settings.copy(searchEngine = engineToSave)
            } else {
                state.settings
            }
            state.copy(
                searchEngines = updatedList,
                settings = updatedSettings,
                showAddEditSearchEngineDialog = false,
                editingSearchEngine = null
            )
        }

        viewModelScope.launch(ioDispatcher) {
            searchEngineRepository?.saveSearchEngine(engineToSave)
            if (_uiState.value.settings.searchEngine.id == engineToSave.id) {
                settingsRepository.setSearchEngine(engineToSave)
            }
        }
    }

    private fun deleteSearchEngine(id: String) {
        val currentEngines = _uiState.value.searchEngines
        if (currentEngines.size <= 1) return

        val remainingEngines = currentEngines.filterNot { it.id == id }
        val wasSelected = _uiState.value.settings.searchEngine.id == id
        val fallbackEngine = if (wasSelected) remainingEngines.first() else _uiState.value.settings.searchEngine

        _uiState.update { state ->
            state.copy(
                searchEngines = remainingEngines,
                settings = state.settings.copy(searchEngine = fallbackEngine)
            )
        }

        viewModelScope.launch(ioDispatcher) {
            searchEngineRepository?.deleteSearchEngine(id)
            if (wasSelected) {
                settingsRepository.setSearchEngine(fallbackEngine)
            }
        }
    }

    private fun reorderSearchEngines(ordered: List<SearchEngine>) {
        val updated = ordered.mapIndexed { index, engine -> engine.copy(orderPosition = index) }
        _uiState.update { it.copy(searchEngines = updated) }
        viewModelScope.launch(ioDispatcher) {
            searchEngineRepository?.reorderSearchEngines(updated)
        }
    }

    private fun updateHistorySuggestions(query: String) {
        historyJob?.cancel()
        historyJob = viewModelScope.launch(ioDispatcher) {
            val isExpanded = _uiState.value.isSearchHistoryExpanded
            if (isExpanded) {
                _uiState.update { it.copy(isLoadingMoreHistory = false) }
                val initialPage = if (query.isBlank()) {
                    historyRepository.getPagedHistory(limit = HISTORY_PAGE_SIZE, offset = 0)
                } else {
                    historyRepository.searchHistoryPaged(query = query, limit = HISTORY_PAGE_SIZE, offset = 0)
                }
                _uiState.update {
                    it.copy(
                        searchHistorySuggestions = initialPage,
                        hasMoreHistory = initialPage.size >= HISTORY_PAGE_SIZE,
                        isLoadingMoreHistory = false,
                    )
                }
            } else {
                val historyFlow =
                    if (query.isBlank()) {
                        historyRepository.getRecentHistory(10)
                    } else {
                        historyRepository.searchHistory(query)
                    }
                historyFlow.collectLatest { results ->
                    _uiState.update {
                        it.copy(
                            searchHistorySuggestions = results,
                            hasMoreHistory = true,
                            isLoadingMoreHistory = false,
                        )
                    }
                }
            }
        }
    }

    private fun loadMoreSearchHistory() {
        val current = _uiState.value
        if (!current.isSearchHistoryExpanded) return
        if (current.isLoadingMoreHistory) return
        if (!current.hasMoreHistory) return

        _uiState.update { it.copy(isLoadingMoreHistory = true) }
        viewModelScope.launch(ioDispatcher) {
            try {
                val currentSuggestions = _uiState.value.searchHistorySuggestions
                val query = _uiState.value.searchQuery
                val offset = currentSuggestions.size
                val nextPage = if (query.isBlank()) {
                    historyRepository.getPagedHistory(limit = HISTORY_PAGE_SIZE, offset = offset)
                } else {
                    historyRepository.searchHistoryPaged(query = query, limit = HISTORY_PAGE_SIZE, offset = offset)
                }

                _uiState.update { state ->
                    val combined = (state.searchHistorySuggestions + nextPage).distinctBy { it.id }
                    state.copy(
                        searchHistorySuggestions = combined,
                        isLoadingMoreHistory = false,
                        hasMoreHistory = nextPage.size >= HISTORY_PAGE_SIZE,
                    )
                }
            } catch (_: Exception) {
                _uiState.update { it.copy(isLoadingMoreHistory = false) }
            }
        }
    }

    private fun handlePageStarted(
        tabId: String,
        url: String,
    ) {
        if (url.isEmpty() || url == "about:blank") return
        val currentTab = _uiState.value.tabs.find { it.id == tabId }
        val sameDomain = currentTab != null && isSameDomain(currentTab.url, url)
        val newFavicon = if (sameDomain) currentTab?.faviconUrl else null

        _uiState.update { state ->
            val updatedTabs = state.tabs.map { tab ->
                if (tab.id == tabId) tab.copy(url = url, faviconUrl = newFavicon) else tab
            }
            if (state.activeTabId == tabId) {
                state.copy(
                    tabs = updatedTabs,
                    currentUrl = url,
                    isLoading = true,
                    loadingProgress = 0.15f,
                    isBottomBarVisible = true
                )
            } else {
                state.copy(tabs = updatedTabs)
            }
        }
        if (!sameDomain) {
            viewModelScope.launch(ioDispatcher) {
                tabRepository.updateFavicon(tabId, null)
            }
        }
    }

    private fun handlePageFinished(
        tabId: String,
        url: String,
        title: String,
    ) {
        if (url.isEmpty() || url == "about:blank") return
        val displayTitle = title.ifEmpty { url }
        if (_uiState.value.activeTabId == tabId) {
            _uiState.update { state ->
                val updatedTabs = state.tabs.map { tab ->
                    if (tab.id == tabId) tab.copy(url = url, title = displayTitle) else tab
                }
                state.copy(
                    tabs = updatedTabs,
                    currentUrl = url,
                    pageTitle = displayTitle,
                    isLoading = false,
                    loadingProgress = 1.0f,
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
                canGoForward = tab?.canGoForward ?: false,
            )
            if (url.isNotEmpty() && !url.startsWith("about:")) {
                historyRepository.recordVisit(url, displayTitle)
                recentTabRepository?.recordRecentTab(
                    url = url,
                    title = displayTitle,
                    snapshotPath = tab?.snapshotPath,
                    faviconUrl = tab?.faviconUrl,
                )
            }
        }
    }

    private fun handleProgressChanged(
        tabId: String,
        progress: Int,
    ) {
        if (_uiState.value.activeTabId == tabId) {
            _uiState.update {
                it.copy(
                    loadingProgress = (progress / 100f).coerceIn(0f, 1f),
                    isLoading = progress < 100,
                )
            }
        }
    }

    private fun handleNavState(
        tabId: String,
        canGoBack: Boolean,
        canGoForward: Boolean,
    ) {
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
                    canGoForward = canGoForward,
                )
            }
        }
    }

    private fun handleSnapshot(
        tabId: String,
        path: String,
    ) {
        viewModelScope.launch(ioDispatcher) {
            tabRepository.updateSnapshot(tabId, path)
            val tab = tabRepository.getTabById(tabId)
            if (tab != null && tab.url.isNotEmpty() && !tab.url.startsWith("about:")) {
                recentTabRepository?.updateSnapshot(tab.url, path)
            }
        }
    }

    private fun handleUpdateFavicon(
        tabId: String,
        faviconUrl: String?,
    ) {
        _uiState.update { state ->
            state.copy(
                tabs =
                    state.tabs.map { tab ->
                        if (tab.id == tabId) tab.copy(faviconUrl = faviconUrl) else tab
                    },
            )
        }
        viewModelScope.launch(ioDispatcher) {
            tabRepository.updateFavicon(tabId, faviconUrl)
            val tab = tabRepository.getTabById(tabId)
            if (tab != null && tab.url.isNotEmpty() && !tab.url.startsWith("about:") && !faviconUrl.isNullOrEmpty()) {
                recentTabRepository?.updateFavicon(tab.url, faviconUrl)
                if (favoriteRepository.isFavorite(tab.url)) {
                    val persistentIconPath = if (context != null && (faviconUrl.startsWith("/") || faviconUrl.startsWith("file:"))) {
                        FaviconManager.persistFaviconForFavorite(context, faviconUrl)
                    } else null
                    val iconToUpdate = persistentIconPath ?: faviconUrl
                    favoriteRepository.updateFavicon(tab.url, iconToUpdate)
                }
            }
        }
    }

    private fun handleThemeColor(
        tabId: String,
        color: Int?,
    ) {
        if (color != null) {
            tabThemeColors[tabId] = color
        } else {
            tabThemeColors.remove(tabId)
        }
        viewModelScope.launch(ioDispatcher) {
            tabRepository.updateThemeColor(tabId, color)
        }
        _uiState.update { state ->
            val updatedTabs =
                state.tabs.map { tab ->
                    if (tab.id == tabId) tab.copy(themeColor = color) else tab
                }
            if (state.activeTabId == tabId) {
                state.copy(tabs = updatedTabs, activeThemeColor = color)
            } else {
                state.copy(tabs = updatedTabs)
            }
        }
    }

    private fun addCurrentToFavorites() {
        val current = _uiState.value
        if (current.currentUrl.isNotEmpty() && !current.currentUrl.startsWith("about:")) {
            val activeTab = current.tabs.find { it.id == current.activeTabId }
            val tabFavicon = activeTab?.faviconUrl
            viewModelScope.launch(ioDispatcher) {
                val persistentIconPath = if (!tabFavicon.isNullOrEmpty() && context != null && (tabFavicon.startsWith("/") || tabFavicon.startsWith("file:"))) {
                    FaviconManager.persistFaviconForFavorite(context, tabFavicon)
                } else null

                val iconUrl = persistentIconPath
                    ?: (if (!tabFavicon.isNullOrEmpty() && tabFavicon.startsWith("http")) tabFavicon else null)
                    ?: FaviconManager.extractFallbackFaviconUrl(current.currentUrl)

                favoriteRepository.addFavorite(
                    title = current.pageTitle.ifEmpty { current.currentUrl },
                    url = current.currentUrl,
                    iconUrl = iconUrl
                )
            }
        }
    }

    private fun handleWebScroll(
        deltaY: Float,
        scrollY: Int = 0,
        viewportHeight: Int = 0,
    ) {
        val current = _uiState.value
        val isHome = current.activeOverlay == OverlayType.HOME || current.currentUrl.isEmpty() || current.currentUrl == "about:blank"
        if (isHome) {
            if (!current.isBottomBarVisible) {
                _uiState.update { it.copy(isBottomBarVisible = true) }
            }
            return
        }

        if (deltaY > 8f && current.isMenuExpanded) {
            _uiState.update { it.copy(isMenuExpanded = false) }
        }

        // Hiding the dock starts working below h-screen (scrollY > viewportHeight)
        val isBelowHScreen = viewportHeight > 0 && scrollY > viewportHeight
        if (!isBelowHScreen) {
            // Within the first screen (scrollY <= viewportHeight) or scrolled back to top: dock must always be visible
            if (!current.isBottomBarVisible) {
                _uiState.update { it.copy(isBottomBarVisible = true) }
            }
        } else {
            // Below h-screen: hide if scrolling down, show if scrolling up
            if (deltaY > 8f) {
                if (current.isBottomBarVisible) {
                    _uiState.update { it.copy(isBottomBarVisible = false, isMenuExpanded = false) }
                }
            } else if (deltaY < -8f) {
                if (!current.isBottomBarVisible) {
                    _uiState.update { it.copy(isBottomBarVisible = true) }
                }
            }
        }
    }

    private fun handleGoBack() {
        _navigationCommands.tryEmit(NavigationCommand.GoBack)
    }

    private fun handleBackNavigation() {
        val current = _uiState.value
        if (current.showAddEditSearchEngineDialog) {
            _uiState.update { it.copy(showAddEditSearchEngineDialog = false, editingSearchEngine = null) }
            return
        }
        if (current.showAddFavoriteDialog) {
            _uiState.update { it.copy(showAddFavoriteDialog = false) }
            return
        }
        if (current.selectedImageUrl != null) {
            _uiState.update {
                it.copy(
                    selectedImageUrl = null,
                    imageContextMenuLinkUrl = null,
                    contextMenuAnchor = null,
                )
            }
            return
        }
        if (current.activeScreen != ScreenType.NONE) {
            _uiState.update { it.copy(activeScreen = ScreenType.NONE) }
            return
        }
        if (current.linkContextMenuUrl != null) {
            _uiState.update {
                it.copy(
                    linkContextMenuUrl = null,
                    contextMenuAnchor = null,
                )
            }
            return
        }
        if (current.isFullscreenCustomView) {
            onEvent(BrowserUiEvent.ExitFullscreen)
            return
        }
        if (current.isMenuExpanded) {
            _uiState.update { it.copy(isMenuExpanded = false) }
            return
        }
        if (current.isSearchHistoryExpanded) {
            _uiState.update {
                it.copy(
                    isSearchHistoryExpanded = false,
                    isLoadingMoreHistory = false,
                    hasMoreHistory = true,
                )
            }
            updateHistorySuggestions(current.searchQuery)
            return
        }
        val isDismissibleOverlay = current.activeOverlay != OverlayType.NONE && current.activeOverlay != OverlayType.HOME
        if (isDismissibleOverlay) {
            _uiState.update {
                it.copy(
                    activeOverlay = OverlayType.NONE,
                    isSearchHistoryExpanded = false,
                    isLoadingMoreHistory = false,
                    hasMoreHistory = true,
                )
            }
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
            val isCurrentTabBlankOrHome =
                current.currentUrl.isEmpty() ||
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
                            activeOverlay = OverlayType.HOME,
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

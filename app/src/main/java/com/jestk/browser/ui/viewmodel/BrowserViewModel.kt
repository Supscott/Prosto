package com.jestk.browser.ui.viewmodel

import android.content.Intent
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jestk.browser.domain.model.DownloadItem
import com.jestk.browser.domain.model.FeedbackPromptState
import com.jestk.browser.domain.model.InsecureNavigationTarget
import com.jestk.browser.domain.model.OverlayType
import com.jestk.browser.domain.model.SearchEngine
import com.jestk.browser.domain.repository.DownloadRepository
import com.jestk.browser.domain.repository.FavoriteRepository
import com.jestk.browser.domain.repository.HistoryRepository
import com.jestk.browser.domain.repository.RecentTabRepository
import com.jestk.browser.domain.repository.SearchEngineRepository
import com.jestk.browser.domain.repository.SettingsRepository
import com.jestk.browser.domain.repository.TabFolderRepository
import com.jestk.browser.domain.repository.TabRepository
import com.jestk.browser.domain.repository.WorkspaceRepository
import com.jestk.browser.engine.WebViewPool
import com.jestk.browser.engine.download.HoriDownloadManager
import com.jestk.browser.engine.update.AppUpdateManager
import com.jestk.browser.ui.viewmodel.events.BackPressHandler
import com.jestk.browser.ui.viewmodel.events.LibraryEventHandler
import com.jestk.browser.ui.viewmodel.events.MediaEventHandler
import com.jestk.browser.ui.viewmodel.events.OverlayEventHandler
import com.jestk.browser.ui.viewmodel.events.PageEventHandler
import com.jestk.browser.ui.viewmodel.events.SafetyEventHandler
import com.jestk.browser.ui.viewmodel.events.SettingsEventHandler
import com.jestk.browser.ui.viewmodel.events.TabEventHandler
import com.jestk.browser.util.UrlSecurityUtils
import com.jestk.browser.util.YouTubeUrlHelper
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import java.util.concurrent.ConcurrentHashMap

private const val TAG = "BrowserViewModel"
private const val PRECONNECT_DEBOUNCE_MS = 300L

class BrowserViewModel(
    private val tabRepository: TabRepository,
    private val historyRepository: HistoryRepository,
    private val favoriteRepository: FavoriteRepository,
    val settingsRepository: SettingsRepository,
    private val recentTabRepository: RecentTabRepository? = null,
    private val downloadRepository: DownloadRepository? = null,
    private val downloadManager: HoriDownloadManager? = null,
    private val searchEngineRepository: SearchEngineRepository? = null,
    private val webViewPool: WebViewPool? = null,
    private val appUpdateManager: AppUpdateManager? = null,
    val tabFolderRepository: TabFolderRepository? = null,
    val workspaceRepository: WorkspaceRepository? = null,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val currentTimeProvider: () -> Long = { System.currentTimeMillis() },
) : ViewModel() {
    private val tabOperationMutex = Mutex()
    private val tabThemeColors = ConcurrentHashMap<String, Int>()
    private val preconnectPlanner = PreconnectPlanner(now = { android.os.SystemClock.elapsedRealtime() })
    private var preconnectJob: Job? = null

    private val _uiState = MutableStateFlow(BrowserUiState())
    val uiState: StateFlow<BrowserUiState> = _uiState.asStateFlow()

    // Updated on every page progress tick; kept out of BrowserUiState so a tick does not recompose
    // everything that reads the state. Only the active tab pill reads it.
    private val _loadingProgress = MutableStateFlow(0f)
    val loadingProgress: StateFlow<Float> = _loadingProgress.asStateFlow()

    // The fullscreen video View is an Android type and does not belong in BrowserUiState; the state
    // only carries hasFullscreenCustomView.
    private val _fullscreenCustomView = MutableStateFlow<android.view.View?>(null)
    val fullscreenCustomView: StateFlow<android.view.View?> = _fullscreenCustomView.asStateFlow()

    internal val updateCoordinator =
        UpdateCoordinator(
            appUpdateManager = appUpdateManager,
            settingsRepository = settingsRepository,
            getCurrentState = { _uiState.value },
            updateUiState = { transform -> _uiState.update(transform) },
            scope = viewModelScope,
            ioDispatcher = ioDispatcher,
        )

    var onLaunchIntent: (Intent) -> Unit
        get() = updateCoordinator.onLaunchIntent
        set(value) {
            updateCoordinator.onLaunchIntent = value
        }

    /**
     * Current vibration preference, read on demand so a toggle takes effect without restarting
     * the gesture that captured this view model.
     */
    val isVibrationEnabled: Boolean
        get() = _uiState.value.settings.isVibrationEnabled

    private val _navigationCommands = MutableSharedFlow<NavigationCommand>(extraBufferCapacity = 16)
    val navigationCommands: SharedFlow<NavigationCommand> = _navigationCommands.asSharedFlow()

    private val downloadCoordinator =
        DownloadCoordinator(
            downloadRepository = downloadRepository,
            downloadManager = downloadManager,
            updateUiState = { transform -> _uiState.update(transform) },
            scope = viewModelScope,
            ioDispatcher = ioDispatcher,
        )

    private val searchCoordinator =
        SearchCoordinator(
            historyRepository = historyRepository,
            searchEngineRepository = searchEngineRepository,
            settingsRepository = settingsRepository,
            getCurrentState = { _uiState.value },
            updateUiState = { transform -> _uiState.update(transform) },
            onSelectTab = { tabId -> tabCoordinator.activation.selectTab(tabId) },
            onOpenNewTab = { url -> onEvent(BrowserUiEvent.OpenNewTab(url)) },
            scope = viewModelScope,
            ioDispatcher = ioDispatcher,
        )

    internal val tabCoordinator =
        TabCoordinator(
            tabRepository = tabRepository,
            recentTabRepository = recentTabRepository,
            favoriteRepository = favoriteRepository,
            historyRepository = historyRepository,
            webViewPool = webViewPool,
            tabOperationMutex = tabOperationMutex,
            tabThemeColors = tabThemeColors,
            getCurrentState = { _uiState.value },
            updateUiState = { transform -> _uiState.update(transform) },
            emitNavigation = { cmd -> _navigationCommands.tryEmit(cmd) },
            getLoadingProgress = { _loadingProgress.value },
            setLoadingProgress = { _loadingProgress.value = it },
            scope = viewModelScope,
            ioDispatcher = ioDispatcher,
            tabFolderRepository = tabFolderRepository,
            workspaceRepository = workspaceRepository,
        )

    private val tabEvents =
        TabEventHandler(
            tabCoordinator = tabCoordinator,
            searchCoordinator = searchCoordinator,
            getCurrentState = { _uiState.value },
            updateUiState = { transform -> _uiState.update(transform) },
            scope = viewModelScope,
            dispatch = this::onEvent,
            loadUrl = this::loadUrl,
            shouldWarnForInsecureUrl = this::shouldWarnForInsecureUrl,
        )

    private val overlayEvents =
        OverlayEventHandler(
            tabCoordinator = tabCoordinator,
            searchCoordinator = searchCoordinator,
            getCurrentState = { _uiState.value },
            updateUiState = { transform -> _uiState.update(transform) },
            dispatch = this::onEvent,
            handleWebScroll = this::handleWebScroll,
        )

    private val pageEvents =
        PageEventHandler(
            tabCoordinator = tabCoordinator,
            searchCoordinator = searchCoordinator,
            updateUiState = { transform -> _uiState.update(transform) },
            emitNavigation = { cmd -> _navigationCommands.tryEmit(cmd) },
            loadUrl = this::loadUrl,
            submitSearch = this::submitSearch,
            schedulePreconnect = this::schedulePreconnect,
            handleGoBack = this::handleGoBack,
            getBypassCleanPlayerVideoId = { bypassCleanPlayerVideoId },
            setBypassCleanPlayerVideoId = { bypassCleanPlayerVideoId = it },
        )

    private val libraryEvents =
        LibraryEventHandler(
            tabCoordinator = tabCoordinator,
            searchCoordinator = searchCoordinator,
            settingsRepository = settingsRepository,
            recentTabRepository = recentTabRepository,
            tabRepository = tabRepository,
            getCurrentState = { _uiState.value },
            updateUiState = { transform -> _uiState.update(transform) },
            scope = viewModelScope,
            ioDispatcher = ioDispatcher,
        )

    private val settingsEvents =
        SettingsEventHandler(
            searchCoordinator = searchCoordinator,
            downloadCoordinator = downloadCoordinator,
            updateCoordinator = updateCoordinator,
            settingsRepository = settingsRepository,
            updateUiState = { transform -> _uiState.update(transform) },
            scope = viewModelScope,
            ioDispatcher = ioDispatcher,
            currentTimeProvider = currentTimeProvider,
            dismissDownloadSnackbar = this::dismissDownloadSnackbar,
            updateDefaultBrowserStatus = this::updateDefaultBrowserStatus,
        )

    private val safetyEvents =
        SafetyEventHandler(
            tabCoordinator = tabCoordinator,
            getCurrentState = { _uiState.value },
            updateUiState = { transform -> _uiState.update(transform) },
            loadUrl = this::loadUrl,
        )

    private val mediaEvents =
        MediaEventHandler(
            webViewPool = webViewPool,
            getCurrentState = { _uiState.value },
            updateUiState = { transform -> _uiState.update(transform) },
            emitNavigation = { cmd -> _navigationCommands.tryEmit(cmd) },
            dispatch = this::onEvent,
            getBypassCleanPlayerVideoId = { bypassCleanPlayerVideoId },
            setBypassCleanPlayerVideoId = { bypassCleanPlayerVideoId = it },
            setFullscreenCustomView = { _fullscreenCustomView.value = it },
        )

    private val backPress =
        BackPressHandler(
            tabRepository = tabRepository,
            tabCoordinator = tabCoordinator,
            searchCoordinator = searchCoordinator,
            getCurrentState = { _uiState.value },
            updateUiState = { transform -> _uiState.update(transform) },
            emitNavigation = { cmd -> _navigationCommands.tryEmit(cmd) },
            scope = viewModelScope,
            ioDispatcher = ioDispatcher,
            dispatch = this::onEvent,
        )

    init {
        webViewPool?.onDownloadStarted = { item -> onDownloadStarted(item) }
        webViewPool?.let { pool ->
            viewModelScope.launch {
                pool.loadedTabIds.collect { loaded ->
                    _uiState.update { it.copy(loadedTabIds = loaded) }
                }
            }
            viewModelScope.launch {
                pool.awaitingFirstPaintTabIds.collect { awaiting ->
                    _uiState.update { it.withAwaitingFirstPaint(awaiting) }
                }
            }
        }
        viewModelScope.launch(ioDispatcher) {
            restoreSession()
            observeDatabaseFlows()
        }
        updateCoordinator.onAppStart()
    }

    override fun onCleared() {
        super.onCleared()
        webViewPool?.onDownloadStarted = null
    }

    var bypassCleanPlayerVideoId: String? = null

    fun shouldInterceptForCleanPlayer(url: String?): Boolean {
        if (url.isNullOrBlank()) return false
        if (!_uiState.value.settings.isCleanYouTubePlayerEnabled) return false
        val videoId = YouTubeUrlHelper.extractVideoId(url) ?: return false
        if (videoId == bypassCleanPlayerVideoId) {
            // Video was explicitly opened on YouTube; do not intercept it during loading/SPA states
            return false
        }
        // A different video is being navigated to; clear bypass and intercept it
        bypassCleanPlayerVideoId = null
        return true
    }

    private fun calculateFeedbackCardVisible(settings: com.jestk.browser.domain.model.BrowserSettings): Boolean {
        return when (settings.feedbackPromptState) {
            FeedbackPromptState.DISMISSED -> false
            FeedbackPromptState.SNOOZED -> currentTimeProvider() >= settings.feedbackPromptSnoozedUntil
            FeedbackPromptState.VISIBLE -> true
        }
    }

    private suspend fun restoreSession() {
        Log.d(TAG, "Restoring session")
        workspaceRepository?.ensureDefaultWorkspaces()
        val initialSettings = settingsRepository.getCurrentSettings()
        _uiState.update { it.copy(isFeedbackCardVisible = calculateFeedbackCardVisible(initialSettings)) }
        val currentEngines =
            if (searchEngineRepository != null) {
                searchEngineRepository.ensureDefaultEnginesSeeded()
                val engines = searchEngineRepository.getAllSearchEngines()
                if (engines.isNotEmpty()) engines else SearchEngine.DEFAULT_ENGINES
            } else {
                SearchEngine.DEFAULT_ENGINES
            }
        tabCoordinator.sessionSync.restoreSession(initialSettings, currentEngines)
    }

    private fun observeDatabaseFlows() {
        tabCoordinator.sessionSync.observeDatabaseFlows()
        searchCoordinator.observeDatabaseFlows()
        downloadCoordinator.observeDatabaseFlows()
        viewModelScope.launch(ioDispatcher) {
            settingsRepository.getBlockedAdsCount().collect { count ->
                _uiState.update { it.copy(blockedAdsCount = count) }
            }
        }
        viewModelScope.launch(ioDispatcher) {
            settingsRepository.getBlockedTrackersCount().collect { count ->
                _uiState.update { it.copy(blockedTrackersCount = count) }
            }
        }
    }

    fun incrementBlockedAds(count: Long = 1) {
        viewModelScope.launch(ioDispatcher) {
            settingsRepository.incrementBlockedAds(count)
        }
    }

    fun incrementBlockedTrackers(count: Long = 1) {
        viewModelScope.launch(ioDispatcher) {
            settingsRepository.incrementBlockedTrackers(count)
        }
    }

    fun onDownloadStarted(item: DownloadItem) {
        downloadCoordinator.onDownloadStarted(item)
    }

    fun dismissDownloadSnackbar() {
        downloadCoordinator.dismissDownloadSnackbar()
    }

    fun updateDefaultBrowserStatus(isDefault: Boolean) {
        _uiState.update {
            it.copy(isDefaultBrowser = isDefault)
        }
    }

    fun isInsecureHostAllowed(urlOrHost: String): Boolean {
        val host = UrlSecurityUtils.extractHost(urlOrHost)
        return host.isNotEmpty() && _uiState.value.allowedInsecureHosts.contains(host)
    }

    fun shouldWarnForInsecureUrl(url: String): Boolean {
        return url.isNotEmpty() && UrlSecurityUtils.isHttpUrl(url) && !isInsecureHostAllowed(url)
    }

    fun onAppResume() {
        updateCoordinator.onAppResume()
        val currentSettings = _uiState.value.settings
        if (currentSettings.feedbackPromptState == FeedbackPromptState.SNOOZED) {
            val visible = calculateFeedbackCardVisible(currentSettings)
            if (visible != _uiState.value.isFeedbackCardVisible) {
                _uiState.update { it.copy(isFeedbackCardVisible = visible) }
            }
        }
    }

    fun onEvent(event: BrowserUiEvent) {
        when (event) {
            is TabUiEvent -> tabEvents.handle(event)
            is OverlayUiEvent -> overlayEvents.handle(event)
            is PageUiEvent -> pageEvents.handle(event)
            is LibraryUiEvent -> libraryEvents.handle(event)
            is SettingsUiEvent -> settingsEvents.handle(event)
            is SafetyUiEvent -> safetyEvents.handle(event)
            is MediaUiEvent -> mediaEvents.handle(event)
            BrowserUiEvent.HandleBackPress -> backPress.handle()
        }
    }

    private fun loadUrl(rawUrl: String) {
        val state = _uiState.value
        val (finalUrl, matchedShortcutEngine) =
            resolveUrlInput(rawUrl, state.searchEngines, state.settings.searchEngine)
        if (matchedShortcutEngine != null) {
            viewModelScope.launch(ioDispatcher) {
                settingsRepository.setSearchEngine(matchedShortcutEngine)
            }
        }

        if (shouldWarnForInsecureUrl(finalUrl)) {
            onEvent(
                BrowserUiEvent.ShowInsecureHttpWarning(
                    url = finalUrl,
                    tabId = _uiState.value.activeTabId,
                    target = InsecureNavigationTarget.ACTIVE_TAB,
                )
            )
            return
        }

        if (shouldInterceptForCleanPlayer(finalUrl)) {
            val videoId = YouTubeUrlHelper.extractVideoId(finalUrl)
            if (videoId != null) {
                val startSeconds = YouTubeUrlHelper.extractStartTimeSeconds(finalUrl)
                onEvent(BrowserUiEvent.OpenCleanPlayer(videoId, finalUrl, startSeconds))
                return
            }
        }

        tabCoordinator.pageEvents.navigateToUrl(finalUrl, matchedShortcutEngine)
    }

    /**
     * Warms the connection to what the user is typing (typed host or search engine) once typing
     * pauses, so the TLS handshake is done before Enter. See [PreconnectPlanner].
     */
    private fun schedulePreconnect(query: String) {
        val pool = webViewPool ?: return
        preconnectJob?.cancel()
        preconnectJob = viewModelScope.launch {
            delay(PRECONNECT_DEBOUNCE_MS)
            val state = _uiState.value
            val origin = preconnectPlanner.originToPreconnect(query) { state.settings.searchEngine.buildSearchUrl(it) }
                ?: return@launch
            val profileName = state.workspaces.find { it.id == state.activeWorkspaceId }?.profileName ?: state.activeWorkspaceId
            pool.preconnect(origin, profileName)
        }
    }

    private fun submitSearch(query: String) {
        loadUrl(query)
    }

    private fun handleWebScroll(
        deltaY: Float,
        scrollY: Int = 0,
        viewportHeight: Int = 0,
    ) {
        _uiState.update { it.afterWebScroll(deltaY, scrollY, viewportHeight) }
    }

    private fun handleGoBack() {
        _navigationCommands.tryEmit(NavigationCommand.GoBack)
    }

    fun switchWorkspace(workspaceId: String) {
        onEvent(BrowserUiEvent.SwitchWorkspace(workspaceId))
    }

    fun moveTabToWorkspace(tabId: String, targetWorkspaceId: String) {
        onEvent(BrowserUiEvent.MoveTabToWorkspace(tabId, targetWorkspaceId))
    }
}

package com.jestk.browser.di

import android.content.Context
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.jestk.browser.data.local.AppDatabase
import com.jestk.browser.data.repository.DownloadRepositoryImpl
import com.jestk.browser.data.repository.FavoriteRepositoryImpl
import com.jestk.browser.data.repository.HistoryRepositoryImpl
import com.jestk.browser.data.repository.RecentTabRepositoryImpl
import com.jestk.browser.data.repository.SearchEngineRepositoryImpl
import com.jestk.browser.data.repository.SettingsBlockedRequestStats
import com.jestk.browser.data.repository.SettingsRepositoryImpl
import com.jestk.browser.data.repository.TabRepositoryImpl
import com.jestk.browser.domain.repository.DownloadRepository
import com.jestk.browser.domain.repository.FavoriteRepository
import com.jestk.browser.domain.repository.HistoryRepository
import com.jestk.browser.domain.repository.RecentTabRepository
import com.jestk.browser.domain.repository.SearchEngineRepository
import com.jestk.browser.domain.repository.SettingsRepository
import com.jestk.browser.domain.repository.TabRepository
import com.jestk.browser.engine.AdBlockerEngine
import com.jestk.browser.engine.FaviconManager
import com.jestk.browser.engine.SnapshotEngine
import com.jestk.browser.engine.WebViewPool
import com.jestk.browser.engine.download.HoriDownloadManager
import com.jestk.browser.engine.update.AppUpdateManager
import com.jestk.browser.ui.viewmodel.BrowserViewModel

interface AppContainer {
    val database: AppDatabase
    val tabRepository: TabRepository
    val historyRepository: HistoryRepository
    val favoriteRepository: FavoriteRepository
    val settingsRepository: SettingsRepository
    val recentTabRepository: RecentTabRepository
    val downloadRepository: DownloadRepository
    val downloadManager: HoriDownloadManager
    val searchEngineRepository: SearchEngineRepository
    val webViewPool: WebViewPool
    val appUpdateManager: AppUpdateManager
    val viewModelFactory: ViewModelProvider.Factory

    /**
     * Releases the resources owned by the container: the pooled WebViews are destroyed and the
     * background scopes of the engine singletons are cancelled.
     *
     * The call is idempotent and safe: every engine recreates its scope lazily, so the app keeps
     * working if the container is used again after a shutdown.
     */
    fun shutdown()
}

class DefaultAppContainer(
    private val context: Context,
) : AppContainer {
    init {
        SnapshotEngine.init(context.applicationContext)
        FaviconManager.init(context.applicationContext)
    }

    override val database: AppDatabase by lazy {
        AppDatabase.getInstance(context)
    }

    override val downloadRepository: DownloadRepository by lazy {
        DownloadRepositoryImpl(database.downloadDao())
    }

    override val downloadManager: HoriDownloadManager by lazy {
        HoriDownloadManager.getInstance(context, downloadRepository)
    }

    private val webViewPoolDelegate =
        lazy {
            WebViewPool(context.applicationContext, downloadRepository, downloadManager)
        }

    override val webViewPool: WebViewPool by webViewPoolDelegate

    override val tabRepository: TabRepository by lazy {
        TabRepositoryImpl(database.tabDao())
    }

    override val historyRepository: HistoryRepository by lazy {
        HistoryRepositoryImpl(database.historyDao())
    }

    override val favoriteRepository: FavoriteRepository by lazy {
        FavoriteRepositoryImpl(database.favoriteDao())
    }

    override val settingsRepository: SettingsRepository by lazy {
        SettingsRepositoryImpl(database.settingDao()).also {
            AdBlockerEngine.setBlockedRequestStats(SettingsBlockedRequestStats(it))
        }
    }

    override val recentTabRepository: RecentTabRepository by lazy {
        RecentTabRepositoryImpl(database.recentTabDao())
    }

    override val searchEngineRepository: SearchEngineRepository by lazy {
        SearchEngineRepositoryImpl(database.searchEngineDao(), context = context.applicationContext)
    }

    override val appUpdateManager: AppUpdateManager by lazy {
        AppUpdateManager(context.applicationContext)
    }

    override val viewModelFactory: ViewModelProvider.Factory =
        viewModelFactory {
            initializer {
                BrowserViewModel(
                    tabRepository = tabRepository,
                    historyRepository = historyRepository,
                    favoriteRepository = favoriteRepository,
                    settingsRepository = settingsRepository,
                    recentTabRepository = recentTabRepository,
                    downloadRepository = downloadRepository,
                    downloadManager = downloadManager,
                    searchEngineRepository = searchEngineRepository,
                    webViewPool = webViewPool,
                    appUpdateManager = appUpdateManager,
                )
            }
        }

    override fun shutdown() {
        // The pool is created lazily: only tear it down when it was actually instantiated.
        if (webViewPoolDelegate.isInitialized()) {
            webViewPoolDelegate.value.shutdown()
        }
        SnapshotEngine.shutdown()
        FaviconManager.shutdown()
        AdBlockerEngine.shutdown()
    }
}

package com.jestk.browser.di

import android.content.Context
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.jestk.browser.data.local.AppDatabase
import com.jestk.browser.data.repository.FavoriteRepositoryImpl
import com.jestk.browser.data.repository.HistoryRepositoryImpl
import com.jestk.browser.data.repository.RecentTabRepositoryImpl
import com.jestk.browser.data.repository.SettingsRepositoryImpl
import com.jestk.browser.data.repository.TabRepositoryImpl
import com.jestk.browser.domain.repository.FavoriteRepository
import com.jestk.browser.domain.repository.HistoryRepository
import com.jestk.browser.domain.repository.RecentTabRepository
import com.jestk.browser.domain.repository.SettingsRepository
import com.jestk.browser.domain.repository.TabRepository
import com.jestk.browser.engine.WebViewPool
import com.jestk.browser.ui.viewmodel.BrowserViewModel

interface AppContainer {
    val database: AppDatabase
    val tabRepository: TabRepository
    val historyRepository: HistoryRepository
    val favoriteRepository: FavoriteRepository
    val settingsRepository: SettingsRepository
    val recentTabRepository: RecentTabRepository
    val webViewPool: WebViewPool
    val viewModelFactory: ViewModelProvider.Factory
}

class DefaultAppContainer(private val context: Context) : AppContainer {

    override val database: AppDatabase by lazy {
        AppDatabase.getInstance(context)
    }

    override val webViewPool: WebViewPool by lazy {
        WebViewPool(context.applicationContext)
    }

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
        SettingsRepositoryImpl(database.settingDao())
    }

    override val recentTabRepository: RecentTabRepository by lazy {
        RecentTabRepositoryImpl(database.recentTabDao())
    }

    override val viewModelFactory: ViewModelProvider.Factory = viewModelFactory {
        initializer {
            BrowserViewModel(
                tabRepository = tabRepository,
                historyRepository = historyRepository,
                favoriteRepository = favoriteRepository,
                settingsRepository = settingsRepository,
                recentTabRepository = recentTabRepository
            )
        }
    }
}

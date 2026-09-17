package com.jestk.browser.data.repository

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.jestk.browser.data.local.AppDatabase
import com.jestk.browser.domain.model.SearchEngine
import com.jestk.browser.domain.model.ThemeMode
import com.jestk.browser.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
class SettingsRepositoryTest {

    private lateinit var db: AppDatabase
    private lateinit var repository: SettingsRepository
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = AppDatabase.createInMemory(context)
        repository = SettingsRepositoryImpl(db.settingDao(), testDispatcher)
    }

    @After
    @Throws(IOException::class)
    fun teardown() {
        db.close()
    }

    @Test
    fun getSettings_returnsDefaultsInitially() = runTest(testDispatcher) {
        val settings = repository.getCurrentSettings()
        assertThat(settings.themeMode).isEqualTo(ThemeMode.SYSTEM)
        assertThat(settings.searchEngine).isEqualTo(SearchEngine.DUCKDUCKGO)
        assertThat(settings.isAdBlockerEnabled).isTrue()
    }

    @Test
    fun setThemeMode_persistsAndEmits() = runTest(testDispatcher) {
        repository.setThemeMode(ThemeMode.DARK)

        val settings = repository.getSettings().first()
        assertThat(settings.themeMode).isEqualTo(ThemeMode.DARK)
    }

    @Test
    fun setSearchEngine_persistsAndEmits() = runTest(testDispatcher) {
        repository.setSearchEngine(SearchEngine.GOOGLE)

        val settings = repository.getSettings().first()
        assertThat(settings.searchEngine).isEqualTo(SearchEngine.GOOGLE)
    }

    @Test
    fun setAdBlockerEnabled_persistsAndEmits() = runTest(testDispatcher) {
        repository.setAdBlockerEnabled(false)

        val settings = repository.getSettings().first()
        assertThat(settings.isAdBlockerEnabled).isFalse()
    }

    @Test
    fun setMenuExpandedOnWeb_persistsAndEmits() = runTest(testDispatcher) {
        assertThat(repository.getCurrentSettings().isMenuExpandedOnWeb).isFalse()

        repository.setMenuExpandedOnWeb(true)

        val settings = repository.getSettings().first()
        assertThat(settings.isMenuExpandedOnWeb).isTrue()
        assertThat(repository.getCurrentSettings().isMenuExpandedOnWeb).isTrue()
    }
}

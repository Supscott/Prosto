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
    fun getSettings_returnsDefaultsInitially() =
        runTest(testDispatcher) {
            val settings = repository.getCurrentSettings()
            assertThat(settings.themeMode).isEqualTo(ThemeMode.SYSTEM)
            assertThat(settings.searchEngine).isEqualTo(SearchEngine.DUCKDUCKGO)
            assertThat(settings.isAdBlockerEnabled).isTrue()
            assertThat(settings.isTrackingProtectionEnabled).isTrue()
        }

    @Test
    fun setThemeMode_persistsAndEmits() =
        runTest(testDispatcher) {
            repository.setThemeMode(ThemeMode.DARK)

            val settings = repository.getSettings().first()
            assertThat(settings.themeMode).isEqualTo(ThemeMode.DARK)
        }

    @Test
    fun setSearchEngine_persistsAndEmits() =
        runTest(testDispatcher) {
            repository.setSearchEngine(SearchEngine.GOOGLE)

            val settings = repository.getSettings().first()
            assertThat(settings.searchEngine).isEqualTo(SearchEngine.GOOGLE)
        }

    @Test
    fun setAdBlockerEnabled_persistsAndEmits() =
        runTest(testDispatcher) {
            repository.setAdBlockerEnabled(false)

            val settings = repository.getSettings().first()
            assertThat(settings.isAdBlockerEnabled).isFalse()
        }

    @Test
    fun getBlockedCounts_returnsZeroInitially() =
        runTest(testDispatcher) {
            val adsCount = repository.getBlockedAdsCount().first()
            val trackersCount = repository.getBlockedTrackersCount().first()

            assertThat(adsCount).isEqualTo(0L)
            assertThat(trackersCount).isEqualTo(0L)
        }

    @Test
    fun incrementBlockedAds_incrementsAndEmits() =
        runTest(testDispatcher) {
            repository.incrementBlockedAds(1)
            assertThat(repository.getBlockedAdsCount().first()).isEqualTo(1L)

            repository.incrementBlockedAds(4)
            assertThat(repository.getBlockedAdsCount().first()).isEqualTo(5L)
        }

    @Test
    fun incrementBlockedTrackers_incrementsAndEmits() =
        runTest(testDispatcher) {
            repository.incrementBlockedTrackers(1)
            assertThat(repository.getBlockedTrackersCount().first()).isEqualTo(1L)

            repository.incrementBlockedTrackers(7)
            assertThat(repository.getBlockedTrackersCount().first()).isEqualTo(8L)
        }

    @Test
    fun getSettings_hiddenSearchPageEngines_defaultsToEmptySet() =
        runTest(testDispatcher) {
            val settings = repository.getCurrentSettings()
            assertThat(settings.hiddenSearchPageEngineIds).isEmpty()
        }

    @Test
    fun setHiddenSearchPageEngineIds_persistsAndEmits() =
        runTest(testDispatcher) {
            val enginesToHide = setOf("bing", "yahoo", "ecosia")
            repository.setHiddenSearchPageEngineIds(enginesToHide)

            val settings = repository.getSettings().first()
            assertThat(settings.hiddenSearchPageEngineIds).containsExactly("bing", "yahoo", "ecosia")

            val current = repository.getCurrentSettings()
            assertThat(current.hiddenSearchPageEngineIds).containsExactly("bing", "yahoo", "ecosia")

            // Clearing hidden engines
            repository.setHiddenSearchPageEngineIds(emptySet())
            val cleared = repository.getCurrentSettings()
            assertThat(cleared.hiddenSearchPageEngineIds).isEmpty()
        }

    @Test
    fun setOpenInAppsEnabled_persistsAndEmits() =
        runTest(testDispatcher) {
            val initial = repository.getCurrentSettings()
            assertThat(initial.isOpenInAppsEnabled).isTrue()

            repository.setOpenInAppsEnabled(false)

            val updated = repository.getSettings().first()
            assertThat(updated.isOpenInAppsEnabled).isFalse()

            val current = repository.getCurrentSettings()
            assertThat(current.isOpenInAppsEnabled).isFalse()

            repository.setOpenInAppsEnabled(true)
            val reEnabled = repository.getCurrentSettings()
            assertThat(reEnabled.isOpenInAppsEnabled).isTrue()
        }

    @Test
    fun vibrationDefaultsToEnabled() =
        runTest(testDispatcher) {
            assertThat(repository.getCurrentSettings().isVibrationEnabled).isTrue()
        }

    @Test
    fun setVibrationEnabled_persistsAndEmits() =
        runTest(testDispatcher) {
            repository.setVibrationEnabled(false)

            assertThat(repository.getSettings().first().isVibrationEnabled).isFalse()
            assertThat(repository.getCurrentSettings().isVibrationEnabled).isFalse()

            repository.setVibrationEnabled(true)
            assertThat(repository.getCurrentSettings().isVibrationEnabled).isTrue()
        }

    @Test
    fun autoUpdateDefaultsToEnabled() =
        runTest(testDispatcher) {
            val settings = repository.getCurrentSettings()
            assertThat(settings.isAutoUpdateEnabled).isTrue()
            assertThat(settings.skippedUpdateVersion).isNull()
        }

    @Test
    fun setAutoUpdateEnabled_persistsAndEmits() =
        runTest(testDispatcher) {
            repository.setAutoUpdateEnabled(false)

            val updated = repository.getSettings().first()
            assertThat(updated.isAutoUpdateEnabled).isFalse()
            assertThat(repository.getCurrentSettings().isAutoUpdateEnabled).isFalse()

            repository.setAutoUpdateEnabled(true)
            assertThat(repository.getCurrentSettings().isAutoUpdateEnabled).isTrue()
        }

    @Test
    fun setSkippedUpdateVersion_persistsAndEmits() =
        runTest(testDispatcher) {
            repository.setSkippedUpdateVersion("1.2.0")

            assertThat(repository.getSettings().first().skippedUpdateVersion).isEqualTo("1.2.0")
            assertThat(repository.getCurrentSettings().skippedUpdateVersion).isEqualTo("1.2.0")

            repository.setSkippedUpdateVersion(null)
            assertThat(repository.getCurrentSettings().skippedUpdateVersion).isNull()
        }

    @Test
    fun setTrackingProtectionEnabled_persistsAndEmits() =
        runTest(testDispatcher) {
            repository.setTrackingProtectionEnabled(false)

            assertThat(repository.getSettings().first().isTrackingProtectionEnabled).isFalse()
            assertThat(repository.getCurrentSettings().isTrackingProtectionEnabled).isFalse()

            repository.setTrackingProtectionEnabled(true)
            assertThat(repository.getCurrentSettings().isTrackingProtectionEnabled).isTrue()
        }

    @Test
    fun grayUnloadedTabsDefaultsToEnabled() =
        runTest(testDispatcher) {
            assertThat(repository.getCurrentSettings().isGrayUnloadedTabsEnabled).isTrue()
            assertThat(repository.getSettings().first().isGrayUnloadedTabsEnabled).isTrue()
        }

    @Test
    fun setGrayUnloadedTabsEnabled_persistsAndEmits() =
        runTest(testDispatcher) {
            repository.setGrayUnloadedTabsEnabled(false)

            assertThat(repository.getSettings().first().isGrayUnloadedTabsEnabled).isFalse()
            assertThat(repository.getCurrentSettings().isGrayUnloadedTabsEnabled).isFalse()

            repository.setGrayUnloadedTabsEnabled(true)
            assertThat(repository.getCurrentSettings().isGrayUnloadedTabsEnabled).isTrue()
        }

    @Test
    fun feedbackPromptDefaultsToVisible() =
        runTest(testDispatcher) {
            val settings = repository.getCurrentSettings()
            assertThat(settings.feedbackPromptState).isEqualTo(com.jestk.browser.domain.model.FeedbackPromptState.VISIBLE)
            assertThat(settings.feedbackPromptSnoozedUntil).isEqualTo(0L)
        }

    @Test
    fun setFeedbackPromptState_persistsAndEmits() =
        runTest(testDispatcher) {
            repository.setFeedbackPromptState(com.jestk.browser.domain.model.FeedbackPromptState.DISMISSED)

            assertThat(repository.getSettings().first().feedbackPromptState)
                .isEqualTo(com.jestk.browser.domain.model.FeedbackPromptState.DISMISSED)
            assertThat(repository.getCurrentSettings().feedbackPromptState)
                .isEqualTo(com.jestk.browser.domain.model.FeedbackPromptState.DISMISSED)
        }

    @Test
    fun snoozeFeedbackPrompt_persistsStateAndTimestamp() =
        runTest(testDispatcher) {
            val targetTimestamp = 123456789L
            repository.snoozeFeedbackPrompt(targetTimestamp)

            val current = repository.getCurrentSettings()
            assertThat(current.feedbackPromptState).isEqualTo(com.jestk.browser.domain.model.FeedbackPromptState.SNOOZED)
            assertThat(current.feedbackPromptSnoozedUntil).isEqualTo(targetTimestamp)
        }
}

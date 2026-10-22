package com.jestk.browser.util

import android.app.Application
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ResolveInfo
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContract
import androidx.core.app.ActivityOptionsCompat
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.jestk.browser.data.local.AppDatabase
import com.jestk.browser.data.repository.FavoriteRepositoryImpl
import com.jestk.browser.data.repository.HistoryRepositoryImpl
import com.jestk.browser.data.repository.SettingsRepositoryImpl
import com.jestk.browser.data.repository.TabRepositoryImpl
import com.jestk.browser.ui.viewmodel.BrowserUiEvent
import com.jestk.browser.ui.viewmodel.BrowserUiState
import com.jestk.browser.ui.viewmodel.BrowserViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class DefaultBrowserHelperTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var db: AppDatabase
    private lateinit var viewModel: BrowserViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = AppDatabase.createInMemory(context)
        val tabRepo = TabRepositoryImpl(db.tabDao(), testDispatcher)
        val historyRepo = HistoryRepositoryImpl(db.historyDao(), testDispatcher)
        val favoriteRepo = FavoriteRepositoryImpl(db.favoriteDao(), testDispatcher)
        val settingsRepo = SettingsRepositoryImpl(db.settingDao(), testDispatcher)
        viewModel = BrowserViewModel(
            tabRepository = tabRepo,
            historyRepository = historyRepo,
            favoriteRepository = favoriteRepo,
            settingsRepository = settingsRepo,
            ioDispatcher = testDispatcher
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        db.close()
    }

    @Test
    @Config(sdk = [Build.VERSION_CODES.Q])
    fun isDefaultBrowser_api29_whenRoleHeld_returnsTrue() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val roleManager = context.getSystemService(RoleManager::class.java)
        val shadowRoleManager = shadowOf(roleManager)
        shadowRoleManager.addHeldRole(RoleManager.ROLE_BROWSER)

        val result = DefaultBrowserHelper.isDefaultBrowser(context)
        assertThat(result).isTrue()
    }

    @Test
    @Config(sdk = [Build.VERSION_CODES.Q])
    fun isDefaultBrowser_api29_whenRoleNotHeld_returnsFalse() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val roleManager = context.getSystemService(RoleManager::class.java)
        // No role held

        val result = DefaultBrowserHelper.isDefaultBrowser(context)
        assertThat(result).isFalse()
    }

    @Test
    @Config(sdk = [Build.VERSION_CODES.Q])
    fun createSetDefaultBrowserIntent_api29_returnsRequestRoleIntent() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val roleManager = context.getSystemService(RoleManager::class.java)
        val expected = roleManager.createRequestRoleIntent(RoleManager.ROLE_BROWSER)

        val intent = DefaultBrowserHelper.createSetDefaultBrowserIntent(context)
        assertThat(intent).isNotNull()
        assertThat(intent.action).isEqualTo(expected.action)
        assertThat(intent.component).isEqualTo(expected.component)
    }

    @Test
    @Config(sdk = [Build.VERSION_CODES.P])
    fun isDefaultBrowser_preQ_whenDefaultActivityMatches_returnsTrue() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val packageManager = context.packageManager
        val shadowPackageManager = shadowOf(packageManager)

        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com"))
        val resolveInfo = ResolveInfo().apply {
            activityInfo = ActivityInfo().apply {
                packageName = context.packageName
                name = "com.jestk.browser.ui.MainActivity"
            }
        }
        shadowPackageManager.addResolveInfoForIntent(intent, resolveInfo)

        val result = DefaultBrowserHelper.isDefaultBrowser(context)
        assertThat(result).isTrue()
    }

    @Test
    @Config(sdk = [Build.VERSION_CODES.P])
    fun isDefaultBrowser_preQ_whenDefaultActivityDiffers_returnsFalse() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val packageManager = context.packageManager
        val shadowPackageManager = shadowOf(packageManager)

        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com"))
        val resolveInfo = ResolveInfo().apply {
            activityInfo = ActivityInfo().apply {
                packageName = "com.android.chrome"
                name = "com.google.android.apps.chrome.Main"
            }
        }
        shadowPackageManager.addResolveInfoForIntent(intent, resolveInfo)

        val result = DefaultBrowserHelper.isDefaultBrowser(context)
        assertThat(result).isFalse()
    }

    @Test
    @Config(sdk = [Build.VERSION_CODES.P])
    fun isDefaultBrowser_preQ_whenNoActivityResolved_returnsFalse() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val shadowPackageManager = shadowOf(context.packageManager)
        shadowPackageManager.removeActivity(android.content.ComponentName(context.packageName, "com.jestk.browser.ui.MainActivity"))

        val result = DefaultBrowserHelper.isDefaultBrowser(context)
        assertThat(result).isFalse()
    }

    @Test
    @Config(sdk = [Build.VERSION_CODES.P])
    fun createSetDefaultBrowserIntent_preQ_returnsManageDefaultAppsIntent() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        val intent = DefaultBrowserHelper.createSetDefaultBrowserIntent(context)
        assertThat(intent).isNotNull()
        assertThat(intent.action).isEqualTo(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)
    }

    @Test
    fun browserUiState_defaultIsDefaultBrowser_isTrue() {
        val state = BrowserUiState()
        assertThat(state.isDefaultBrowser).isTrue()
    }

    @Test
    fun browserViewModel_updateDefaultBrowserStatus_updatesState() = runTest(testDispatcher) {
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.isDefaultBrowser).isTrue()

        viewModel.updateDefaultBrowserStatus(false)
        assertThat(viewModel.uiState.value.isDefaultBrowser).isFalse()

        viewModel.updateDefaultBrowserStatus(true)
        assertThat(viewModel.uiState.value.isDefaultBrowser).isTrue()
    }

    @Test
    fun browserViewModel_onEventUpdateDefaultBrowser_updatesState() = runTest(testDispatcher) {
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.isDefaultBrowser).isTrue()

        viewModel.onEvent(BrowserUiEvent.UpdateDefaultBrowserStatus(false))
        assertThat(viewModel.uiState.value.isDefaultBrowser).isFalse()

        viewModel.onEvent(BrowserUiEvent.UpdateDefaultBrowserStatus(true))
        assertThat(viewModel.uiState.value.isDefaultBrowser).isTrue()
    }

    @Test
    @Config(sdk = [Build.VERSION_CODES.Q])
    fun defaultBrowserStatus_api29_followsTheHeldRole() = runTest(testDispatcher) {
        advanceUntilIdle()
        val context = ApplicationProvider.getApplicationContext<Context>()
        val roleManager = context.getSystemService(RoleManager::class.java)
        val shadowRoleManager = shadowOf(roleManager)

        // Case 1: Role not held
        assertThat(DefaultBrowserHelper.isDefaultBrowser(context)).isFalse()
        viewModel.updateDefaultBrowserStatus(DefaultBrowserHelper.isDefaultBrowser(context))
        assertThat(viewModel.uiState.value.isDefaultBrowser).isFalse()

        // Case 2: Role held
        shadowRoleManager.addHeldRole(RoleManager.ROLE_BROWSER)
        assertThat(DefaultBrowserHelper.isDefaultBrowser(context)).isTrue()
        viewModel.updateDefaultBrowserStatus(DefaultBrowserHelper.isDefaultBrowser(context))
        assertThat(viewModel.uiState.value.isDefaultBrowser).isTrue()
    }

    @Test
    @Config(sdk = [Build.VERSION_CODES.Q])
    fun browserViewModel_onEventUpdateDefaultBrowserStatus_updatesState() = runTest(testDispatcher) {
        advanceUntilIdle()
        val context = ApplicationProvider.getApplicationContext<Context>()
        val roleManager = context.getSystemService(RoleManager::class.java)
        val shadowRoleManager = shadowOf(roleManager)

        viewModel.onEvent(
            BrowserUiEvent.UpdateDefaultBrowserStatus(DefaultBrowserHelper.isDefaultBrowser(context))
        )
        assertThat(viewModel.uiState.value.isDefaultBrowser).isFalse()

        shadowRoleManager.addHeldRole(RoleManager.ROLE_BROWSER)
        viewModel.onEvent(
            BrowserUiEvent.UpdateDefaultBrowserStatus(DefaultBrowserHelper.isDefaultBrowser(context))
        )
        assertThat(viewModel.uiState.value.isDefaultBrowser).isTrue()
    }

    @Test
    @Config(sdk = [Build.VERSION_CODES.Q])
    fun openDefaultBrowserSettings_api29_withLauncher_launchesRoleIntent() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val launcher = FakeActivityResultLauncher()

        DefaultBrowserHelper.openDefaultBrowserSettings(context, launcher)

        assertThat(launcher.launchedIntent).isNotNull()
        val roleManager = context.getSystemService(RoleManager::class.java)
        val expected = roleManager.createRequestRoleIntent(RoleManager.ROLE_BROWSER)
        assertThat(launcher.launchedIntent?.action).isEqualTo(expected.action)
    }

    @Test
    @Config(sdk = [Build.VERSION_CODES.P])
    fun openDefaultBrowserSettings_api28_withLauncher_launchesManageDefaultApps() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val launcher = FakeActivityResultLauncher()

        DefaultBrowserHelper.openDefaultBrowserSettings(context, launcher)

        assertThat(launcher.launchedIntent).isNotNull()
        assertThat(launcher.launchedIntent?.action).isEqualTo(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)
    }

    @Test
    @Config(sdk = [Build.VERSION_CODES.Q])
    fun openDefaultBrowserSettings_withoutLauncher_startsManageDefaultAppsActivity() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        DefaultBrowserHelper.openDefaultBrowserSettings(context, launcher = null)

        val nextStartedIntent = shadowOf(ApplicationProvider.getApplicationContext<Application>()).nextStartedActivity
        assertThat(nextStartedIntent).isNotNull()
        assertThat(nextStartedIntent.action).isEqualTo(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)
    }
}

private class FakeActivityResultLauncher : ActivityResultLauncher<Intent>() {
    var launchedIntent: Intent? = null

    override fun launch(input: Intent, options: ActivityOptionsCompat?) {
        launchedIntent = input
    }

    override fun unregister() {}

    override val contract: ActivityResultContract<Intent, *>
        get() = throw NotImplementedError()
}

package com.jestk.browser.ui

import android.app.PictureInPictureParams
import android.app.UiModeManager
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Rational
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.lifecycle.ViewModelProvider
import com.jestk.browser.engine.adblock.DebugFilterListIntent
import androidx.lifecycle.lifecycleScope
import com.jestk.browser.JestkApplication
import com.jestk.browser.engine.media.BackgroundPlaybackController
import com.jestk.browser.domain.model.ThemeMode
import com.jestk.browser.engine.AdBlockerEngine
import com.jestk.browser.ui.viewmodel.BackgroundStamp
import com.jestk.browser.ui.viewmodel.BrowserUiEvent
import com.jestk.browser.ui.viewmodel.BrowserViewModel
import com.jestk.browser.ui.views.FullscreenViewHost
import com.jestk.browser.ui.views.FullscreenViewHostOwner
import com.jestk.browser.util.BackgroundStampStore
import com.jestk.browser.util.DefaultBrowserHelper
import com.jestk.browser.util.LocalVibrationFeedback
import com.jestk.browser.util.VibratorVibrationFeedback
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

class MainActivity : ComponentActivity(), FileChooserHandler, FullscreenViewHostOwner {
    private lateinit var viewModel: BrowserViewModel
    private lateinit var backgroundPlayback: BackgroundPlaybackController
    private val backgroundStamps by lazy { BackgroundStampStore(this) }

    /** A link arrived since the last stop; that return is not a plain return to the app. */
    private var linkOpenedSinceStop = false

    private val pipVideoFill = PipVideoFill()

    /** Whether a video was playing when the user left for PiP, so the PiP window resumes it. */
    private var pipResumeVideo = false
    override val fullscreenViewHost: FullscreenViewHost by lazy { FullscreenViewHost(this) }
    private var filePathCallback: ValueCallback<Array<Uri>>? = null

    private val fileChooserLauncher: ActivityResultLauncher<Intent> =
        registerForActivityResult(
            ActivityResultContracts.StartActivityForResult(),
        ) { result ->
            val uris: Array<Uri>? =
                if (result.resultCode == RESULT_OK && result.data != null) {
                    val data = result.data!!
                    val clipData = data.clipData
                    when {
                        clipData != null && clipData.itemCount > 0 -> {
                            val list = ArrayList<Uri>()
                            for (i in 0 until clipData.itemCount) {
                                clipData.getItemAt(i)?.uri?.let { list.add(it) }
                            }
                            if (list.isNotEmpty()) {
                                list.toTypedArray()
                            } else {
                                WebChromeClient.FileChooserParams.parseResult(
                                    result.resultCode,
                                    data,
                                )
                            }
                        }

                        data.data != null -> {
                            arrayOf(data.data!!)
                        }

                        else -> {
                            WebChromeClient.FileChooserParams.parseResult(result.resultCode, data)
                        }
                    }
                } else {
                    null
                }
            filePathCallback?.onReceiveValue(uris)
            filePathCallback = null
        }

    private val notificationPermissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestPermission(),
        ) { _ -> }

    private fun checkNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) !=
                android.content.pm.PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    override fun openFileChooser(
        callback: ValueCallback<Array<Uri>>?,
        params: WebChromeClient.FileChooserParams?,
    ): Boolean {
        filePathCallback?.onReceiveValue(null)
        filePathCallback = callback

        if (callback == null) return false

        val intent =
            try {
                params?.createIntent() ?: Intent(Intent.ACTION_GET_CONTENT).apply {
                    type = "*/*"
                    addCategory(Intent.CATEGORY_OPENABLE)
                }
            } catch (_: Exception) {
                Intent(Intent.ACTION_GET_CONTENT).apply {
                    type = "*/*"
                    addCategory(Intent.CATEGORY_OPENABLE)
                }
            }

        return try {
            fileChooserLauncher.launch(intent)
            true
        } catch (_: Exception) {
            filePathCallback?.onReceiveValue(null)
            filePathCallback = null
            false
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle =
                SystemBarStyle.auto(
                    android.graphics.Color.TRANSPARENT,
                    android.graphics.Color.TRANSPARENT,
                ),
            navigationBarStyle =
                SystemBarStyle.auto(
                    android.graphics.Color.TRANSPARENT,
                    android.graphics.Color.TRANSPARENT,
                ),
        )
        super.onCreate(savedInstanceState)

        val container = (application as JestkApplication).container

        // Initialize Ad & Tracker blocker ruleset
        AdBlockerEngine.initialize(applicationContext)

        viewModel = ViewModelProvider(this, container.viewModelFactory)[BrowserViewModel::class.java]
        backgroundPlayback = BackgroundPlaybackController(this, container.webViewPool)
        viewModel.updateDefaultBrowserStatus(DefaultBrowserHelper.isDefaultBrowser(this))
        checkNotificationPermission()

        // A recreated Activity (theme change, process restore) or a relaunch from Recents gets
        // the original intent again; its tab is already open.
        val isRedelivered = savedInstanceState != null ||
            intent.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY != 0
        if (!isRedelivered) handleIntent(intent)

        viewModel.onLaunchIntent = { intent -> startActivity(intent) }
        keepWindowSecureDuringIncognito(viewModel.uiState)

        lifecycleScope.launch {
            container.settingsRepository.getSettings()
                .map { it.themeMode }
                .distinctUntilChanged()
                .collect { applyAppNightMode(it) }
        }

        lifecycleScope.launch {
            container.settingsRepository.getSettings()
                .map { it.isBackgroundPlaybackEnabled }
                .distinctUntilChanged()
                .collect { enabled -> if (!enabled) backgroundPlayback.stop() }
        }

        setContent {
            val vibration =
                remember {
                    VibratorVibrationFeedback(applicationContext) {
                        viewModel.isVibrationEnabled
                    }
                }
            CompositionLocalProvider(LocalVibrationFeedback provides vibration) {
                JestkApp(
                    viewModel = viewModel,
                    webViewPool = container.webViewPool,
                )
            }
        }
    }

    /**
     * Makes the Activity theme (and with it the WebView's `prefers-color-scheme` and algorithmic
     * darkening) follow the in-app theme setting. The system persists the value per app, so the
     * Activity is recreated only when the setting actually changes.
     * Below Android 12 the WebView follows the system theme.
     */
    private fun applyAppNightMode(mode: ThemeMode) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        val uiModeManager = getSystemService(UiModeManager::class.java) ?: return
        uiModeManager.setApplicationNightMode(
            when (mode) {
                ThemeMode.DARK -> UiModeManager.MODE_NIGHT_YES
                ThemeMode.LIGHT -> UiModeManager.MODE_NIGHT_NO
                ThemeMode.SYSTEM -> UiModeManager.MODE_NIGHT_AUTO
            },
        )
    }

    override fun onStart() {
        super.onStart()
        viewModel.onAppResume()
        viewModel.onReturnedToForeground(
            stamp = backgroundStamps.take(),
            returnedWithLink = linkOpenedSinceStop,
            mediaActiveNow = backgroundPlayback.isSessionActive,
        )
        linkOpenedSinceStop = false
    }

    override fun onPause() {
        super.onPause()
        val state = viewModel.uiState.value
        backgroundPlayback.onActivityPaused(
            enabled = state.settings.isBackgroundPlaybackEnabled,
            inPictureInPicture = isInPictureInPictureMode,
            changingConfigurations = isChangingConfigurations,
            tab = state.tabs.find { it.id == state.activeTabId },
        )
    }

    override fun onResume() {
        super.onResume()
        backgroundPlayback.onActivityResumed()
        viewModel.updateDefaultBrowserStatus(DefaultBrowserHelper.isDefaultBrowser(this))
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (DebugFilterListIntent.matches(intent)) {
            lifecycleScope.launch { DebugFilterListIntent.handle(applicationContext, intent!!) }
            return
        }
        val data = intent?.dataString
        if (!data.isNullOrEmpty()) {
            linkOpenedSinceStop = true
            // Our own links (Settings "About", feedback) reach us here too; they are not external.
            val openedByExternalApp = referrer?.host != packageName
            viewModel.onEvent(BrowserUiEvent.OpenNewTab(data, openedByExternalApp = openedByExternalApp))
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        // Enter Android System PiP if video is active when leaving app
        val state = viewModel.uiState.value
        if (state.isVideoPlaying || state.isFullscreenCustomView) {
            pipResumeVideo = state.isVideoPlaying
            enterPipMode()
        }
    }

    private fun enterPipMode() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                val params =
                    PictureInPictureParams
                        .Builder()
                        .setAspectRatio(Rational(16, 9))
                        .build()
                enterPictureInPictureMode(params)
            } catch (e: Exception) {
                try {
                    @Suppress("DEPRECATION")
                    enterPictureInPictureMode()
                } catch (_: Exception) {
                }
            }
        }
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration,
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        viewModel.onEvent(BrowserUiEvent.SetBottomBarVisibility(!isInPictureInPictureMode))
        val activeWebView = viewModel.uiState.value.activeTabId?.let {
            (application as JestkApplication).container.webViewPool.getWebView(it)
        }
        if (!isInPictureInPictureMode) {
            pipVideoFill.restore(activeWebView)
        } else if (activeWebView != null) {
            // Chromium leaves HTML5 fullscreen when the window shrinks, and players pause on that
            // exit: wait for it (and the page's own handlers), then keep the video in the window.
            lifecycleScope.launch {
                withTimeoutOrNull(1_500) { viewModel.uiState.first { !it.isFullscreenCustomView } }
                delay(300)
                if (isInPictureInPictureMode) applyPipVideoFill(activeWebView)
            }
        }
    }

    private fun applyPipVideoFill(activeWebView: android.webkit.WebView) {
        pipVideoFill.apply(activeWebView, resume = pipResumeVideo) { width, height ->
            val ratio = (width.toFloat() / height).coerceIn(1f / 2.39f, 2.39f)
            runCatching {
                setPictureInPictureParams(
                    PictureInPictureParams.Builder().setAspectRatio(Rational((ratio * 1000).toInt(), 1000)).build(),
                )
            }
        }
    }

    override fun onStop() {
        super.onStop()
        backgroundPlayback.onActivityStopped()
        linkOpenedSinceStop = false
        val state = viewModel.uiState.value
        backgroundStamps.save(
            BackgroundStamp(
                atMs = System.currentTimeMillis(),
                mediaActive = state.isVideoPlaying || state.isFullscreenCustomView || isInPictureInPictureMode,
            ),
        )
        // The process may be killed while in the background; keep every tab's history.
        (application as JestkApplication).container.webViewPool.saveAllStates()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isFinishing) backgroundPlayback.stop()
        filePathCallback?.onReceiveValue(null)
        filePathCallback = null
    }
}

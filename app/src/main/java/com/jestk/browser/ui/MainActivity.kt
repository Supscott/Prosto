package com.jestk.browser.ui

import android.app.PictureInPictureParams
import android.content.Intent
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.ViewModelProvider
import com.jestk.browser.JestkApplication
import com.jestk.browser.engine.AdBlockerEngine
import com.jestk.browser.ui.viewmodel.BrowserUiEvent
import com.jestk.browser.ui.viewmodel.BrowserViewModel

class MainActivity : ComponentActivity() {

    private lateinit var viewModel: BrowserViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        val container = (application as JestkApplication).container

        // Initialize Ad & Tracker blocker ruleset
        AdBlockerEngine.initialize(applicationContext)

        viewModel = ViewModelProvider(this, container.viewModelFactory)[BrowserViewModel::class.java]

        handleIntent(intent)

        setContent {
            JestkApp(
                viewModel = viewModel,
                webViewPool = container.webViewPool
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        val data = intent?.dataString
        if (!data.isNullOrEmpty()) {
            viewModel.onEvent(BrowserUiEvent.OpenNewTab(data))
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        // Enter Android System PiP if video is active when leaving app
        if (viewModel.uiState.value.isVideoPlaying || viewModel.uiState.value.isFullscreenCustomView) {
            enterPipMode()
        }
    }

    private fun enterPipMode() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                val params = PictureInPictureParams.Builder()
                    .setAspectRatio(Rational(16, 9))
                    .build()
                enterPictureInPictureMode(params)
            } catch (e: Exception) {
                try {
                    @Suppress("DEPRECATION")
                    enterPictureInPictureMode()
                } catch (_: Exception) {}
            }
        }
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        if (isInPictureInPictureMode) {
            viewModel.onEvent(BrowserUiEvent.SetBottomBarVisibility(false))
        } else {
            viewModel.onEvent(BrowserUiEvent.SetBottomBarVisibility(true))
        }
    }
}

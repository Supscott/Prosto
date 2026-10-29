package com.jestk.browser.ui

import android.app.PictureInPictureParams
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
import com.jestk.browser.JestkApplication
import com.jestk.browser.engine.AdBlockerEngine
import com.jestk.browser.ui.viewmodel.BrowserUiEvent
import com.jestk.browser.ui.viewmodel.BrowserViewModel
import com.jestk.browser.util.DefaultBrowserHelper
import com.jestk.browser.util.LocalVibrationFeedback
import com.jestk.browser.util.VibratorVibrationFeedback

class MainActivity : ComponentActivity(), FileChooserHandler {
    private lateinit var viewModel: BrowserViewModel
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
        viewModel.updateDefaultBrowserStatus(DefaultBrowserHelper.isDefaultBrowser(this))
        checkNotificationPermission()

        handleIntent(intent)

        viewModel.onLaunchIntent = { intent -> startActivity(intent) }

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

    override fun onStart() {
        super.onStart()
        viewModel.onAppResume()
    }

    override fun onResume() {
        super.onResume()
        viewModel.updateDefaultBrowserStatus(DefaultBrowserHelper.isDefaultBrowser(this))
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
        if (isInPictureInPictureMode) {
            viewModel.onEvent(BrowserUiEvent.SetBottomBarVisibility(false))
        } else {
            viewModel.onEvent(BrowserUiEvent.SetBottomBarVisibility(true))
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        filePathCallback?.onReceiveValue(null)
        filePathCallback = null
    }
}

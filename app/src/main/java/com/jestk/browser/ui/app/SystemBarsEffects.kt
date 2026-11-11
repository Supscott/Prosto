package com.jestk.browser.ui.app

import android.content.pm.ActivityInfo
import android.os.Build
import android.view.WindowManager
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.jestk.browser.ui.theme.DynamicBarColors
import com.jestk.browser.ui.viewmodel.BrowserUiState
import com.jestk.browser.util.findActivity

internal data class SystemBarColors(val statusBar: Color, val bottomBar: Color)

@Composable
internal fun rememberSystemBarsEffects(
    uiState: BrowserUiState,
    isDark: Boolean,
): SystemBarColors {
    val context = LocalContext.current
    val showHomeScreen = uiState.isHomeContentVisible
    val currentTab = uiState.tabs.find { it.id == uiState.activeTabId }
    val siteTopColor = uiState.activeTopThemeColor ?: currentTab?.topThemeColor
    val siteBottomColor = uiState.activeBottomThemeColor ?: currentTab?.bottomThemeColor
    val isHomeOrNeutral = showHomeScreen || uiState.isSettingsVisible || uiState.isDownloadsVisible

    val resolvedColors = DynamicBarColors.resolve(
        isDark = isDark,
        isHomeOrNeutralScreen = isHomeOrNeutral,
        topThemeColor = siteTopColor,
        bottomThemeColor = siteBottomColor,
    )

    val animatedStatusBarBg by animateColorAsState(
        targetValue = resolvedColors.statusBarColor,
        animationSpec = tween(durationMillis = 350),
        label = "JestkAppStatusBarBgColor",
    )
    val animatedBottomBarBg by animateColorAsState(
        targetValue = resolvedColors.underDockColor,
        animationSpec = tween(durationMillis = 350),
        label = "JestkAppBottomBarBgColor",
    )

    val view = LocalView.current
    val isLightStatus = animatedStatusBarBg.luminance() > 0.5f
    val isLightNav = animatedBottomBarBg.luminance() > 0.5f
    if (!view.isInEditMode) {
        val isFullscreen = uiState.isFullscreenCustomView
        SideEffect {
            val activity = view.context.findActivity()
            val window = activity?.window
            if (window != null) {
                val insetsController = WindowCompat.getInsetsController(window, window.decorView)
                insetsController.isAppearanceLightStatusBars = isLightStatus
                insetsController.isAppearanceLightNavigationBars = isLightNav

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    window.isNavigationBarContrastEnforced = false
                }
                @Suppress("DEPRECATION")
                window.statusBarColor = animatedStatusBarBg.toArgb()
                @Suppress("DEPRECATION")
                window.navigationBarColor = animatedBottomBarBg.toArgb()
            }
        }

        LaunchedEffect(isFullscreen) {
            val activity = view.context.findActivity() ?: return@LaunchedEffect
            val window = activity.window ?: return@LaunchedEffect
            val insetsController = WindowCompat.getInsetsController(window, window.decorView)
            if (isFullscreen) {
                insetsController.hide(WindowInsetsCompat.Type.systemBars())
                insetsController.systemBarsBehavior =
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            } else {
                insetsController.show(WindowInsetsCompat.Type.systemBars())
                insetsController.systemBarsBehavior =
                    WindowInsetsControllerCompat.BEHAVIOR_DEFAULT
                window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        }
    }

    val isFullscreenVideo = uiState.isFullscreenCustomView
    val requestedOrientation = uiState.fullscreenRequestedOrientation
    LaunchedEffect(isFullscreenVideo, requestedOrientation) {
        val activity = context.findActivity() ?: return@LaunchedEffect
        if (isFullscreenVideo) {
            if (requestedOrientation != ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED) {
                activity.requestedOrientation = requestedOrientation
            }
        } else {
            if (activity.requestedOrientation != ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED) {
                activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            val activity = context.findActivity()
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            activity?.window?.let { window ->
                val insetsController = WindowCompat.getInsetsController(window, window.decorView)
                insetsController.show(WindowInsetsCompat.Type.systemBars())
                insetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_DEFAULT
                window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        }
    }

    return SystemBarColors(animatedStatusBarBg, animatedBottomBarBg)
}

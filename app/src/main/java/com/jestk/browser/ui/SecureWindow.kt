package com.jestk.browser.ui

import android.content.pm.ApplicationInfo
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.jestk.browser.ui.viewmodel.BrowserUiState
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Sets FLAG_SECURE on the window (no screenshots, a blank app preview in Recents) while the
 * active tab is incognito, and clears it otherwise. A debuggable build can turn it off over adb
 * ([DebugSecureWindowSwitch]); a release build cannot.
 */
internal fun ComponentActivity.keepWindowSecureDuringIncognito(uiState: StateFlow<BrowserUiState>) {
    val debuggable = applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
    lifecycleScope.launch {
        combine(
            uiState.map { it.activeTab?.isIncognito == true },
            DebugSecureWindowSwitch.disabled(applicationContext),
        ) { incognito, debugDisabled -> shouldBeSecure(incognito, debugDisabled, debuggable) }
            .distinctUntilChanged()
            .collect { secure ->
                if (secure) {
                    window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                } else {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                }
            }
    }
}

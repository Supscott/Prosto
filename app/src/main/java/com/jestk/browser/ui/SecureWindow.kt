package com.jestk.browser.ui

import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.jestk.browser.ui.viewmodel.BrowserUiState
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Sets FLAG_SECURE on the window (no screenshots, a blank app preview in Recents) while the
 * active tab is incognito, and clears it otherwise.
 */
internal fun ComponentActivity.keepWindowSecureDuringIncognito(uiState: StateFlow<BrowserUiState>) {
    lifecycleScope.launch {
        uiState
            .map { it.activeTab?.isIncognito == true }
            .distinctUntilChanged()
            .collect { incognito ->
                if (incognito) {
                    window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                } else {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                }
            }
    }
}

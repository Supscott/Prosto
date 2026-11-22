package com.jestk.browser.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.jestk.browser.ui.viewmodel.BrowserUiEvent
import com.jestk.browser.ui.viewmodel.BrowserUiState
import com.jestk.browser.util.DefaultBrowserHelper
import com.jestk.browser.util.LocalVibrationFeedback

/** Scrollable body of one Settings category, built from the existing section composables. */
@Composable
internal fun SettingsCategoryContent(
    category: SettingsCategory,
    uiState: BrowserUiState,
    onEvent: (BrowserUiEvent) -> Unit,
    isDark: Boolean,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val vibration = LocalVibrationFeedback.current
    val appVersion = rememberAppVersion()
    val defaultBrowserLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.StartActivityForResult(),
        ) {
            onEvent(BrowserUiEvent.UpdateDefaultBrowserStatus(DefaultBrowserHelper.isDefaultBrowser(context)))
        }

    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(top = 8.dp, bottom = 36.dp),
    ) {
        when (category) {
            SettingsCategory.GENERAL -> {
                SettingsAppearanceSection(uiState = uiState, onEvent = onEvent, isDark = isDark)
                Spacer(modifier = Modifier.height(24.dp))
                SettingsGeneralSection(
                    uiState = uiState,
                    onEvent = onEvent,
                    isDark = isDark,
                    context = context,
                    vibration = vibration,
                    defaultBrowserLauncher = defaultBrowserLauncher,
                )
            }
            SettingsCategory.SEARCH -> SettingsSearchSection(uiState = uiState, onEvent = onEvent, isDark = isDark)
            SettingsCategory.PROTECTION -> {
                SettingsPrivacySection(uiState = uiState, onEvent = onEvent, isDark = isDark)
                Spacer(modifier = Modifier.height(24.dp))
                SettingsContentBlockingSection(uiState = uiState, onEvent = onEvent, isDark = isDark)
            }
            SettingsCategory.ABOUT -> {
                SettingsAboutSection(
                    uiState = uiState,
                    onEvent = onEvent,
                    isDark = isDark,
                    context = context,
                    appVersion = appVersion,
                )
                Spacer(modifier = Modifier.height(28.dp))
                SettingsFooter(isDark = isDark, appVersion = appVersion)
            }
        }
    }
}

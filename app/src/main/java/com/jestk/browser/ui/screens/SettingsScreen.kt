package com.jestk.browser.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBackIos
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.jestk.browser.R
import com.jestk.browser.domain.model.ThemeMode
import com.jestk.browser.ui.theme.DarkBackground
import com.jestk.browser.ui.theme.DarkOnSurface
import com.jestk.browser.ui.theme.JestkBrowserTheme
import com.jestk.browser.ui.theme.LocalIsDarkTheme
import com.jestk.browser.ui.theme.LocalProstoTypography
import com.jestk.browser.ui.theme.ProstoBrandPrimary
import com.jestk.browser.ui.theme.ProstoCanvasFloor
import com.jestk.browser.ui.theme.ProstoDarkText
import com.jestk.browser.ui.theme.ProstoMutedSlate
import com.jestk.browser.ui.viewmodel.BrowserUiEvent
import com.jestk.browser.ui.viewmodel.BrowserUiState
import com.jestk.browser.util.DefaultBrowserHelper
import com.jestk.browser.util.LocalVibrationFeedback
/**
 * Settings Screen - Dedicated full-screen settings destination in Prosto Canvas design system.
 * Features:
 * - Full-screen layout with edge-to-edge padding.
 * - Grouped categories: Appearance, General, Search, Privacy & Protection, and About & Prosto.
 * - Minimalist outline icons without heavy background plates for enhanced readability.
 * - Prosto tactile switches and dropdown menus with crisp borders.
 */
@Composable
fun SettingsScreen(
    uiState: BrowserUiState,
    onEvent: (BrowserUiEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val isDark = LocalIsDarkTheme.current
    val scrollState = rememberScrollState()

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .background(if (isDark) DarkBackground else ProstoCanvasFloor)
                .statusBarsPadding()
                .navigationBarsPadding(),
    ) {
        // Dedicated Top Bar
        SettingsTopBar(
            isDark = isDark,
            onBackClick = { onEvent(BrowserUiEvent.DismissOverlay) },
            onCloseClick = { onEvent(BrowserUiEvent.DismissOverlay) },
        )

        // Scrollable Settings Content
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(scrollState)
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 36.dp),
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            // Large Title Row
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(bottom = 20.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.settings),
                    style = LocalProstoTypography.current.headlineLg,
                    color = if (isDark) DarkOnSurface else ProstoDarkText,
                )
            }

            SettingsContent(
                uiState = uiState,
                onEvent = onEvent,
                isDark = isDark,
            )
        }
    }
}

@Composable
fun SettingsTopBar(
    isDark: Boolean,
    onBackClick: () -> Unit,
    onCloseClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(
            onClick = onBackClick,
            modifier = Modifier.size(40.dp),
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBackIos,
                contentDescription = stringResource(R.string.action_back),
                tint = ProstoBrandPrimary,
                modifier = Modifier.size(20.dp),
            )
        }

        Text(
            text = stringResource(R.string.settings),
            style = LocalProstoTypography.current.headlineSm,
            color = if (isDark) DarkOnSurface else ProstoDarkText,
        )

        IconButton(
            onClick = onCloseClick,
            modifier = Modifier.size(40.dp),
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = stringResource(R.string.action_close),
                tint = ProstoMutedSlate,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
fun SettingsContent(
    uiState: BrowserUiState,
    onEvent: (BrowserUiEvent) -> Unit,
    isDark: Boolean,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val vibration = LocalVibrationFeedback.current
    val appVersion = remember(context) {
        runCatching {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull() ?: ""
    }
    val defaultBrowserLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.StartActivityForResult(),
        ) {
            onEvent(BrowserUiEvent.UpdateDefaultBrowserStatus(DefaultBrowserHelper.isDefaultBrowser(context)))
        }

    Column(modifier = modifier.fillMaxWidth()) {
        // ==========================================
        // 1. APPEARANCE
        // ==========================================
        SettingsAppearanceSection(uiState = uiState, onEvent = onEvent, isDark = isDark)

        Spacer(modifier = Modifier.height(24.dp))

        // ==========================================
        // 2. GENERAL
        // ==========================================
        SettingsGeneralSection(
            uiState = uiState,
            onEvent = onEvent,
            isDark = isDark,
            context = context,
            vibration = vibration,
            defaultBrowserLauncher = defaultBrowserLauncher,
        )

        Spacer(modifier = Modifier.height(24.dp))

        // ==========================================
        // 3. SEARCH
        // ==========================================
        SettingsSearchSection(uiState = uiState, onEvent = onEvent, isDark = isDark)

        Spacer(modifier = Modifier.height(24.dp))

        // ==========================================
        // 4. PRIVACY & PROTECTION
        // ==========================================
        SettingsPrivacySection(uiState = uiState, onEvent = onEvent, isDark = isDark)

        Spacer(modifier = Modifier.height(24.dp))

        // ==========================================
        // 5. ABOUT & SYSTEM
        // ==========================================
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

@Preview(name = "SettingsScreen Light", showBackground = true, widthDp = 412, heightDp = 800)
@Composable
fun SettingsScreenLightPreview() {
    JestkBrowserTheme(themeMode = ThemeMode.LIGHT) {
        SettingsScreen(
            uiState = BrowserUiState(),
            onEvent = {},
        )
    }
}

@Preview(name = "SettingsScreen Dark", showBackground = true, widthDp = 412, heightDp = 800)
@Composable
fun SettingsScreenDarkPreview() {
    JestkBrowserTheme(themeMode = ThemeMode.DARK) {
        SettingsScreen(
            uiState = BrowserUiState(),
            onEvent = {},
        )
    }
}

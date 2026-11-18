package com.jestk.browser.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBackIos
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.jestk.browser.ui.viewmodel.BrowserUiEvent
import com.jestk.browser.ui.viewmodel.BrowserUiState

/**
 * Settings destination: a root list of categories, each opening a sub-screen of grouped cards.
 * Back returns from a sub-screen to the root before the Settings overlay itself is dismissed.
 */
@Composable
fun SettingsScreen(
    uiState: BrowserUiState,
    onEvent: (BrowserUiEvent) -> Unit,
    modifier: Modifier = Modifier,
    initialCategory: SettingsCategory? = null,
) {
    val isDark = LocalIsDarkTheme.current
    var selected by rememberSaveable { mutableStateOf(initialCategory) }

    BackHandler(enabled = selected != null) { selected = null }

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .background(if (isDark) DarkBackground else ProstoCanvasFloor)
                .statusBarsPadding()
                .navigationBarsPadding(),
    ) {
        AnimatedContent(
            targetState = selected,
            transitionSpec = {
                val forward = targetState != null
                val offset = { width: Int -> if (forward) width / 4 else -width / 4 }
                (slideInHorizontally(tween(220)) { offset(it) } + fadeIn(tween(220))) togetherWith
                    (slideOutHorizontally(tween(220)) { -offset(it) } + fadeOut(tween(120)))
            },
            modifier = Modifier.fillMaxSize(),
            label = "settingsCategory",
        ) { category ->
            Column(modifier = Modifier.fillMaxSize()) {
                if (category == null) {
                    SettingsTopBar(
                        title = stringResource(R.string.settings),
                        isDark = isDark,
                        onBackClick = { onEvent(BrowserUiEvent.DismissOverlay) },
                    )
                    SettingsCategoryList(
                        isDark = isDark,
                        onSelect = { selected = it },
                        modifier = Modifier.weight(1f),
                    )
                } else {
                    SettingsTopBar(
                        title = stringResource(category.titleRes),
                        isDark = isDark,
                        onBackClick = { selected = null },
                    )
                    SettingsCategoryContent(
                        category = category,
                        uiState = uiState,
                        onEvent = onEvent,
                        isDark = isDark,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
fun SettingsTopBar(
    title: String,
    isDark: Boolean,
    onBackClick: () -> Unit,
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
            text = title,
            style = LocalProstoTypography.current.headlineSm,
            color = if (isDark) DarkOnSurface else ProstoDarkText,
        )

        Spacer(modifier = Modifier.size(40.dp))
    }
}

@Composable
private fun SettingsPreview(
    mode: ThemeMode,
    category: SettingsCategory?,
) {
    JestkBrowserTheme(themeMode = mode) {
        SettingsScreen(uiState = BrowserUiState(), onEvent = {}, initialCategory = category)
    }
}

@Preview(name = "Settings Root Light", showBackground = true, widthDp = 412, heightDp = 800)
@Composable
fun SettingsScreenLightPreview() = SettingsPreview(ThemeMode.LIGHT, null)

@Preview(name = "Settings Root Dark", showBackground = true, widthDp = 412, heightDp = 800)
@Composable
fun SettingsScreenDarkPreview() = SettingsPreview(ThemeMode.DARK, null)

@Preview(name = "Settings General Light", showBackground = true, widthDp = 412, heightDp = 800)
@Composable
fun SettingsGeneralLightPreview() = SettingsPreview(ThemeMode.LIGHT, SettingsCategory.GENERAL)

@Preview(name = "Settings General Dark", showBackground = true, widthDp = 412, heightDp = 800)
@Composable
fun SettingsGeneralDarkPreview() = SettingsPreview(ThemeMode.DARK, SettingsCategory.GENERAL)

@Preview(name = "Settings Search Light", showBackground = true, widthDp = 412, heightDp = 800)
@Composable
fun SettingsSearchLightPreview() = SettingsPreview(ThemeMode.LIGHT, SettingsCategory.SEARCH)

@Preview(name = "Settings Search Dark", showBackground = true, widthDp = 412, heightDp = 800)
@Composable
fun SettingsSearchDarkPreview() = SettingsPreview(ThemeMode.DARK, SettingsCategory.SEARCH)

@Preview(name = "Settings Protection Light", showBackground = true, widthDp = 412, heightDp = 800)
@Composable
fun SettingsProtectionLightPreview() = SettingsPreview(ThemeMode.LIGHT, SettingsCategory.PROTECTION)

@Preview(name = "Settings Protection Dark", showBackground = true, widthDp = 412, heightDp = 800)
@Composable
fun SettingsProtectionDarkPreview() = SettingsPreview(ThemeMode.DARK, SettingsCategory.PROTECTION)

@Preview(name = "Settings About Light", showBackground = true, widthDp = 412, heightDp = 800)
@Composable
fun SettingsAboutLightPreview() = SettingsPreview(ThemeMode.LIGHT, SettingsCategory.ABOUT)

@Preview(name = "Settings About Dark", showBackground = true, widthDp = 412, heightDp = 800)
@Composable
fun SettingsAboutDarkPreview() = SettingsPreview(ThemeMode.DARK, SettingsCategory.ABOUT)

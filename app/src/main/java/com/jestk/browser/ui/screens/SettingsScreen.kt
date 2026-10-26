package com.jestk.browser.ui.screens

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBackIos
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.BrightnessAuto
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FilterNone
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.jestk.browser.R
import com.jestk.browser.domain.model.SearchEngine
import com.jestk.browser.domain.model.ThemeMode
import com.jestk.browser.domain.model.isVisible
import com.jestk.browser.ui.components.ProstoToggleSwitch
import com.jestk.browser.ui.overlays.SearchEngineBadge
import com.jestk.browser.ui.overlays.UpdateNotificationCard
import com.jestk.browser.ui.theme.DarkBackground
import com.jestk.browser.ui.theme.DarkOnSurface
import com.jestk.browser.ui.theme.DarkSurface
import com.jestk.browser.ui.theme.DarkSurfaceVariant
import com.jestk.browser.ui.theme.JestkBrowserTheme
import com.jestk.browser.ui.theme.LocalIsDarkTheme
import com.jestk.browser.ui.theme.LocalProstoTypography
import com.jestk.browser.ui.theme.ProstoBorders
import com.jestk.browser.ui.theme.ProstoBrandPrimary
import com.jestk.browser.ui.theme.ProstoCanvasFloor
import com.jestk.browser.ui.theme.ProstoDarkText
import com.jestk.browser.ui.theme.ProstoMutedSlate
import com.jestk.browser.ui.theme.ProstoPaleBlue
import com.jestk.browser.ui.theme.ProstoShapes
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
                contentDescription = "Back",
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
                contentDescription = "Close",
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
    var showProvidersDialog by remember { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxWidth()) {
        // Update notification banner (when an update is ready or pending)
        if (uiState.updateState.isVisible) {
            UpdateNotificationCard(
                state = uiState.updateState,
                onDownload = { onEvent(BrowserUiEvent.DownloadUpdate) },
                onInstall = { onEvent(BrowserUiEvent.InstallUpdate) },
                onInstallLater = { onEvent(BrowserUiEvent.InstallUpdateLater) },
                onSkip = { onEvent(BrowserUiEvent.SkipUpdate) },
                isDark = isDark,
            )
            Spacer(modifier = Modifier.height(24.dp))
        }

        // ==========================================
        // 1. APPEARANCE (ВНЕШНИЙ ВИД)
        // ==========================================
        SettingsSectionHeader(title = stringResource(R.string.settings_section_appearance))
        SegmentedThemeControl(
            selectedMode = uiState.settings.themeMode,
            isDark = isDark,
            onSelectMode = { onEvent(BrowserUiEvent.SetThemeMode(it)) },
        )

        Spacer(modifier = Modifier.height(12.dp))

        InsetGroupedCard(isDark = isDark) {
            SettingToggleRow(
                icon = Icons.Default.FilterNone,
                title = stringResource(R.string.gray_unloaded_tabs_title),
                summary = stringResource(R.string.gray_unloaded_tabs_summary),
                checked = uiState.settings.isGrayUnloadedTabsEnabled,
                onCheckedChange = { onEvent(BrowserUiEvent.SetGrayUnloadedTabsEnabled(it)) },
                isDark = isDark,
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // ==========================================
        // 2. GENERAL (ОБЩИЕ)
        // ==========================================
        SettingsSectionHeader(title = stringResource(R.string.settings_section_general))
        InsetGroupedCard(isDark = isDark) {
            // Default Browser (Only visible if not currently default browser)
            if (!uiState.isDefaultBrowser) {
                DefaultBrowserSettingTile(
                    isDark = isDark,
                    onClick = {
                        DefaultBrowserHelper.openDefaultBrowserSettings(context, defaultBrowserLauncher)
                    },
                )

                HorizontalDivider(
                    color = if (isDark) DarkSurfaceVariant else Color(0xFFF1F5F9),
                    modifier = Modifier.padding(start = 56.dp),
                )
            }

            // Open in Apps Row
            SettingToggleRow(
                icon = Icons.AutoMirrored.Filled.OpenInNew,
                title = stringResource(R.string.open_in_apps_setting_title),
                summary = stringResource(R.string.open_in_apps_setting_summary),
                checked = uiState.settings.isOpenInAppsEnabled,
                onCheckedChange = { onEvent(BrowserUiEvent.SetOpenInAppsEnabled(it)) },
                isDark = isDark,
            )

            HorizontalDivider(
                color = if (isDark) DarkSurfaceVariant else Color(0xFFF1F5F9),
                modifier = Modifier.padding(start = 56.dp),
            )

            // Vibration Row
            SettingToggleRow(
                icon = Icons.Default.Vibration,
                title = stringResource(R.string.vibration_setting_title),
                summary = stringResource(R.string.vibration_setting_summary),
                checked = uiState.settings.isVibrationEnabled,
                onCheckedChange = { enabled ->
                    onEvent(BrowserUiEvent.SetVibrationEnabled(enabled))
                    if (enabled) {
                        vibration.confirm()
                    }
                },
                isDark = isDark,
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // ==========================================
        // 3. SEARCH (ПОИСК)
        // ==========================================
        SettingsSectionHeader(title = stringResource(R.string.settings_section_search))
        InsetGroupedCard(isDark = isDark) {
            // Preferred Search Engine Row with Dropdown Menu
            var searchMenuExpanded by remember { mutableStateOf(false) }
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clickable { searchMenuExpanded = true }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    Box(
                        modifier = Modifier.width(24.dp),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = null,
                            tint = ProstoBrandPrimary,
                            modifier = Modifier.size(22.dp),
                        )
                    }

                    Column {
                        Text(
                            text = stringResource(R.string.search_engine_title),
                            style = LocalProstoTypography.current.bodyMd.copy(fontWeight = FontWeight.Medium),
                            color = if (isDark) DarkOnSurface else ProstoDarkText,
                        )
                        Text(
                            text = stringResource(R.string.search_engine_summary),
                            style = LocalProstoTypography.current.bodySm,
                            color = ProstoMutedSlate,
                        )
                    }
                }

                Box {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (isDark) DarkSurfaceVariant else ProstoPaleBlue,
                        modifier = Modifier.clickable { searchMenuExpanded = true },
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text(
                                text = uiState.settings.searchEngine.displayName,
                                style = LocalProstoTypography.current.labelSm.copy(fontWeight = FontWeight.SemiBold),
                                color = ProstoBrandPrimary,
                            )
                            Icon(
                                imageVector = Icons.Default.ArrowDropDown,
                                contentDescription = null,
                                tint = ProstoBrandPrimary,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }

                    DropdownMenu(
                        expanded = searchMenuExpanded,
                        onDismissRequest = { searchMenuExpanded = false },
                        modifier = Modifier.background(if (isDark) DarkSurface else Color.White),
                    ) {
                        uiState.searchEngines.forEach { engine ->
                            val isSelected = uiState.settings.searchEngine.id == engine.id
                            DropdownMenuItem(
                                text = {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        Text(
                                            text = engine.displayName,
                                            style = LocalProstoTypography.current.bodyMd,
                                            color = if (isDark) DarkOnSurface else ProstoDarkText,
                                        )
                                        if (engine.shortcut != null) {
                                            Text(
                                                text = "(${engine.shortcut})",
                                                style = LocalProstoTypography.current.labelSm,
                                                color = ProstoMutedSlate,
                                            )
                                        }
                                        if (isSelected) {
                                            Icon(
                                                imageVector = Icons.Default.Check,
                                                contentDescription = "Selected",
                                                tint = ProstoBrandPrimary,
                                                modifier = Modifier.size(16.dp),
                                            )
                                        }
                                    }
                                },
                                onClick = {
                                    onEvent(BrowserUiEvent.SetSearchEngine(engine))
                                    searchMenuExpanded = false
                                },
                            )
                        }
                    }
                }
            }

            HorizontalDivider(
                color = if (isDark) DarkSurfaceVariant else Color(0xFFF1F5F9),
                modifier = Modifier.padding(start = 56.dp),
            )

            // Add Search Engine Row
            SettingClickableRow(
                icon = Icons.Default.Add,
                title = stringResource(R.string.add_search_engine_title),
                summary = stringResource(R.string.add_search_engine_summary),
                onClick = { onEvent(BrowserUiEvent.ShowAddSearchEngineDialog()) },
                isDark = isDark,
            )

            HorizontalDivider(
                color = if (isDark) DarkSurfaceVariant else Color(0xFFF1F5F9),
                modifier = Modifier.padding(start = 56.dp),
            )

            // Search Page Providers Row
            SettingClickableRow(
                icon = Icons.Default.Tune,
                title = stringResource(R.string.search_providers_title),
                summary = stringResource(R.string.search_providers_summary),
                onClick = { showProvidersDialog = true },
                isDark = isDark,
            )
        }

        if (showProvidersDialog) {
            SearchPageProvidersDialog(
                engines = uiState.searchEngines,
                hiddenEngineIds = uiState.settings.hiddenSearchPageEngineIds,
                isDark = isDark,
                onToggleEngine = { engineId, isVisible ->
                    onEvent(BrowserUiEvent.ToggleSearchPageEngineVisibility(engineId, isVisible))
                },
                onDismiss = { showProvidersDialog = false },
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // ==========================================
        // 4. PRIVACY & PROTECTION (КОНФИДЕНЦИАЛЬНОСТЬ)
        // ==========================================
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SettingsSectionHeader(
                title = stringResource(R.string.settings_section_privacy),
                modifier = Modifier.padding(bottom = 0.dp),
            )

            Surface(
                shape = RoundedCornerShape(12.dp),
                color = if (isDark) DarkSurfaceVariant else ProstoPaleBlue,
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.VerifiedUser,
                        contentDescription = null,
                        tint = ProstoBrandPrimary,
                        modifier = Modifier.size(13.dp),
                    )
                    Text(
                        text = stringResource(R.string.privacy_status_protected),
                        style =
                            LocalProstoTypography.current.labelMd.copy(
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                            ),
                        color = ProstoBrandPrimary,
                    )
                }
            }
        }

        InsetGroupedCard(isDark = isDark) {
            // Ad Blocker Row
            SettingToggleRow(
                icon = Icons.Default.Block,
                title = stringResource(R.string.ad_blocker_title),
                summary = stringResource(R.string.ad_blocker_summary),
                checked = uiState.settings.isAdBlockerEnabled,
                onCheckedChange = { onEvent(BrowserUiEvent.SetAdBlockerEnabled(it)) },
                isDark = isDark,
            )

            HorizontalDivider(
                color = if (isDark) DarkSurfaceVariant else Color(0xFFF1F5F9),
                modifier = Modifier.padding(start = 56.dp),
            )

            // Tracking Protection Row
            SettingToggleRow(
                icon = Icons.Default.Shield,
                title = stringResource(R.string.tracking_protection_title),
                summary = stringResource(R.string.tracking_protection_summary),
                checked = uiState.settings.isTrackingProtectionEnabled,
                onCheckedChange = { onEvent(BrowserUiEvent.SetTrackingProtectionEnabled(it)) },
                isDark = isDark,
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // ==========================================
        // 5. ABOUT & SYSTEM (О ПРИЛОЖЕНИИ)
        // ==========================================
        SettingsSectionHeader(title = stringResource(R.string.settings_section_about))
        InsetGroupedCard(isDark = isDark) {
            // Auto-update Row
            SettingToggleRow(
                icon = Icons.Default.SystemUpdate,
                title = stringResource(R.string.update_setting_title),
                summary = stringResource(R.string.update_setting_summary),
                checked = uiState.settings.isAutoUpdateEnabled,
                onCheckedChange = { onEvent(BrowserUiEvent.SetAutoUpdateEnabled(it)) },
                isDark = isDark,
            )

            HorizontalDivider(
                color = if (isDark) DarkSurfaceVariant else Color(0xFFF1F5F9),
                modifier = Modifier.padding(start = 56.dp),
            )

            // Website link
            HyperlinkRow(
                icon = Icons.Default.Public,
                title = stringResource(R.string.about_app_website_title),
                subtitle = stringResource(R.string.about_app_website_summary),
                onClick = {
                    try {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://jestk.com"))
                        context.startActivity(intent)
                    } catch (_: Exception) {
                        Toast.makeText(context, "Could not open link", Toast.LENGTH_SHORT).show()
                    }
                },
                isDark = isDark,
            )

            HorizontalDivider(
                color = if (isDark) DarkSurfaceVariant else Color(0xFFF1F5F9),
                modifier = Modifier.padding(start = 56.dp),
            )

            // GitHub link
            HyperlinkRow(
                icon = Icons.Default.Code,
                title = stringResource(R.string.about_app_github_title),
                subtitle = stringResource(R.string.about_app_github_summary),
                onClick = {
                    try {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/Supscott/Prosto"))
                        context.startActivity(intent)
                    } catch (_: Exception) {
                        Toast.makeText(context, "Could not open link", Toast.LENGTH_SHORT).show()
                    }
                },
                isDark = isDark,
            )
        }

        Spacer(modifier = Modifier.height(28.dp))

        // Footer Note
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Box(
                modifier =
                    Modifier
                        .size(30.dp)
                        .clip(CircleShape)
                        .background(if (isDark) DarkSurfaceVariant else ProstoPaleBlue),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.Favorite,
                    contentDescription = null,
                    tint = ProstoBrandPrimary,
                    modifier = Modifier.size(16.dp),
                )
            }

            Text(
                text = stringResource(R.string.about_love_footer),
                style = LocalProstoTypography.current.bodySm,
                color = ProstoMutedSlate,
            )
            Text(
                text = stringResource(R.string.about_version, appVersion),
                style = LocalProstoTypography.current.labelSm,
                color = ProstoMutedSlate.copy(alpha = 0.7f),
            )
        }
    }
}

/**
 * Standard toggle row with a sleek outline icon, title, summary, and switch control.
 */
@Composable
fun SettingToggleRow(
    icon: ImageVector,
    title: String,
    summary: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    isDark: Boolean,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.weight(1f),
        ) {
            Box(
                modifier = Modifier.width(24.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = ProstoBrandPrimary,
                    modifier = Modifier.size(22.dp),
                )
            }

            Column {
                Text(
                    text = title,
                    style = LocalProstoTypography.current.bodyMd.copy(fontWeight = FontWeight.Medium),
                    color = if (isDark) DarkOnSurface else ProstoDarkText,
                )
                Text(
                    text = summary,
                    style = LocalProstoTypography.current.bodySm,
                    color = ProstoMutedSlate,
                )
            }
        }

        ProstoToggleSwitch(
            checked = checked,
            onCheckedChange = onCheckedChange,
        )
    }
}

/**
 * Standard clickable row with an outline icon, title, summary, and chevron indicator.
 */
@Composable
fun SettingClickableRow(
    icon: ImageVector,
    title: String,
    summary: String,
    onClick: () -> Unit,
    isDark: Boolean,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.weight(1f),
        ) {
            Box(
                modifier = Modifier.width(24.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = ProstoBrandPrimary,
                    modifier = Modifier.size(22.dp),
                )
            }

            Column {
                Text(
                    text = title,
                    style = LocalProstoTypography.current.bodyMd.copy(fontWeight = FontWeight.Medium),
                    color = if (isDark) DarkOnSurface else ProstoDarkText,
                )
                Text(
                    text = summary,
                    style = LocalProstoTypography.current.bodySm,
                    color = ProstoMutedSlate,
                )
            }
        }

        Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = null,
            tint = ProstoMutedSlate,
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
fun SettingsSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = title,
        style =
            LocalProstoTypography.current.labelMd.copy(
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.5.sp,
            ),
        color = ProstoMutedSlate,
        modifier = modifier.padding(bottom = 8.dp, start = 4.dp),
    )
}

@Composable
fun InsetGroupedCard(
    isDark: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Surface(
        shape = ProstoShapes.Card,
        color = if (isDark) DarkSurface else Color.White,
        border = ProstoBorders.cardBorder(isDark),
        shadowElevation = 2.dp,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column {
            content()
        }
    }
}

@Composable
fun SegmentedThemeControl(
    selectedMode: ThemeMode,
    isDark: Boolean,
    onSelectMode: (ThemeMode) -> Unit,
) {
    Surface(
        shape = ProstoShapes.Card,
        color = if (isDark) DarkSurface else Color.White,
        border = ProstoBorders.cardBorder(isDark),
        shadowElevation = 2.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            ThemeMode.entries.forEach { mode ->
                val isSelected = selectedMode == mode
                val segmentBg =
                    if (isSelected) {
                        if (isDark) DarkSurfaceVariant else ProstoPaleBlue
                    } else {
                        Color.Transparent
                    }

                val segmentBorder =
                    if (isSelected) {
                        BorderStroke(1.5.dp, ProstoBrandPrimary)
                    } else {
                        null
                    }

                val tint = if (isSelected) ProstoBrandPrimary else ProstoMutedSlate

                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = segmentBg,
                    border = segmentBorder,
                    shadowElevation = 0.dp,
                    modifier =
                        Modifier
                            .weight(1f)
                            .height(48.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .clickable { onSelectMode(mode) },
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        val icon: ImageVector =
                            when (mode) {
                                ThemeMode.SYSTEM -> Icons.Default.BrightnessAuto
                                ThemeMode.LIGHT -> Icons.Default.LightMode
                                ThemeMode.DARK -> Icons.Default.DarkMode
                            }
                        val label =
                            when (mode) {
                                ThemeMode.SYSTEM -> "System"
                                ThemeMode.LIGHT -> "Light"
                                ThemeMode.DARK -> "Dark"
                            }

                        Icon(
                            imageVector = icon,
                            contentDescription = label,
                            tint = tint,
                            modifier = Modifier.size(19.dp),
                        )
                        Text(
                            text = label,
                            style =
                                LocalProstoTypography.current.labelSm.copy(
                                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                                ),
                            color = tint,
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun HyperlinkRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    isDark: Boolean = LocalIsDarkTheme.current,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.weight(1f),
        ) {
            Box(
                modifier = Modifier.width(24.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = ProstoBrandPrimary,
                    modifier = Modifier.size(22.dp),
                )
            }

            Column {
                Text(
                    text = title,
                    style = LocalProstoTypography.current.bodyMd.copy(fontWeight = FontWeight.Medium),
                    color = if (isDark) DarkOnSurface else ProstoDarkText,
                )
                Text(
                    text = subtitle,
                    style = LocalProstoTypography.current.bodySm,
                    color = ProstoMutedSlate,
                )
            }
        }

        Icon(
            imageVector = Icons.AutoMirrored.Filled.OpenInNew,
            contentDescription = null,
            tint = ProstoMutedSlate,
            modifier = Modifier.size(18.dp),
        )
    }
}

@Composable
fun DefaultBrowserSettingTile(
    isDark: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.weight(1f),
        ) {
            Box(
                modifier = Modifier.width(24.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                Icon(
                    imageVector = Icons.Default.Public,
                    contentDescription = null,
                    tint = ProstoBrandPrimary,
                    modifier = Modifier.size(22.dp),
                )
            }

            Column {
                Text(
                    text = stringResource(R.string.default_browser_tile_title),
                    style = LocalProstoTypography.current.bodyMd.copy(fontWeight = FontWeight.Medium),
                    color = if (isDark) DarkOnSurface else ProstoDarkText,
                )
                Text(
                    text = stringResource(R.string.default_browser_tile_desc),
                    style = LocalProstoTypography.current.bodySm,
                    color = ProstoMutedSlate,
                )
            }
        }

        Surface(
            shape = RoundedCornerShape(12.dp),
            color = if (isDark) DarkSurfaceVariant else ProstoPaleBlue,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = stringResource(R.string.default_browser_tile_action),
                    style = LocalProstoTypography.current.labelSm.copy(fontWeight = FontWeight.SemiBold),
                    color = ProstoBrandPrimary,
                )
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = ProstoBrandPrimary,
                    modifier = Modifier.size(14.dp),
                )
            }
        }
    }
}

@Composable
fun SearchPageProvidersDialog(
    engines: List<SearchEngine>,
    hiddenEngineIds: Set<String>,
    isDark: Boolean,
    onToggleEngine: (String, Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = ProstoShapes.Card,
            color = if (isDark) DarkSurface else Color.White,
            border = ProstoBorders.cardBorder(isDark),
            shadowElevation = 8.dp,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.search_providers_title),
                        style = LocalProstoTypography.current.headlineSm.copy(fontWeight = FontWeight.Bold),
                        color = if (isDark) DarkOnSurface else ProstoDarkText,
                    )
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(32.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = ProstoMutedSlate,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }

                Text(
                    text = stringResource(R.string.search_providers_summary),
                    style = LocalProstoTypography.current.bodySm,
                    color = ProstoMutedSlate,
                    modifier = Modifier.padding(top = 4.dp, bottom = 16.dp),
                )

                HorizontalDivider(
                    color = if (isDark) DarkSurfaceVariant else Color(0xFFF1F5F9),
                    thickness = 1.dp,
                )

                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .heightIn(max = 320.dp)
                            .verticalScroll(rememberScrollState()),
                ) {
                    engines.forEachIndexed { index, engine ->
                        val isChecked = engine.id !in hiddenEngineIds
                        Row(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.weight(1f),
                            ) {
                                SearchEngineBadge(
                                    engine = engine,
                                    size = 28.dp,
                                    isDark = isDark,
                                )
                                Column {
                                    Text(
                                        text = engine.displayName,
                                        style = LocalProstoTypography.current.bodyMd.copy(fontWeight = FontWeight.Medium),
                                        color = if (isDark) DarkOnSurface else ProstoDarkText,
                                    )
                                    if (engine.shortcut != null) {
                                        Text(
                                            text = "Shortcut: ${engine.shortcut}",
                                            style = LocalProstoTypography.current.labelSm,
                                            color = ProstoMutedSlate,
                                        )
                                    }
                                }
                            }

                            ProstoToggleSwitch(
                                checked = isChecked,
                                onCheckedChange = { onToggleEngine(engine.id, it) },
                            )
                        }

                        if (index < engines.lastIndex) {
                            HorizontalDivider(
                                color = if (isDark) DarkSurfaceVariant else Color(0xFFF1F5F9),
                                thickness = 0.5.dp,
                            )
                        }
                    }
                }
            }
        }
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

package com.jestk.browser.ui.screens

import android.content.Context
import android.content.Intent
import androidx.activity.result.ActivityResultLauncher
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.BrightnessAuto
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.FilterNone
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jestk.browser.R
import com.jestk.browser.domain.model.ThemeMode
import com.jestk.browser.ui.theme.DarkSurface
import com.jestk.browser.ui.theme.DarkSurfaceVariant
import com.jestk.browser.ui.theme.LocalProstoTypography
import com.jestk.browser.ui.theme.ProstoBorders
import com.jestk.browser.ui.theme.ProstoBrandPrimary
import com.jestk.browser.ui.theme.ProstoMutedSlate
import com.jestk.browser.ui.theme.ProstoPaleBlue
import com.jestk.browser.ui.theme.ProstoShapes
import com.jestk.browser.ui.viewmodel.BrowserUiEvent
import com.jestk.browser.ui.viewmodel.BrowserUiState
import com.jestk.browser.util.DefaultBrowserHelper
import com.jestk.browser.util.VibrationFeedback

@Composable
internal fun SettingsAppearanceSection(
    uiState: BrowserUiState,
    onEvent: (BrowserUiEvent) -> Unit,
    isDark: Boolean,
) {
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
}

@Composable
internal fun SettingsGeneralSection(
    uiState: BrowserUiState,
    onEvent: (BrowserUiEvent) -> Unit,
    isDark: Boolean,
    context: Context,
    vibration: VibrationFeedback,
    defaultBrowserLauncher: ActivityResultLauncher<Intent>,
) {
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
}

@Composable
internal fun SettingsPrivacySection(
    uiState: BrowserUiState,
    onEvent: (BrowserUiEvent) -> Unit,
    isDark: Boolean,
) {
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

        // Ad Redirect & Popup Protection Row
        SettingToggleRow(
            icon = Icons.Default.Security,
            title = stringResource(R.string.ad_redirect_protection_title),
            summary = stringResource(R.string.ad_redirect_protection_summary),
            checked = uiState.settings.isAdRedirectProtectionEnabled,
            onCheckedChange = { onEvent(BrowserUiEvent.SetAdRedirectProtectionEnabled(it)) },
            isDark = isDark,
        )

        HorizontalDivider(
            color = if (isDark) DarkSurfaceVariant else Color(0xFFF1F5F9),
            modifier = Modifier.padding(start = 56.dp),
        )

        // Auto-dismiss Cookie Banners Row
        SettingToggleRow(
            icon = Icons.Default.VerifiedUser,
            title = stringResource(R.string.pref_auto_consent_title),
            summary = stringResource(R.string.pref_auto_consent_summary),
            checked = uiState.settings.autoConsentEnabled,
            onCheckedChange = { onEvent(BrowserUiEvent.SetAutoConsentEnabled(it)) },
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

        HorizontalDivider(
            color = if (isDark) DarkSurfaceVariant else Color(0xFFF1F5F9),
            modifier = Modifier.padding(start = 56.dp),
        )

        // Clean YouTube Player Row
        SettingToggleRow(
            icon = Icons.Default.PlayArrow,
            title = stringResource(R.string.clean_player_setting_title),
            summary = stringResource(R.string.clean_player_setting_summary),
            checked = uiState.settings.isCleanYouTubePlayerEnabled,
            onCheckedChange = { onEvent(BrowserUiEvent.SetCleanYouTubePlayerEnabled(it)) },
            isDark = isDark,
        )
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
                                ThemeMode.SYSTEM -> stringResource(R.string.theme_mode_system)
                                ThemeMode.LIGHT -> stringResource(R.string.theme_mode_light)
                                ThemeMode.DARK -> stringResource(R.string.theme_mode_dark)
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

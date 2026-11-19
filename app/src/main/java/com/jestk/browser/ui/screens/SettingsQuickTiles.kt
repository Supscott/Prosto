package com.jestk.browser.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrightnessAuto
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Feedback
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jestk.browser.R
import com.jestk.browser.domain.model.ThemeMode
import com.jestk.browser.ui.theme.DarkOnSurface
import com.jestk.browser.ui.theme.DarkSurface
import com.jestk.browser.ui.theme.LocalProstoTypography
import com.jestk.browser.ui.theme.ProstoBorders
import com.jestk.browser.ui.theme.ProstoBrandPrimary
import com.jestk.browser.ui.theme.ProstoDarkText
import com.jestk.browser.ui.theme.ProstoMutedSlate
import com.jestk.browser.ui.viewmodel.BrowserUiEvent
import com.jestk.browser.ui.viewmodel.BrowserUiState
import com.jestk.browser.ui.viewmodel.SetBackgroundPlaybackEnabled
import com.jestk.browser.util.LocalVibrationFeedback

/** Next mode in the System -> Light -> Dark cycle used by the Theme tile. */
internal fun ThemeMode.next(): ThemeMode =
    ThemeMode.entries[(ordinal + 1) % ThemeMode.entries.size]

/** Two-column grid of quick-toggle tiles shown at the top of the main Settings screen. */
@Composable
internal fun SettingsQuickTiles(
    uiState: BrowserUiState,
    onEvent: (BrowserUiEvent) -> Unit,
    isDark: Boolean,
    modifier: Modifier = Modifier,
) {
    val vibration = LocalVibrationFeedback.current
    val settings = uiState.settings
    val on = stringResource(R.string.settings_tile_on)
    val off = stringResource(R.string.settings_tile_off)
    val themeMode = settings.themeMode

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            QuickTile(
                icon = Icons.Default.MusicNote,
                title = stringResource(R.string.background_playback_title),
                state = if (settings.isBackgroundPlaybackEnabled) on else off,
                active = settings.isBackgroundPlaybackEnabled,
                isDark = isDark,
                onClick = {
                    vibration.tick()
                    onEvent(SetBackgroundPlaybackEnabled(!settings.isBackgroundPlaybackEnabled))
                },
                modifier = Modifier.weight(1f),
            )
            QuickTile(
                icon = Icons.Default.PlayCircle,
                title = stringResource(R.string.clean_player_setting_title),
                state = if (settings.isCleanYouTubePlayerEnabled) on else off,
                active = settings.isCleanYouTubePlayerEnabled,
                isDark = isDark,
                onClick = {
                    vibration.tick()
                    onEvent(BrowserUiEvent.SetCleanYouTubePlayerEnabled(!settings.isCleanYouTubePlayerEnabled))
                },
                modifier = Modifier.weight(1f),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            QuickTile(
                icon = Icons.Default.Shield,
                title = stringResource(R.string.ad_blocker_title),
                state = if (settings.isAdBlockerEnabled) on else off,
                active = settings.isAdBlockerEnabled,
                isDark = isDark,
                onClick = {
                    vibration.tick()
                    onEvent(BrowserUiEvent.SetAdBlockerEnabled(!settings.isAdBlockerEnabled))
                },
                modifier = Modifier.weight(1f),
            )
            QuickTile(
                icon =
                    when (themeMode) {
                        ThemeMode.SYSTEM -> Icons.Default.BrightnessAuto
                        ThemeMode.LIGHT -> Icons.Default.LightMode
                        ThemeMode.DARK -> Icons.Default.DarkMode
                    },
                title = stringResource(R.string.settings_tile_theme),
                state =
                    stringResource(
                        when (themeMode) {
                            ThemeMode.SYSTEM -> R.string.theme_mode_system
                            ThemeMode.LIGHT -> R.string.theme_mode_light
                            ThemeMode.DARK -> R.string.theme_mode_dark
                        },
                    ),
                active = false,
                isDark = isDark,
                onClick = {
                    vibration.tick()
                    onEvent(BrowserUiEvent.SetThemeMode(themeMode.next()))
                },
                modifier = Modifier.weight(1f),
                role = Role.Button,
            )
        }
    }
}

@Composable
private fun QuickTile(
    icon: ImageVector,
    title: String,
    state: String,
    active: Boolean,
    isDark: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    role: Role = Role.Switch,
) {
    val container by animateColorAsState(
        targetValue = if (active) ProstoBrandPrimary else if (isDark) DarkSurface else Color.White,
        animationSpec = tween(220),
        label = "tileContainer",
    )
    val primary by animateColorAsState(
        targetValue = if (active) Color.White else if (isDark) DarkOnSurface else ProstoDarkText,
        animationSpec = tween(220),
        label = "tilePrimary",
    )
    val secondary by animateColorAsState(
        targetValue = if (active) Color.White.copy(alpha = 0.85f) else ProstoMutedSlate,
        animationSpec = tween(220),
        label = "tileSecondary",
    )
    val iconTint by animateColorAsState(
        targetValue = if (active) Color.White else ProstoBrandPrimary,
        animationSpec = tween(220),
        label = "tileIcon",
    )
    val shape = RoundedCornerShape(24.dp)

    Surface(
        shape = shape,
        color = container,
        border = if (active) null else ProstoBorders.cardBorder(isDark),
        modifier = modifier.height(112.dp),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clickable(role = role, onClick = onClick)
                    .padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(26.dp),
            )
            Column {
                Text(
                    text = title,
                    style = LocalProstoTypography.current.bodyMd.copy(fontWeight = FontWeight.SemiBold),
                    color = primary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = state,
                    style = LocalProstoTypography.current.bodySm,
                    color = secondary,
                    maxLines = 1,
                )
            }
        }
    }
}

/** Prominent full-width "Send feedback" button at the bottom of the main Settings screen. */
@Composable
internal fun SettingsFeedbackButton(
    isDark: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = if (isDark) ProstoBrandPrimary.copy(alpha = 0.18f) else ProstoBrandPrimary.copy(alpha = 0.10f),
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clickable(role = Role.Button, onClick = onClick)
                    .padding(horizontal = 20.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
        ) {
            Icon(
                imageVector = Icons.Default.Feedback,
                contentDescription = null,
                tint = ProstoBrandPrimary,
                modifier = Modifier.size(22.dp),
            )
            Text(
                text = stringResource(R.string.settings_send_feedback),
                style = LocalProstoTypography.current.bodyMd.copy(fontWeight = FontWeight.SemiBold),
                color = ProstoBrandPrimary,
            )
        }
    }
}

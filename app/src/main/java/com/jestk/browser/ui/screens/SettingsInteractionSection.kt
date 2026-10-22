package com.jestk.browser.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jestk.browser.R
import com.jestk.browser.ui.components.ProstoToggleSwitch
import com.jestk.browser.ui.theme.DarkOnSurface
import com.jestk.browser.ui.theme.DarkSurfaceVariant
import com.jestk.browser.ui.theme.LocalProstoTypography
import com.jestk.browser.ui.theme.ProstoBrandPrimary
import com.jestk.browser.ui.theme.ProstoCanvasFloor
import com.jestk.browser.ui.theme.ProstoDarkText
import com.jestk.browser.ui.theme.ProstoMutedSlate

/**
 * INTERACTION section of the settings screen.
 *
 * Holds the vibration toggle - the single switch for the app's tactile feedback
 * (see `docs/adr/0002-platform-vibration-feedback.md`).
 */
@Composable
fun SettingsInteractionSection(
    isDark: Boolean,
    isVibrationEnabled: Boolean,
    onVibrationEnabledChange: (Boolean) -> Unit,
) {
    SettingsSectionHeader(title = stringResource(R.string.settings_section_interaction))
    InsetGroupedCard(isDark = isDark) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.weight(1f),
            ) {
                Box(
                    modifier =
                        Modifier
                            .size(34.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isDark) DarkSurfaceVariant else ProstoCanvasFloor),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Default.Vibration,
                        contentDescription = null,
                        tint = ProstoBrandPrimary,
                        modifier = Modifier.size(20.dp),
                    )
                }

                Column {
                    Text(
                        text = stringResource(R.string.vibration_setting_title),
                        style = LocalProstoTypography.current.bodyMd.copy(fontWeight = FontWeight.Medium),
                        color = if (isDark) DarkOnSurface else ProstoDarkText,
                    )
                    Text(
                        text = stringResource(R.string.vibration_setting_summary),
                        style = LocalProstoTypography.current.bodySm,
                        color = ProstoMutedSlate,
                    )
                }
            }

            ProstoToggleSwitch(
                checked = isVibrationEnabled,
                onCheckedChange = onVibrationEnabledChange,
            )
        }
    }
}

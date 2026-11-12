package com.jestk.browser.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.jestk.browser.R
import com.jestk.browser.ui.theme.DarkSurfaceVariant
import com.jestk.browser.ui.theme.LocalProstoTypography
import com.jestk.browser.ui.theme.ProstoBrandPrimary
import com.jestk.browser.ui.theme.ProstoMutedSlate
import com.jestk.browser.ui.theme.ProstoPaleBlue
import com.jestk.browser.ui.viewmodel.BrowserUiEvent
import com.jestk.browser.ui.viewmodel.BrowserUiState

@Composable
internal fun SettingsAboutSection(
    uiState: BrowserUiState,
    onEvent: (BrowserUiEvent) -> Unit,
    isDark: Boolean,
    context: Context,
    appVersion: String,
) {
    SettingsSectionHeader(title = stringResource(R.string.settings_section_about))
    InsetGroupedCard(isDark = isDark) {
        // Auto-update Toggle Row
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

        // Manual Check for Updates
        HyperlinkRow(
            icon = Icons.Default.SystemUpdate,
            title = stringResource(R.string.update_check_manual),
            subtitle = stringResource(R.string.update_check_up_to_date, appVersion),
            onClick = {
                onEvent(BrowserUiEvent.CheckForUpdate(manual = true))
            },
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
                    Toast.makeText(context, R.string.error_cannot_open_link, Toast.LENGTH_SHORT).show()
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
                    Toast.makeText(context, R.string.error_cannot_open_link, Toast.LENGTH_SHORT).show()
                }
            },
            isDark = isDark,
        )

        HorizontalDivider(
            color = if (isDark) DarkSurfaceVariant else Color(0xFFF1F5F9),
            modifier = Modifier.padding(start = 56.dp),
        )

        // Feedback and Community
        HyperlinkRow(
            icon = Icons.AutoMirrored.Filled.Chat,
            title = stringResource(R.string.settings_about_feedback_title),
            subtitle = stringResource(R.string.settings_about_feedback_summary),
            onClick = {
                onEvent(BrowserUiEvent.OpenFeedbackSheet)
            },
            isDark = isDark,
        )
    }
}

@Composable
internal fun SettingsFooter(
    isDark: Boolean,
    appVersion: String,
) {
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

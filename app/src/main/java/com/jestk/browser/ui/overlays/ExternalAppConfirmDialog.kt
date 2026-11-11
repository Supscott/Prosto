package com.jestk.browser.ui.overlays

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.jestk.browser.R
import com.jestk.browser.ui.theme.DarkOnSurface
import com.jestk.browser.ui.theme.DarkSurface
import com.jestk.browser.ui.theme.LocalProstoTypography
import com.jestk.browser.ui.theme.ProstoBorders
import com.jestk.browser.ui.theme.ProstoBrandPrimary
import com.jestk.browser.ui.theme.ProstoDarkText
import com.jestk.browser.ui.theme.ProstoMutedSlate
import com.jestk.browser.ui.theme.ProstoShapes

/**
 * Confirmation shown when the user deliberately taps a site's own "Open app" control.
 *
 * Actions: [onOpen] leaves the browser for the native app, [onCloseTab] closes the tab that asked
 * for the handoff, [onCancel] (also back / outside tap) keeps the page as it is.
 */
@Composable
fun ExternalAppConfirmDialog(
    isDark: Boolean,
    onOpen: () -> Unit,
    onCloseTab: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val typography = LocalProstoTypography.current

    Dialog(onDismissRequest = onCancel) {
        Surface(
            shape = ProstoShapes.Card,
            color = if (isDark) DarkSurface else Color.White,
            border = ProstoBorders.cardBorder(isDark),
            shadowElevation = 8.dp,
            modifier = modifier
                .fillMaxWidth()
                .testTag("external_app_confirm_dialog"),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
            ) {
                Text(
                    text = stringResource(R.string.external_app_confirm_title),
                    style = typography.headlineSm,
                    color = if (isDark) DarkOnSurface else ProstoDarkText,
                    fontWeight = FontWeight.Bold,
                )

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = stringResource(R.string.external_app_confirm_message),
                    style = typography.bodyMd,
                    color = ProstoMutedSlate,
                )

                Spacer(modifier = Modifier.height(20.dp))

                Button(
                    onClick = onOpen,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ProstoBrandPrimary,
                        contentColor = Color.White,
                    ),
                    shape = ProstoShapes.Pill,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("external_app_confirm_open"),
                ) {
                    Text(
                        text = stringResource(R.string.external_app_confirm_open),
                        style = typography.labelMd,
                        fontWeight = FontWeight.SemiBold,
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    TextButton(
                        onClick = onCloseTab,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("external_app_confirm_close_tab"),
                    ) {
                        Text(
                            text = stringResource(R.string.external_app_confirm_close_tab),
                            style = typography.labelMd,
                            color = ProstoMutedSlate,
                        )
                    }
                    TextButton(
                        onClick = onCancel,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("external_app_confirm_cancel"),
                    ) {
                        Text(
                            text = stringResource(R.string.external_app_confirm_cancel),
                            style = typography.labelMd,
                            color = ProstoMutedSlate,
                        )
                    }
                }
            }
        }
    }
}

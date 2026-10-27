package com.jestk.browser.ui.overlays

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jestk.browser.R
import com.jestk.browser.domain.model.AppUpdateUiState
import com.jestk.browser.ui.theme.DarkOnSurface
import com.jestk.browser.ui.theme.DarkSurface
import com.jestk.browser.ui.theme.DarkSurfaceVariant
import com.jestk.browser.ui.theme.LocalProstoTypography
import com.jestk.browser.ui.theme.ProstoBorders
import com.jestk.browser.ui.theme.ProstoBrandPrimary
import com.jestk.browser.ui.theme.ProstoCanvasFloor
import com.jestk.browser.ui.theme.ProstoDarkText
import com.jestk.browser.ui.theme.ProstoMutedSlate
import com.jestk.browser.ui.theme.ProstoPaleBlue
import com.jestk.browser.ui.theme.ProstoShapes

/**
 * In-app notification about a pending app update. Rendered on the home and the
 * settings screen whenever [state] is not [AppUpdateUiState.None].
 */
@Composable
fun UpdateNotificationCard(
    state: AppUpdateUiState,
    onInstall: () -> Unit,
    onSkip: () -> Unit,
    isDark: Boolean,
    modifier: Modifier = Modifier,
    onDownload: () -> Unit = onInstall,
) {
    Surface(
        shape = ProstoShapes.Card,
        color = if (isDark) DarkSurface else Color.White,
        border = ProstoBorders.cardBorder(isDark),
        shadowElevation = 4.dp,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    modifier =
                        Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isDark) DarkSurfaceVariant else ProstoPaleBlue),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Default.SystemUpdate,
                        contentDescription = null,
                        tint = ProstoBrandPrimary,
                        modifier = Modifier.size(22.dp),
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text =
                            stringResource(
                                when (state) {
                                    is AppUpdateUiState.ReadyToInstall -> R.string.update_card_title_ready
                                    else -> R.string.update_card_title
                                },
                            ),
                        style =
                            LocalProstoTypography.current.headlineSm.copy(
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                            ),
                        color = if (isDark) DarkOnSurface else ProstoDarkText,
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = updateBody(state),
                        style =
                            LocalProstoTypography.current.bodySm.copy(
                                fontSize = 12.sp,
                                lineHeight = 16.sp,
                            ),
                        color = ProstoMutedSlate,
                    )
                }
            }

            if (state is AppUpdateUiState.Downloading) {
                Spacer(modifier = Modifier.height(12.dp))
                LinearProgressIndicator(
                    progress = { state.progress / 100f },
                    color = ProstoBrandPrimary,
                    trackColor = if (isDark) DarkSurfaceVariant else ProstoCanvasFloor,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            if (state !is AppUpdateUiState.Downloading) {
                Spacer(modifier = Modifier.height(14.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    when (state) {
                        is AppUpdateUiState.Available -> {
                            PrimaryActionButton(
                                text = stringResource(R.string.update_action_download),
                                onClick = onDownload,
                            )

                            TextButton(
                                onClick = onSkip,
                                shape = ProstoShapes.Pill,
                                modifier = Modifier.height(38.dp),
                            ) {
                                Text(
                                    text = stringResource(R.string.update_action_skip),
                                    style = LocalProstoTypography.current.labelMd,
                                    color = ProstoMutedSlate,
                                )
                            }
                        }

                        is AppUpdateUiState.ReadyToInstall -> {
                            PrimaryActionButton(
                                text = stringResource(R.string.update_action_restart_install),
                                onClick = onInstall,
                            )
                        }

                        else -> Unit
                    }
                }
            }
        }
    }
}

@Composable
private fun PrimaryActionButton(
    text: String,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        shape = ProstoShapes.Pill,
        colors =
            ButtonDefaults.buttonColors(
                containerColor = ProstoBrandPrimary,
                contentColor = Color.White,
            ),
        modifier = Modifier.height(38.dp),
    ) {
        Text(
            text = text,
            style = LocalProstoTypography.current.labelMd.copy(fontWeight = FontWeight.Bold),
        )
    }
}

@Composable
private fun updateBody(state: AppUpdateUiState): String =
    when (state) {
        is AppUpdateUiState.Available ->
            state.update.releaseNotes.takeIf { it.isNotBlank() }
                ?: stringResource(R.string.update_card_body, state.update.versionName)

        is AppUpdateUiState.Downloading ->
            stringResource(R.string.update_card_downloading, state.progress)

        is AppUpdateUiState.ReadyToInstall ->
            stringResource(R.string.update_card_ready_body, state.versionName)

        AppUpdateUiState.None ->
            ""
    }

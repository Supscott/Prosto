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
import com.jestk.browser.ui.theme.LocalSereneTypography
import com.jestk.browser.ui.theme.SereneBorders
import com.jestk.browser.ui.theme.SereneBrandPrimary
import com.jestk.browser.ui.theme.SereneCanvasFloor
import com.jestk.browser.ui.theme.SereneDarkText
import com.jestk.browser.ui.theme.SereneMutedSlate
import com.jestk.browser.ui.theme.SerenePaleBlue
import com.jestk.browser.ui.theme.SereneShapes

/**
 * In-app notification about a pending app update. Rendered on the home and the
 * settings screen whenever [state] is not [AppUpdateUiState.None].
 */
@Composable
fun UpdateNotificationCard(
    state: AppUpdateUiState,
    onInstall: () -> Unit,
    onInstallLater: () -> Unit,
    onSkip: () -> Unit,
    isDark: Boolean,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = SereneShapes.Card,
        color = if (isDark) DarkSurface else Color.White,
        border = SereneBorders.cardBorder(isDark),
        shadowElevation = 2.dp,
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
                            .background(if (isDark) DarkSurfaceVariant else SerenePaleBlue),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Default.SystemUpdate,
                        contentDescription = null,
                        tint = SereneBrandPrimary,
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
                            LocalSereneTypography.current.headlineSm.copy(
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                            ),
                        color = if (isDark) DarkOnSurface else SereneDarkText,
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = updateBody(state),
                        style =
                            LocalSereneTypography.current.bodySm.copy(
                                fontSize = 12.sp,
                                lineHeight = 16.sp,
                            ),
                        color = SereneMutedSlate,
                    )
                }
            }

            if (state is AppUpdateUiState.Downloading) {
                Spacer(modifier = Modifier.height(12.dp))
                LinearProgressIndicator(
                    progress = { state.progress / 100f },
                    color = SereneBrandPrimary,
                    trackColor = if (isDark) DarkSurfaceVariant else SereneCanvasFloor,
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
                    Button(
                        onClick = onInstall,
                        shape = SereneShapes.Pill,
                        colors =
                            ButtonDefaults.buttonColors(
                                containerColor = SereneBrandPrimary,
                                contentColor = Color.White,
                            ),
                        modifier = Modifier.height(38.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.update_action_install),
                            style = LocalSereneTypography.current.labelMd.copy(fontWeight = FontWeight.Bold),
                        )
                    }

                    if (state is AppUpdateUiState.Available) {
                        OutlinedButton(
                            onClick = onInstallLater,
                            shape = SereneShapes.Pill,
                            border = BorderStroke(1.dp, SereneBrandPrimary.copy(alpha = 0.35f)),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = SereneBrandPrimary),
                            modifier = Modifier.height(38.dp),
                        ) {
                            Text(
                                text = stringResource(R.string.update_action_install_later),
                                style = LocalSereneTypography.current.labelMd,
                            )
                        }
                    }

                    TextButton(
                        onClick = onSkip,
                        shape = SereneShapes.Pill,
                        modifier = Modifier.height(38.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.update_action_skip),
                            style = LocalSereneTypography.current.labelMd,
                            color = SereneMutedSlate,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun updateBody(state: AppUpdateUiState): String =
    when (state) {
        is AppUpdateUiState.Available -> {
            stringResource(R.string.update_card_body, state.update.versionName)
        }

        is AppUpdateUiState.Downloading -> {
            stringResource(R.string.update_card_downloading, state.progress)
        }

        is AppUpdateUiState.ReadyToInstall -> {
            stringResource(R.string.update_card_ready_body, state.versionName)
        }

        AppUpdateUiState.None -> {
            ""
        }
    }

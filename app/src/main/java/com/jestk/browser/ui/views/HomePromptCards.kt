package com.jestk.browser.ui.views

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Public
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jestk.browser.R
import com.jestk.browser.ui.theme.DarkOnSurface
import com.jestk.browser.ui.theme.DarkSurface
import com.jestk.browser.ui.theme.DarkSurfaceVariant
import com.jestk.browser.ui.theme.LocalProstoTypography
import com.jestk.browser.ui.theme.ProstoBorders
import com.jestk.browser.ui.theme.ProstoBrandPrimary
import com.jestk.browser.ui.theme.ProstoCanvasFloor
import com.jestk.browser.ui.theme.ProstoDarkText
import com.jestk.browser.ui.theme.ProstoMutedSlate
import com.jestk.browser.ui.theme.ProstoShapes

@Composable
internal fun DefaultBrowserPromptCard(
    isDark: Boolean,
    onSetDefaultClick: () -> Unit,
    onDismissClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = ProstoShapes.Card,
        color = if (isDark) DarkSurface else Color.White,
        border = ProstoBorders.cardBorder(isDark),
        shadowElevation = 2.dp,
        modifier = modifier,
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
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    Box(
                        modifier =
                            Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    if (isDark) DarkSurfaceVariant else ProstoCanvasFloor,
                                ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Default.Public,
                            contentDescription = null,
                            tint = ProstoBrandPrimary,
                            modifier = Modifier.size(24.dp),
                        )
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.default_browser_prompt_title),
                            style =
                                LocalProstoTypography.current.headlineSm.copy(
                                    fontSize = 15.sp,
                                    letterSpacing = (-0.2).sp,
                                ),
                            color = if (isDark) DarkOnSurface else ProstoDarkText,
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = stringResource(R.string.default_browser_prompt_desc),
                            style =
                                LocalProstoTypography.current.bodySm.copy(
                                    fontSize = 12.sp,
                                    lineHeight = 16.sp,
                                ),
                            color = ProstoMutedSlate,
                        )
                    }
                }

                Box(
                    modifier =
                        Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .clickable(onClick = onDismissClick),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = stringResource(R.string.dismiss),
                        tint = ProstoMutedSlate,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Button(
                onClick = onSetDefaultClick,
                shape = ProstoShapes.Pill,
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor = ProstoBrandPrimary,
                        contentColor = Color.White,
                    ),
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(40.dp),
            ) {
                Text(
                    text = stringResource(R.string.default_browser_prompt_button),
                    style =
                        LocalProstoTypography.current.labelMd.copy(
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                        ),
                )
            }
        }
    }
}

@Composable
fun FeedbackPromptCard(
    isDark: Boolean,
    onRateClick: () -> Unit,
    onSnoozeClick: () -> Unit,
    onDismissClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = ProstoShapes.Card,
        color = if (isDark) DarkSurface else Color.White,
        border = ProstoBorders.cardBorder(isDark),
        shadowElevation = 2.dp,
        modifier = modifier,
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
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    Box(
                        modifier =
                            Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    if (isDark) DarkSurfaceVariant else ProstoCanvasFloor,
                                ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Default.Favorite,
                            contentDescription = stringResource(R.string.a11y_feedback_icon),
                            tint = ProstoBrandPrimary,
                            modifier = Modifier.size(24.dp).testTag("feedback_card_icon"),
                        )
                    }

                    Column(
                        modifier =
                            Modifier
                                .weight(1f)
                                .padding(end = 8.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.feedback_card_title),
                            style =
                                LocalProstoTypography.current.headlineSm.copy(
                                    fontSize = 15.sp,
                                    letterSpacing = (-0.2).sp,
                                ),
                            color = if (isDark) DarkOnSurface else ProstoDarkText,
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = stringResource(R.string.feedback_card_desc),
                            style =
                                LocalProstoTypography.current.bodySm.copy(
                                    fontSize = 12.sp,
                                    lineHeight = 16.sp,
                                ),
                            color = ProstoMutedSlate,
                        )
                    }
                }

                Box(
                    modifier =
                        Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .clickable(onClick = onDismissClick)
                            .testTag("feedback_card_dismiss"),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = stringResource(R.string.dismiss),
                        tint = ProstoMutedSlate,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = onRateClick,
                    shape = ProstoShapes.Pill,
                    colors =
                        ButtonDefaults.buttonColors(
                            containerColor = ProstoBrandPrimary,
                            contentColor = Color.White,
                        ),
                    modifier = Modifier.height(40.dp).testTag("feedback_card_rate_button"),
                ) {
                    Text(
                        text = stringResource(R.string.feedback_card_action_rate),
                        style =
                            LocalProstoTypography.current.labelMd.copy(
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                            ),
                    )
                }

                TextButton(
                    onClick = onSnoozeClick,
                    shape = ProstoShapes.Pill,
                    colors =
                        ButtonDefaults.textButtonColors(
                            contentColor = ProstoMutedSlate,
                        ),
                    modifier = Modifier.height(40.dp).testTag("feedback_card_later_button"),
                ) {
                    Text(
                        text = stringResource(R.string.feedback_card_action_later),
                        style =
                            LocalProstoTypography.current.labelMd.copy(
                                fontWeight = FontWeight.Medium,
                                color = ProstoMutedSlate,
                            ),
                    )
                }
            }
        }
    }
}

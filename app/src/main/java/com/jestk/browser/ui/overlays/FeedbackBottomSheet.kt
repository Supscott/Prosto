package com.jestk.browser.ui.overlays

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.jestk.browser.R
import com.jestk.browser.domain.model.ThemeMode
import com.jestk.browser.ui.theme.DarkOnSurface
import com.jestk.browser.ui.theme.DarkOutlineVariant
import com.jestk.browser.ui.theme.DarkSurface
import com.jestk.browser.ui.theme.DarkSurfaceVariant
import com.jestk.browser.ui.theme.JestkBrowserTheme
import com.jestk.browser.ui.theme.LocalProstoTypography
import com.jestk.browser.ui.theme.ProstoBorders
import com.jestk.browser.ui.theme.ProstoDarkText
import com.jestk.browser.ui.theme.ProstoMutedSlate
import com.jestk.browser.ui.theme.ProstoOutlineVariant
import com.jestk.browser.ui.theme.ProstoShapes

const val TELEGRAM_COMMUNITY_URL = "https://t.me/prosto_browser_chat"
const val GITHUB_ISSUES_URL = "https://github.com/Supscott/Prosto/issues"

/**
 * Safely dispatches an external URL intent with guard against missing browser/activity
 * or security restrictions.
 */
internal fun launchFeedbackUrl(context: Context, url: String) {
    try {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, R.string.error_cannot_open_link, Toast.LENGTH_SHORT).show()
    } catch (_: SecurityException) {
        Toast.makeText(context, R.string.error_cannot_open_link, Toast.LENGTH_SHORT).show()
    }
}

/**
 * Prosto Canvas drag handle pill: 36.dp x 4.dp with ProstoShapes.Pill shape.
 */
@Composable
fun FeedbackDragHandle(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .padding(top = 12.dp, bottom = 8.dp)
            .size(width = 36.dp, height = 4.dp)
            .background(
                color = ProstoMutedSlate.copy(alpha = 0.4f),
                shape = ProstoShapes.Pill,
            ),
    )
}

/**
 * Thumb-reachable bottom sheet overlay adhering strictly to Prosto Canvas design
 * that provides users with two direct feedback channels: Telegram Community Chat and GitHub Issues.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedbackBottomSheet(
    isDark: Boolean,
    onDismiss: () -> Unit,
    onFeedbackSubmitted: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        shape = ProstoShapes.BottomSheet,
        containerColor = if (isDark) DarkSurface else Color.White,
        tonalElevation = 0.dp,
        scrimColor = Color.Black.copy(alpha = 0.45f),
        dragHandle = {
            FeedbackDragHandle()
        },
        modifier = modifier.border(
            border = ProstoBorders.cardBorder(isDark),
            shape = ProstoShapes.BottomSheet,
        ),
    ) {
        FeedbackBottomSheetContent(
            isDark = isDark,
            onDismiss = onDismiss,
            onFeedbackSubmitted = onFeedbackSubmitted,
        )
    }
}

/**
 * Content container of FeedbackBottomSheet, extracted for reusability and preview/unit testing.
 */
@Composable
fun FeedbackBottomSheetContent(
    isDark: Boolean,
    onDismiss: () -> Unit,
    onFeedbackSubmitted: () -> Unit,
    modifier: Modifier = Modifier,
    showDragHandle: Boolean = false,
) {
    val context = LocalContext.current

    Column(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(bottom = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (showDragHandle) {
            FeedbackDragHandle()
        }

        // Header with title and dismiss close button
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 24.dp, end = 16.dp, top = 4.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = stringResource(R.string.feedback_sheet_title),
                style = LocalProstoTypography.current.headlineSm.copy(fontWeight = FontWeight.Bold),
                color = if (isDark) DarkOnSurface else ProstoDarkText,
            )
            IconButton(
                onClick = onDismiss,
                modifier = Modifier.size(36.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = stringResource(R.string.dismiss),
                    tint = ProstoMutedSlate,
                    modifier = Modifier.size(20.dp),
                )
            }
        }

        // Row 1: Telegram Community Chat
        FeedbackChannelRow(
            icon = Icons.AutoMirrored.Filled.Send,
            iconTint = if (isDark) Color(0xFF38BDF8) else Color(0xFF0284C7),
            iconBg = if (isDark) DarkSurfaceVariant else Color(0xFFE0F2FE),
            title = stringResource(R.string.feedback_sheet_telegram_title),
            description = stringResource(R.string.feedback_sheet_telegram_desc),
            isDark = isDark,
            onClick = {
                launchFeedbackUrl(context, TELEGRAM_COMMUNITY_URL)
                onFeedbackSubmitted()
            },
            modifier = Modifier.padding(horizontal = 16.dp),
        )

        HorizontalDivider(
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 6.dp),
            color = if (isDark) DarkOutlineVariant.copy(alpha = 0.5f) else ProstoOutlineVariant.copy(alpha = 0.5f),
            thickness = 1.dp,
        )

        // Row 2: GitHub Issues
        FeedbackChannelRow(
            icon = Icons.Default.BugReport,
            iconTint = if (isDark) DarkOnSurface else Color(0xFF24292F),
            iconBg = if (isDark) DarkSurfaceVariant else Color(0xFFF1F5F9),
            title = stringResource(R.string.feedback_sheet_github_title),
            description = stringResource(R.string.feedback_sheet_github_desc),
            isDark = isDark,
            onClick = {
                launchFeedbackUrl(context, GITHUB_ISSUES_URL)
                onFeedbackSubmitted()
            },
            modifier = Modifier.padding(horizontal = 16.dp),
        )
    }
}

@Composable
private fun FeedbackChannelRow(
    icon: ImageVector,
    iconTint: Color,
    iconBg: Color,
    title: String,
    description: String,
    isDark: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(ProstoShapes.Md)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(iconBg),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(22.dp),
            )
        }

        Spacer(modifier = Modifier.width(14.dp))

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = title,
                style = LocalProstoTypography.current.bodyMd.copy(
                    fontWeight = FontWeight.SemiBold,
                ),
                color = if (isDark) DarkOnSurface else ProstoDarkText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = description,
                style = LocalProstoTypography.current.bodySm,
                color = ProstoMutedSlate,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = null,
            tint = ProstoMutedSlate.copy(alpha = 0.6f),
            modifier = Modifier.size(20.dp),
        )
    }
}

@Preview(name = "Feedback Bottom Sheet Light", showBackground = true, backgroundColor = 0xFFFFFFFF)
@Composable
fun FeedbackBottomSheetLightPreview() {
    JestkBrowserTheme(themeMode = ThemeMode.LIGHT) {
        Surface(
            shape = ProstoShapes.BottomSheet,
            color = Color.White,
            border = ProstoBorders.cardBorder(isDark = false),
        ) {
            FeedbackBottomSheetContent(
                isDark = false,
                onDismiss = {},
                onFeedbackSubmitted = {},
                showDragHandle = true,
            )
        }
    }
}

@Preview(name = "Feedback Bottom Sheet Dark", showBackground = true, backgroundColor = 0xFF171D1C)
@Composable
fun FeedbackBottomSheetDarkPreview() {
    JestkBrowserTheme(themeMode = ThemeMode.DARK) {
        Surface(
            shape = ProstoShapes.BottomSheet,
            color = DarkSurface,
            border = ProstoBorders.cardBorder(isDark = true),
        ) {
            FeedbackBottomSheetContent(
                isDark = true,
                onDismiss = {},
                onFeedbackSubmitted = {},
                showDragHandle = true,
            )
        }
    }
}

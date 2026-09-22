package com.jestk.browser.ui.overlays

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jestk.browser.R
import com.jestk.browser.domain.model.ThemeMode
import com.jestk.browser.ui.theme.JestkBrowserTheme
import com.jestk.browser.ui.theme.LiquidBorders
import com.jestk.browser.ui.theme.LiquidShapes
import com.jestk.browser.ui.theme.LiquidOnSurface
import com.jestk.browser.ui.theme.LiquidOnSurfaceVariant
import com.jestk.browser.ui.theme.LiquidOutline
import com.jestk.browser.ui.theme.LiquidPrimary
import com.jestk.browser.ui.theme.LiquidSecondary
import com.jestk.browser.ui.theme.LiquidSurfaceContainerHigh
import com.jestk.browser.ui.theme.LiquidTertiary
import com.jestk.browser.ui.theme.LocalIsDarkTheme
import com.jestk.browser.ui.theme.liquidFrostedGlass
import com.jestk.browser.ui.viewmodel.BrowserUiEvent

/**
 * Link Context Menu Floating Modal - Liquid Pastel Sunset design system.
 * Anchored directly under/near the user's finger touch position on hyperlinks.
 *
 * Actions:
 * 1. Open in new tab (foreground / active)
 * 2. Open in background (current screen stays on the page)
 * 3. Copy URL to system clipboard
 */
@Composable
fun LinkContextMenuModal(
    url: String,
    onEvent: (BrowserUiEvent) -> Unit,
    anchor: Offset? = null,
    modifier: Modifier = Modifier,
) {
    FloatingContextMenuContainer(
        anchor = anchor,
        onDismiss = { onEvent(BrowserUiEvent.DismissLinkContextMenu) },
        modifier = modifier,
    ) {
        LinkContextMenuContent(
            url = url,
            onEvent = onEvent,
        )
    }
}

/**
 * Backwards-compatible alias for [LinkContextMenuModal].
 */
@Composable
fun LinkContextMenuBottomSheet(
    url: String,
    onEvent: (BrowserUiEvent) -> Unit,
    anchor: Offset? = null,
    modifier: Modifier = Modifier,
) = LinkContextMenuModal(
    url = url,
    onEvent = onEvent,
    anchor = anchor,
    modifier = modifier,
)

/**
 * Visual content of Link Context Menu, extracted for reuse and Robolectric preview testing.
 */
@Composable
fun LinkContextMenuContent(
    url: String,
    onEvent: (BrowserUiEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val isDark = LocalIsDarkTheme.current

    val urlCardBg = if (isDark) Color(0xFF2B2627) else LiquidSurfaceContainerHigh
    val textColor = if (isDark) Color(0xFFF6EFF0) else LiquidOnSurface
    val secondaryTextColor = if (isDark) Color(0xFFC2C7D0) else LiquidOnSurfaceVariant

    val displayHost =
        remember(url) {
            runCatching {
                val uri = Uri.parse(url)
                uri.host?.takeIf { it.isNotBlank() } ?: url
            }.getOrDefault(url)
        }

    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        // URL Header Card
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .liquidFrostedGlass(
                        shape = RoundedCornerShape(14.dp),
                        isDark = isDark,
                        blurRadius = 24.dp,
                        tintColor = urlCardBg,
                        alpha = if (isDark) 0.55f else 0.50f,
                    )
                    .padding(horizontal = 10.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier =
                    Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(LiquidPrimary.copy(alpha = if (isDark) 0.25f else 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.Language,
                    contentDescription = null,
                    tint = LiquidPrimary,
                    modifier = Modifier.size(18.dp),
                )
            }

            Spacer(modifier = Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                if (displayHost != url) {
                    Text(
                        text = displayHost,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style =
                            MaterialTheme.typography.bodyMedium.copy(
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.SemiBold,
                            ),
                        color = textColor,
                    )
                }
                Text(
                    text = url,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style =
                        MaterialTheme.typography.bodySmall.copy(
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Normal,
                        ),
                    color = secondaryTextColor,
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        HorizontalDivider(
            color = LiquidOutline.copy(alpha = if (isDark) 0.15f else 0.12f),
            thickness = 0.8.dp,
        )

        Spacer(modifier = Modifier.height(6.dp))

        // Action 1: Open in new tab (foreground)
        ContextMenuActionRow(
            icon = Icons.AutoMirrored.Filled.OpenInNew,
            iconTint = LiquidPrimary,
            iconBg = LiquidPrimary.copy(alpha = if (isDark) 0.25f else 0.12f),
            title = stringResource(R.string.open_in_new_tab),
            titleColor = textColor,
            onClick = {
                onEvent(BrowserUiEvent.DismissLinkContextMenu)
                onEvent(BrowserUiEvent.OpenNewTab(url))
            },
        )

        Spacer(modifier = Modifier.height(2.dp))

        // Action 2: Open in background
        ContextMenuActionRow(
            icon = Icons.Default.Layers,
            iconTint = LiquidSecondary,
            iconBg = LiquidSecondary.copy(alpha = if (isDark) 0.25f else 0.12f),
            title = stringResource(R.string.open_in_background),
            titleColor = textColor,
            onClick = {
                onEvent(BrowserUiEvent.DismissLinkContextMenu)
                onEvent(BrowserUiEvent.OpenTabInBackground(url))
                Toast.makeText(
                    context,
                    context.getString(R.string.tab_opened_in_background),
                    Toast.LENGTH_SHORT,
                ).show()
            },
        )

        Spacer(modifier = Modifier.height(2.dp))

        // Action 3: Copy URL
        ContextMenuActionRow(
            icon = Icons.Default.ContentCopy,
            iconTint = LiquidTertiary,
            iconBg = LiquidTertiary.copy(alpha = if (isDark) 0.25f else 0.12f),
            title = stringResource(R.string.copy_link_url),
            titleColor = textColor,
            onClick = {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("URL", url)
                clipboard.setPrimaryClip(clip)
                Toast.makeText(
                    context,
                    context.getString(R.string.url_copied),
                    Toast.LENGTH_SHORT,
                ).show()
                onEvent(BrowserUiEvent.DismissLinkContextMenu)
            },
        )
    }
}

@Preview(name = "Link Context Menu Light", showBackground = true, backgroundColor = 0xFFFFF8F8)
@Composable
fun LinkContextMenuLightPreview() {
    JestkBrowserTheme(themeMode = ThemeMode.LIGHT) {
        Surface(
            shape = LiquidShapes.Card,
            color = Color(0xFFFFF8F8).copy(alpha = 0.96f),
            border = LiquidBorders.specularBorder(isDark = false),
            shadowElevation = 16.dp,
            modifier = Modifier.width(310.dp),
        ) {
            LinkContextMenuContent(
                url = "https://example.com/articles/liquid-pastel-sunset",
                onEvent = {},
            )
        }
    }
}

@Preview(name = "Link Context Menu Dark", showBackground = true, backgroundColor = 0xFF1E1B1C)
@Composable
fun LinkContextMenuDarkPreview() {
    JestkBrowserTheme(themeMode = ThemeMode.DARK) {
        Surface(
            shape = LiquidShapes.Card,
            color = Color(0xFF1E1B1C).copy(alpha = 0.94f),
            border = LiquidBorders.specularBorder(isDark = true),
            shadowElevation = 16.dp,
            modifier = Modifier.width(310.dp),
        ) {
            LinkContextMenuContent(
                url = "https://example.com/articles/liquid-pastel-sunset",
                onEvent = {},
            )
        }
    }
}

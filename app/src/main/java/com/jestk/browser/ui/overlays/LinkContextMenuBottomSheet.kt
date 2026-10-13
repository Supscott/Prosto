package com.jestk.browser.ui.overlays

import android.content.Context
import android.net.Uri
import android.widget.Toast
import com.jestk.browser.util.ClipboardHelper
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import com.jestk.browser.R
import com.jestk.browser.domain.model.ThemeMode
import com.jestk.browser.ui.theme.DarkOnSurface
import com.jestk.browser.ui.theme.DarkSurface
import com.jestk.browser.ui.theme.DarkSurfaceVariant
import com.jestk.browser.ui.theme.JestkBrowserTheme
import com.jestk.browser.ui.theme.LocalIsDarkTheme
import com.jestk.browser.ui.theme.LocalSereneTypography
import com.jestk.browser.ui.theme.SereneAccent
import com.jestk.browser.ui.theme.SereneBorders
import com.jestk.browser.ui.theme.SereneBrandPrimary
import com.jestk.browser.ui.theme.SereneCanvasFloor
import com.jestk.browser.ui.theme.SereneDarkText
import com.jestk.browser.ui.theme.SereneMutedSlate
import com.jestk.browser.ui.theme.SerenePaleBlue
import com.jestk.browser.ui.theme.SereneShapes
import com.jestk.browser.ui.viewmodel.BrowserUiEvent

/**
 * Link Context Menu Floating Modal - Serene Canvas design system.
 * Anchored directly under/near the user's finger touch position on hyperlinks.
 * Crisp Level 2 solid surface with NO BLUR.
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

    val tabOpenedInBackgroundMessage = stringResource(R.string.tab_opened_in_background)
    val urlCopiedMessage = stringResource(R.string.url_copied)

    val textColor = if (isDark) DarkOnSurface else SereneDarkText
    val headerBg = if (isDark) DarkSurfaceVariant else SereneCanvasFloor
    val dividerColor = if (isDark) DarkSurfaceVariant else Color(0xFFF1F5F9)

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
        // URL Header Card (Solid Level 1 Inset)
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = headerBg,
            border = SereneBorders.cardBorder(isDark),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier =
                        Modifier
                            .size(32.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isDark) DarkSurface else SerenePaleBlue),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Default.Language,
                        contentDescription = null,
                        tint = SereneBrandPrimary,
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
                            style = LocalSereneTypography.current.bodyMd.copy(
                                fontWeight = FontWeight.SemiBold,
                            ),
                            color = textColor,
                        )
                    }
                    Text(
                        text = url,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = LocalSereneTypography.current.bodySm,
                        color = SereneMutedSlate,
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        HorizontalDivider(
            color = dividerColor,
            thickness = 1.dp,
        )

        Spacer(modifier = Modifier.height(6.dp))

        // Action 1: Open in new tab (foreground)
        ContextMenuActionRow(
            icon = Icons.AutoMirrored.Filled.OpenInNew,
            iconTint = SereneBrandPrimary,
            iconBg = if (isDark) DarkSurfaceVariant else SerenePaleBlue,
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
            iconTint = SereneAccent,
            iconBg = if (isDark) DarkSurfaceVariant else Color(0xFFEFF6FF),
            title = stringResource(R.string.open_in_background),
            titleColor = textColor,
            onClick = {
                onEvent(BrowserUiEvent.DismissLinkContextMenu)
                onEvent(BrowserUiEvent.OpenTabInBackground(url))
                Toast.makeText(
                    context,
                    tabOpenedInBackgroundMessage,
                    Toast.LENGTH_SHORT,
                ).show()
            },
        )

        Spacer(modifier = Modifier.height(2.dp))

        // Action 3: Copy URL
        ContextMenuActionRow(
            icon = Icons.Default.ContentCopy,
            iconTint = SereneMutedSlate,
            iconBg = if (isDark) DarkSurfaceVariant else Color(0xFFF1F5F9),
            title = stringResource(R.string.copy_link_url),
            titleColor = textColor,
            onClick = {
                ClipboardHelper.copyToClipboard(
                    context = context,
                    text = url,
                    toastMessage = urlCopiedMessage,
                )
                onEvent(BrowserUiEvent.DismissLinkContextMenu)
            },
        )
    }
}

@Preview(name = "Link Context Menu Light", showBackground = true, backgroundColor = 0xFFF1F7FC)
@Composable
fun LinkContextMenuLightPreview() {
    JestkBrowserTheme(themeMode = ThemeMode.LIGHT) {
        Surface(
            shape = SereneShapes.Card,
            color = Color.White,
            border = SereneBorders.floatingBorder(isDark = false),
            shadowElevation = 8.dp,
            modifier = Modifier.width(310.dp),
        ) {
            LinkContextMenuContent(
                url = "https://example.com/articles/serene-canvas-design",
                onEvent = {},
            )
        }
    }
}

@Preview(name = "Link Context Menu Dark", showBackground = true, backgroundColor = 0xFF0F172A)
@Composable
fun LinkContextMenuDarkPreview() {
    JestkBrowserTheme(themeMode = ThemeMode.DARK) {
        Surface(
            shape = SereneShapes.Card,
            color = DarkSurface,
            border = SereneBorders.floatingBorder(isDark = true),
            shadowElevation = 8.dp,
            modifier = Modifier.width(310.dp),
        ) {
            LinkContextMenuContent(
                url = "https://example.com/articles/serene-canvas-design",
                onEvent = {},
            )
        }
    }
}

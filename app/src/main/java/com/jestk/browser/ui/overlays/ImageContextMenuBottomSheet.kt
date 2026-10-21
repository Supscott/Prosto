package com.jestk.browser.ui.overlays

import android.content.Context
import android.net.Uri
import com.jestk.browser.util.ClipboardHelper
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Image
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
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
 * Image Context Menu Floating Modal - Serene Canvas design system.
 * Anchored directly under/near the user's finger touch position on images.
 * Crisp Level 2 solid surface with NO BLUR.
 *
 * Actions:
 * 1. "Скопировать ссылку на изображение" (Copies image URL to clipboard).
 * 2. "Скачать изображение" (Calls download logic).
 * 3. "Превью (открыть в новой вкладке)" (Opens photo in a new tab).
 */
@Composable
fun ImageContextMenuModal(
    imageUrl: String,
    onEvent: (BrowserUiEvent) -> Unit,
    linkUrl: String? = null,
    anchor: Offset? = null,
    onDownloadImage: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    FloatingContextMenuContainer(
        anchor = anchor,
        onDismiss = { onEvent(BrowserUiEvent.DismissImageContextMenu) },
        modifier = modifier,
    ) {
        ImageContextMenuContent(
            imageUrl = imageUrl,
            linkUrl = linkUrl,
            onEvent = onEvent,
            onDownloadImage = onDownloadImage,
        )
    }
}

/**
 * Backwards-compatible alias for [ImageContextMenuModal].
 */
@Composable
fun ImageContextMenuBottomSheet(
    imageUrl: String,
    onEvent: (BrowserUiEvent) -> Unit,
    onDownloadImage: ((String) -> Unit)? = null,
    linkUrl: String? = null,
    anchor: Offset? = null,
    modifier: Modifier = Modifier,
) = ImageContextMenuModal(
    imageUrl = imageUrl,
    linkUrl = linkUrl,
    anchor = anchor,
    onEvent = onEvent,
    onDownloadImage = onDownloadImage,
    modifier = modifier,
)

/**
 * Content of Image Context Menu, separated for clean composable reuse and Robolectric preview tests.
 */
@Composable
fun ImageContextMenuContent(
    imageUrl: String,
    onEvent: (BrowserUiEvent) -> Unit,
    linkUrl: String? = null,
    onDownloadImage: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val isDark = LocalIsDarkTheme.current

    val imageUrlCopiedMessage = stringResource(R.string.image_url_copied)

    val textColor = if (isDark) DarkOnSurface else SereneDarkText
    val headerBg = if (isDark) DarkSurfaceVariant else SereneCanvasFloor
    val dividerColor = if (isDark) DarkSurfaceVariant else Color(0xFFF1F5F9)

    val displayHost =
        remember(imageUrl) {
            runCatching {
                val uri = Uri.parse(imageUrl)
                uri.host?.takeIf { it.isNotBlank() } ?: imageUrl
            }.getOrDefault(imageUrl)
        }

    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        // Image Header Card (Thumbnail Preview + URL)
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
                // Image Thumbnail Container
                Box(
                    modifier =
                        Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isDark) DarkSurface else SerenePaleBlue),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Default.Image,
                        contentDescription = null,
                        tint = SereneBrandPrimary,
                        modifier = Modifier.size(20.dp),
                    )

                    if (imageUrl.isNotBlank()) {
                        AsyncImage(
                            model =
                                ImageRequest.Builder(context)
                                    .data(imageUrl)
                                    .crossfade(true)
                                    .build(),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier =
                                Modifier
                                    .fillMaxSize()
                                    .clip(RoundedCornerShape(10.dp)),
                        )
                    }
                }

                Spacer(modifier = Modifier.width(10.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = displayHost,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = LocalSereneTypography.current.bodyMd.copy(
                            fontWeight = FontWeight.SemiBold,
                        ),
                        color = textColor,
                    )
                    Text(
                        text = imageUrl,
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

        // Optional: Open image link in new tab if image was inside <a>
        if (!linkUrl.isNullOrBlank()) {
            ContextMenuActionRow(
                icon = Icons.AutoMirrored.Filled.OpenInNew,
                iconTint = SereneBrandPrimary,
                iconBg = if (isDark) DarkSurfaceVariant else SerenePaleBlue,
                title = stringResource(R.string.open_in_new_tab),
                titleColor = textColor,
                onClick = {
                    onEvent(BrowserUiEvent.DismissImageContextMenu)
                    onEvent(BrowserUiEvent.OpenNewTab(linkUrl))
                },
            )

            Spacer(modifier = Modifier.height(2.dp))
        }

        // Action 1: "Превью (открыть в новой вкладке)"
        ContextMenuActionRow(
            icon = Icons.Default.Image,
            iconTint = SereneBrandPrimary,
            iconBg = if (isDark) DarkSurfaceVariant else SerenePaleBlue,
            title = stringResource(R.string.preview_image),
            titleColor = textColor,
            onClick = {
                onEvent(BrowserUiEvent.DismissImageContextMenu)
                onEvent(BrowserUiEvent.PreviewImageInNewTab(imageUrl))
            },
        )

        Spacer(modifier = Modifier.height(2.dp))

        // Action 2: "Скопировать ссылку на изображение"
        ContextMenuActionRow(
            icon = Icons.Default.ContentCopy,
            iconTint = SereneMutedSlate,
            iconBg = if (isDark) DarkSurfaceVariant else Color(0xFFF1F5F9),
            title = stringResource(R.string.copy_image_url),
            titleColor = textColor,
            onClick = {
                ClipboardHelper.copyToClipboard(
                    context = context,
                    text = imageUrl,
                    label = "Image URL",
                    toastMessage = imageUrlCopiedMessage,
                )
                onEvent(BrowserUiEvent.CopyImageUrl(imageUrl))
                onEvent(BrowserUiEvent.DismissImageContextMenu)
            },
        )

        Spacer(modifier = Modifier.height(2.dp))

        // Action 3: "Скачать изображение"
        ContextMenuActionRow(
            icon = Icons.Default.Download,
            iconTint = SereneAccent,
            iconBg = if (isDark) DarkSurfaceVariant else Color(0xFFDFF7F3),
            title = stringResource(R.string.download_image),
            titleColor = textColor,
            onClick = {
                onDownloadImage?.invoke(imageUrl)
                onEvent(BrowserUiEvent.DownloadImage(imageUrl))
                onEvent(BrowserUiEvent.DismissImageContextMenu)
            },
        )
    }
}

@Preview(name = "Image Context Menu Light", showBackground = true, backgroundColor = 0xFFE9F6F2)
@Composable
fun ImageContextMenuLightPreview() {
    JestkBrowserTheme(themeMode = ThemeMode.LIGHT) {
        Surface(
            shape = SereneShapes.Card,
            color = Color.White,
            border = SereneBorders.floatingBorder(isDark = false),
            shadowElevation = 8.dp,
            modifier = Modifier.width(310.dp),
        ) {
            ImageContextMenuContent(
                imageUrl = "https://images.unsplash.com/photo-1579783902614-a3fb3927b675?auto=format&fit=crop&w=800&q=80",
                onEvent = {},
            )
        }
    }
}

@Preview(name = "Image Context Menu Dark", showBackground = true, backgroundColor = 0xFF0E1513)
@Composable
fun ImageContextMenuDarkPreview() {
    JestkBrowserTheme(themeMode = ThemeMode.DARK) {
        Surface(
            shape = SereneShapes.Card,
            color = DarkSurface,
            border = SereneBorders.floatingBorder(isDark = true),
            shadowElevation = 8.dp,
            modifier = Modifier.width(310.dp),
        ) {
            ImageContextMenuContent(
                imageUrl = "https://images.unsplash.com/photo-1579783902614-a3fb3927b675?auto=format&fit=crop&w=800&q=80",
                onEvent = {},
            )
        }
    }
}

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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.jestk.browser.R
import com.jestk.browser.domain.model.ThemeMode
import com.jestk.browser.ui.theme.JestkBrowserTheme
import com.jestk.browser.ui.theme.LiquidBorders
import com.jestk.browser.ui.theme.LiquidOnSurface
import com.jestk.browser.ui.theme.LiquidOnSurfaceVariant
import com.jestk.browser.ui.theme.LiquidOutline
import com.jestk.browser.ui.theme.LiquidPrimary
import com.jestk.browser.ui.theme.LiquidSecondary
import com.jestk.browser.ui.theme.LiquidShapes
import com.jestk.browser.ui.theme.LiquidSurfaceContainerHigh
import com.jestk.browser.ui.theme.LiquidTertiary
import com.jestk.browser.ui.theme.LocalIsDarkTheme
import com.jestk.browser.ui.theme.liquidFrostedGlass
import com.jestk.browser.ui.viewmodel.BrowserUiEvent

/**
 * Image Context Menu Floating Modal - Liquid Pastel Sunset design system.
 * Anchored directly under/near the user's finger touch position on images.
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

    val urlCardBg = if (isDark) Color(0xFF2B2627) else LiquidSurfaceContainerHigh
    val textColor = if (isDark) Color(0xFFF6EFF0) else LiquidOnSurface
    val secondaryTextColor = if (isDark) Color(0xFFC2C7D0) else LiquidOnSurfaceVariant

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
            // Image Thumbnail Container
            Box(
                modifier =
                    Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(LiquidPrimary.copy(alpha = if (isDark) 0.25f else 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.Image,
                    contentDescription = null,
                    tint = LiquidPrimary,
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
                    style =
                        MaterialTheme.typography.bodyMedium.copy(
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.SemiBold,
                        ),
                    color = textColor,
                )
                Text(
                    text = imageUrl,
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

        // Optional: Open image link in new tab if image was inside <a>
        if (!linkUrl.isNullOrBlank()) {
            ContextMenuActionRow(
                icon = Icons.AutoMirrored.Filled.OpenInNew,
                iconTint = LiquidPrimary,
                iconBg = LiquidPrimary.copy(alpha = if (isDark) 0.25f else 0.12f),
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
            iconTint = LiquidPrimary,
            iconBg = LiquidPrimary.copy(alpha = if (isDark) 0.25f else 0.12f),
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
            iconTint = LiquidTertiary,
            iconBg = LiquidTertiary.copy(alpha = if (isDark) 0.25f else 0.12f),
            title = stringResource(R.string.copy_image_url),
            titleColor = textColor,
            onClick = {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("Image URL", imageUrl)
                clipboard.setPrimaryClip(clip)
                Toast.makeText(
                    context,
                    context.getString(R.string.image_url_copied),
                    Toast.LENGTH_SHORT,
                ).show()
                onEvent(BrowserUiEvent.CopyImageUrl(imageUrl))
                onEvent(BrowserUiEvent.DismissImageContextMenu)
            },
        )

        Spacer(modifier = Modifier.height(2.dp))

        // Action 3: "Скачать изображение"
        ContextMenuActionRow(
            icon = Icons.Default.Download,
            iconTint = LiquidSecondary,
            iconBg = LiquidSecondary.copy(alpha = if (isDark) 0.25f else 0.12f),
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

@Preview(name = "Image Context Menu Light", showBackground = true, backgroundColor = 0xFFFFF8F8)
@Composable
fun ImageContextMenuLightPreview() {
    JestkBrowserTheme(themeMode = ThemeMode.LIGHT) {
        Surface(
            shape = LiquidShapes.Card,
            color = Color(0xFFFFF8F8).copy(alpha = 0.96f),
            border = LiquidBorders.specularBorder(isDark = false),
            shadowElevation = 16.dp,
            modifier = Modifier.width(310.dp),
        ) {
            ImageContextMenuContent(
                imageUrl = "https://images.unsplash.com/photo-1579783902614-a3fb3927b675?auto=format&fit=crop&w=800&q=80",
                onEvent = {},
            )
        }
    }
}

@Preview(name = "Image Context Menu Dark", showBackground = true, backgroundColor = 0xFF1E1B1C)
@Composable
fun ImageContextMenuDarkPreview() {
    JestkBrowserTheme(themeMode = ThemeMode.DARK) {
        Surface(
            shape = LiquidShapes.Card,
            color = Color(0xFF1E1B1C).copy(alpha = 0.94f),
            border = LiquidBorders.specularBorder(isDark = true),
            shadowElevation = 16.dp,
            modifier = Modifier.width(310.dp),
        ) {
            ImageContextMenuContent(
                imageUrl = "https://images.unsplash.com/photo-1579783902614-a3fb3927b675?auto=format&fit=crop&w=800&q=80",
                onEvent = {},
            )
        }
    }
}

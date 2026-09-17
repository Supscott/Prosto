package com.jestk.browser.ui.overlays

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
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
 * Image Context Menu Bottom Sheet - Liquid Pastel Sunset design system.
 * Triggered on WebView long tap on images.
 *
 * Actions:
 * 1. "Скопировать ссылку на изображение" (Copies image URL to clipboard, shows confirmation toast).
 * 2. "Скачать изображение" (Calls download logic).
 * 3. "Превью (открыть в новой вкладке)" (Opens photo in a new tab and enters active tab).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImageContextMenuBottomSheet(
    imageUrl: String,
    onEvent: (BrowserUiEvent) -> Unit,
    onDownloadImage: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val sheetState = rememberModalBottomSheetState()
    val isDark = LocalIsDarkTheme.current
    val sheetBg = if (isDark) Color(0xFF1E1B1C).copy(alpha = 0.92f) else Color(0xFFFFF8F8).copy(alpha = 0.90f)

    ModalBottomSheet(
        onDismissRequest = { onEvent(BrowserUiEvent.DismissImageContextMenu) },
        sheetState = sheetState,
        shape = LiquidShapes.BottomSheet,
        containerColor = sheetBg,
        modifier = modifier
    ) {
        ImageContextMenuContent(
            imageUrl = imageUrl,
            onEvent = onEvent,
            onDownloadImage = onDownloadImage,
            modifier = Modifier.navigationBarsPadding()
        )
    }
}

/**
 * Content of Image Context Menu, separated for clean composable reuse and Robolectric preview tests.
 */
@Composable
fun ImageContextMenuContent(
    imageUrl: String,
    onEvent: (BrowserUiEvent) -> Unit,
    onDownloadImage: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val isDark = LocalIsDarkTheme.current

    val urlCardBg = if (isDark) Color(0xFF2B2627) else LiquidSurfaceContainerHigh
    val textColor = if (isDark) Color(0xFFF6EFF0) else LiquidOnSurface
    val secondaryTextColor = if (isDark) Color(0xFFC2C7D0) else LiquidOnSurfaceVariant

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(bottom = 28.dp)
    ) {
        // Image Header Card (Thumbnail Preview + URL)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .liquidFrostedGlass(
                    shape = LiquidShapes.Card,
                    isDark = isDark,
                    blurRadius = 16.dp,
                    tintColor = urlCardBg,
                    alpha = if (isDark) 0.85f else 0.75f,
                    borderColor = if (isDark) Color.White.copy(alpha = 0.12f) else Color.White.copy(alpha = 0.75f)
                )
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Image Thumbnail Container
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(LiquidPrimary.copy(alpha = if (isDark) 0.25f else 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Image,
                    contentDescription = null,
                    tint = LiquidPrimary,
                    modifier = Modifier.size(24.dp)
                )

                if (imageUrl.isNotBlank()) {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(imageUrl)
                            .crossfade(true)
                            .build(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(12.dp))
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Ellipsized URL
            Text(
                text = imageUrl,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 13.5.sp,
                    lineHeight = 18.sp,
                    fontWeight = FontWeight.Normal
                ),
                color = secondaryTextColor,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        HorizontalDivider(
            color = LiquidOutline.copy(alpha = if (isDark) 0.15f else 0.12f),
            thickness = 1.dp
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Action 1: "Скопировать ссылку на изображение"
        ImageActionRow(
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
                    Toast.LENGTH_SHORT
                ).show()
                onEvent(BrowserUiEvent.CopyImageUrl(imageUrl))
                onEvent(BrowserUiEvent.DismissImageContextMenu)
            }
        )

        Spacer(modifier = Modifier.height(6.dp))

        // Action 2: "Скачать изображение"
        ImageActionRow(
            icon = Icons.Default.Download,
            iconTint = LiquidSecondary,
            iconBg = LiquidSecondary.copy(alpha = if (isDark) 0.25f else 0.12f),
            title = stringResource(R.string.download_image),
            titleColor = textColor,
            onClick = {
                onDownloadImage?.invoke(imageUrl)
                onEvent(BrowserUiEvent.DownloadImage(imageUrl))
                onEvent(BrowserUiEvent.DismissImageContextMenu)
            }
        )

        Spacer(modifier = Modifier.height(6.dp))

        // Action 3: "Превью (открыть в новой вкладке)"
        ImageActionRow(
            icon = Icons.AutoMirrored.Filled.OpenInNew,
            iconTint = LiquidPrimary,
            iconBg = LiquidPrimary.copy(alpha = if (isDark) 0.25f else 0.12f),
            title = stringResource(R.string.preview_image),
            titleColor = textColor,
            onClick = {
                onEvent(BrowserUiEvent.DismissImageContextMenu)
                onEvent(BrowserUiEvent.PreviewImageInNewTab(imageUrl))
            }
        )
    }
}

@Composable
private fun ImageActionRow(
    icon: ImageVector,
    iconTint: Color,
    iconBg: Color,
    title: String,
    titleColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(LiquidShapes.Card)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(CircleShape)
                .background(iconBg),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = iconTint,
                modifier = Modifier.size(22.dp)
            )
        }

        Spacer(modifier = Modifier.width(16.dp))

        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge.copy(
                fontWeight = FontWeight.Medium,
                fontSize = 16.sp
            ),
            color = titleColor
        )
    }
}

@Preview(name = "Image Context Menu Light", showBackground = true, backgroundColor = 0xFFFFF8F8)
@Composable
fun ImageContextMenuLightPreview() {
    JestkBrowserTheme(themeMode = ThemeMode.LIGHT) {
        ImageContextMenuContent(
            imageUrl = "https://images.unsplash.com/photo-1579783902614-a3fb3927b675?auto=format&fit=crop&w=800&q=80",
            onEvent = {}
        )
    }
}

@Preview(name = "Image Context Menu Dark", showBackground = true, backgroundColor = 0xFF1E1B1C)
@Composable
fun ImageContextMenuDarkPreview() {
    JestkBrowserTheme(themeMode = ThemeMode.DARK) {
        ImageContextMenuContent(
            imageUrl = "https://images.unsplash.com/photo-1579783902614-a3fb3927b675?auto=format&fit=crop&w=800&q=80",
            onEvent = {}
        )
    }
}

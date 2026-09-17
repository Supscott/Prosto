package com.jestk.browser.ui.overlays

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Layers
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jestk.browser.R
import com.jestk.browser.ui.theme.LiquidOnSurface
import com.jestk.browser.ui.theme.LiquidOnSurfaceVariant
import com.jestk.browser.ui.theme.LiquidOutline
import com.jestk.browser.ui.theme.LiquidPrimary
import com.jestk.browser.ui.theme.LiquidSecondary
import com.jestk.browser.ui.theme.LiquidSurface
import com.jestk.browser.ui.theme.LiquidSurfaceContainerHigh
import com.jestk.browser.ui.theme.LiquidTertiary
import com.jestk.browser.ui.theme.LocalIsDarkTheme
import com.jestk.browser.ui.theme.LiquidShapes
import com.jestk.browser.ui.theme.LiquidBorders
import com.jestk.browser.ui.theme.liquidFrostedGlass
import com.jestk.browser.ui.viewmodel.BrowserUiEvent

/**
 * Link Context Menu Bottom Sheet - Liquid Pastel Sunset design system.
 * Triggered on WebView long tap on hyperlinks.
 *
 * Actions:
 * 1. Open in new tab (foreground / active)
 * 2. Open in background (current screen stays on the page)
 * 3. Copy URL to system clipboard
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LinkContextMenuBottomSheet(
    url: String,
    onEvent: (BrowserUiEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    val sheetState = rememberModalBottomSheetState()
    val context = LocalContext.current
    val isDark = LocalIsDarkTheme.current

    val sheetBg = if (isDark) Color(0xFF1E1B1C).copy(alpha = 0.92f) else Color(0xFFFFF8F8).copy(alpha = 0.90f)
    val urlCardBg = if (isDark) Color(0xFF2B2627) else LiquidSurfaceContainerHigh
    val textColor = if (isDark) Color(0xFFF6EFF0) else LiquidOnSurface
    val secondaryTextColor = if (isDark) Color(0xFFC2C7D0) else LiquidOnSurfaceVariant

    ModalBottomSheet(
        onDismissRequest = { onEvent(BrowserUiEvent.DismissLinkContextMenu) },
        sheetState = sheetState,
        shape = LiquidShapes.BottomSheet,
        containerColor = sheetBg,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp)
        ) {
            // URL Header Card
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
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(LiquidPrimary.copy(alpha = if (isDark) 0.25f else 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Language,
                        contentDescription = null,
                        tint = LiquidPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Text(
                    text = url,
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

            // Action 1: Open in new tab (foreground)
            LinkActionRow(
                icon = Icons.AutoMirrored.Filled.OpenInNew,
                iconTint = LiquidPrimary,
                iconBg = LiquidPrimary.copy(alpha = if (isDark) 0.25f else 0.12f),
                title = stringResource(R.string.open_in_new_tab),
                titleColor = textColor,
                onClick = {
                    onEvent(BrowserUiEvent.DismissLinkContextMenu)
                    onEvent(BrowserUiEvent.OpenNewTab(url))
                }
            )

            Spacer(modifier = Modifier.height(6.dp))

            // Action 2: Open in background
            LinkActionRow(
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
                        Toast.LENGTH_SHORT
                    ).show()
                }
            )

            Spacer(modifier = Modifier.height(6.dp))

            // Action 3: Copy URL
            LinkActionRow(
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
                        Toast.LENGTH_SHORT
                    ).show()
                    onEvent(BrowserUiEvent.DismissLinkContextMenu)
                }
            )
        }
    }
}

@Composable
private fun LinkActionRow(
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

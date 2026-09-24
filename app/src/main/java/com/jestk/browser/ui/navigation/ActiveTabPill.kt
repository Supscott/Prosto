package com.jestk.browser.ui.navigation

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.jestk.browser.domain.model.Tab
import com.jestk.browser.engine.FaviconManager
import com.jestk.browser.ui.theme.LocalSereneTypography
import com.jestk.browser.ui.theme.SereneAccent
import com.jestk.browser.ui.theme.SereneBrandPrimary

/**
 * Active tab capsule composable representing the currently focused tab.
 * Adheres to Serene Canvas design system:
 * - Solid crisp surface with cerulean tint (rgba(34, 113, 179, 0.10) in light, rgba(96, 165, 250, 0.15) in dark)
 * - SereneBrandPrimary / SereneAccent bold typography and icons
 * - Micro-border active ring
 * - 14px-16px rounded squircle favicon container
 * - NO BLUR
 * - Tap to edit URL / open search
 * - Long press to trigger contextual actions menu
 * - Close tab button
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ActiveTabPill(
    title: String,
    domain: String,
    url: String,
    faviconUrl: String?,
    isHome: Boolean,
    backgroundColor: Color,
    border: BorderStroke?,
    isDark: Boolean,
    onOpenSearch: () -> Unit,
    onClose: () -> Unit,
    onAddFavorite: (String, String) -> Unit,
    onShare: (String) -> Unit,
    modifier: Modifier = Modifier,
    onSwipeNext: () -> Unit = {},
    onSwipePrev: () -> Unit = {}
) {
    val context = LocalContext.current
    var showMenu by remember { mutableStateOf(false) }
    val typography = LocalSereneTypography.current

    val activeTint = if (isDark) SereneAccent else SereneBrandPrimary
    val resolvedBg = backgroundColor
    val resolvedBorder = border

    Surface(
        shape = RoundedCornerShape(22.dp),
        color = resolvedBg,
        border = resolvedBorder,
        shadowElevation = 0.dp,
        modifier = modifier
            .height(44.dp)
            .clip(RoundedCornerShape(22.dp))
            .combinedClickable(
                onClick = onOpenSearch,
                onLongClick = { showMenu = true }
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 8.dp, end = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // 14px-16px rounded squircle favicon container
            val squircleShape = RoundedCornerShape(6.dp)
            val squircleBg = if (isDark) Color(0xFF1E293B).copy(alpha = 0.60f) else Color.White.copy(alpha = 0.85f)

            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(squircleShape)
                    .background(squircleBg),
                contentAlignment = Alignment.Center
            ) {
                if (isHome) {
                    Icon(
                        imageVector = Icons.Default.Home,
                        contentDescription = "Home",
                        tint = activeTint,
                        modifier = Modifier.size(15.dp)
                    )
                } else if (!faviconUrl.isNullOrEmpty()) {
                    val imageModel = remember(faviconUrl) {
                        FaviconManager.toImageModel(faviconUrl)
                    }
                    if (imageModel != null) {
                        AsyncImage(
                            model = imageModel,
                            contentDescription = title,
                            modifier = Modifier
                                .size(16.dp)
                                .clip(RoundedCornerShape(4.dp)),
                            contentScale = ContentScale.Fit
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = "Secure Connection",
                            tint = activeTint,
                            modifier = Modifier.size(13.dp)
                        )
                    }
                } else {
                    val initial = title.trim().firstOrNull()?.uppercaseChar()
                    if (initial != null && initial.isLetterOrDigit() && title != "Hori Browser" && title != "Hori" && title != "New Tab") {
                        Text(
                            text = initial.toString(),
                            style = typography.labelMd.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp
                            ),
                            color = activeTint
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = "Secure Connection",
                            tint = activeTint,
                            modifier = Modifier.size(13.dp)
                        )
                    }
                }
            }

            // Truncated Title & Domain (if not Home)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = 2.dp),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = title,
                    style = typography.labelMd.copy(
                        fontSize = if (domain.isEmpty()) 12.5.sp else 11.5.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    color = activeTint,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (domain.isNotEmpty()) {
                    Text(
                        text = domain,
                        style = typography.bodySm.copy(
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.Normal
                        ),
                        color = if (isDark) SereneAccent.copy(alpha = 0.70f) else SereneBrandPrimary.copy(alpha = 0.70f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Close Tab Action Glyph
            IconButton(
                onClick = onClose,
                modifier = Modifier.size(24.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Close tab",
                    tint = activeTint.copy(alpha = 0.75f),
                    modifier = Modifier.size(14.dp)
                )
            }
        }

        TabContextMenu(
            expanded = showMenu,
            onDismissRequest = { showMenu = false },
            isDark = isDark,
            onCloseTab = onClose,
            onCopyUrl = {
                copyUrlToClipboard(context, url)
            },
            onAddFavorite = {
                if (url.isNotBlank() && !url.startsWith("about:")) {
                    onAddFavorite(title, url)
                    Toast.makeText(context, "Added to Favorites", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "Cannot add Home to Favorites", Toast.LENGTH_SHORT).show()
                }
            },
            onShareLink = { onShare(url) }
        )
    }
}

/**
 * Convenience overload accepting [Tab] domain model.
 */
@Composable
fun ActiveTabPill(
    tab: Tab,
    displayTitle: String,
    displayDomain: String,
    isHome: Boolean,
    backgroundColor: Color,
    border: BorderStroke?,
    isDark: Boolean,
    onOpenSearch: () -> Unit,
    onClose: () -> Unit,
    onAddFavorite: (String, String) -> Unit,
    onShare: (String) -> Unit,
    modifier: Modifier = Modifier,
    onSwipeNext: () -> Unit = {},
    onSwipePrev: () -> Unit = {}
) {
    ActiveTabPill(
        title = displayTitle,
        domain = displayDomain,
        url = tab.url,
        faviconUrl = tab.faviconUrl,
        isHome = isHome,
        backgroundColor = backgroundColor,
        border = border,
        isDark = isDark,
        onOpenSearch = onOpenSearch,
        onClose = onClose,
        onAddFavorite = onAddFavorite,
        onShare = onShare,
        modifier = modifier,
        onSwipeNext = onSwipeNext,
        onSwipePrev = onSwipePrev
    )
}

/**
 * Backward compatibility alias for [ActiveTabPill].
 */
@Composable
fun ActiveTabCenterCapsule(
    title: String,
    domain: String,
    url: String,
    faviconUrl: String?,
    isHome: Boolean,
    backgroundColor: Color,
    border: BorderStroke?,
    isDark: Boolean,
    onOpenSearch: () -> Unit,
    onClose: () -> Unit,
    onAddFavorite: (String, String) -> Unit,
    onShare: (String) -> Unit,
    modifier: Modifier = Modifier,
    onSwipeNext: () -> Unit = {},
    onSwipePrev: () -> Unit = {}
) {
    ActiveTabPill(
        title = title,
        domain = domain,
        url = url,
        faviconUrl = faviconUrl,
        isHome = isHome,
        backgroundColor = backgroundColor,
        border = border,
        isDark = isDark,
        onOpenSearch = onOpenSearch,
        onClose = onClose,
        onAddFavorite = onAddFavorite,
        onShare = onShare,
        modifier = modifier,
        onSwipeNext = onSwipeNext,
        onSwipePrev = onSwipePrev
    )
}

internal fun copyUrlToClipboard(context: Context, url: String) {
    if (url.isNotBlank() && !url.startsWith("about:")) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("URL", url))
        Toast.makeText(context, "URL copied to clipboard", Toast.LENGTH_SHORT).show()
    } else {
        Toast.makeText(context, "No URL to copy", Toast.LENGTH_SHORT).show()
    }
}

internal fun shareWebpageUrl(context: Context, urlToShare: String) {
    if (urlToShare.isNotBlank() && !urlToShare.startsWith("about:")) {
        try {
            val sendIntent = Intent().apply {
                action = Intent.ACTION_SEND
                putExtra(Intent.EXTRA_TEXT, urlToShare)
                type = "text/plain"
            }
            val shareIntent = Intent.createChooser(sendIntent, "Share Webpage")
            context.startActivity(shareIntent)
        } catch (_: Exception) {
            Toast.makeText(context, "Could not share link", Toast.LENGTH_SHORT).show()
        }
    } else {
        Toast.makeText(context, "No URL to share", Toast.LENGTH_SHORT).show()
    }
}

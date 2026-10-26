package com.jestk.browser.ui.navigation

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Language
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jestk.browser.domain.model.Tab
import com.jestk.browser.ui.components.SiteFavicon
import com.jestk.browser.ui.theme.DarkError
import com.jestk.browser.ui.theme.DarkOnSurfaceVariant
import com.jestk.browser.ui.theme.DarkTextHighContrast
import com.jestk.browser.ui.theme.LocalProstoTypography
import com.jestk.browser.ui.theme.ProstoAccent
import com.jestk.browser.ui.theme.ProstoBrandPrimary
import com.jestk.browser.ui.theme.ProstoDarkText
import com.jestk.browser.ui.theme.ProstoError
import com.jestk.browser.ui.theme.ProstoMutedSlate
import com.jestk.browser.util.ClipboardHelper

/**
 * Active tab capsule composable representing the currently focused tab.
 * Adheres to Prosto Canvas design system:
 * - Solid crisp surface (#FFFFFF in light, #0D1824 in dark) with light elevation shadow (2.dp) and no outline.
 * - In-capsule loading progress fill (100% height, animatedProgress width, 20% opacity ProstoBrandPrimary).
 * - Bare favicon without background box.
 * - Bold title typography in high-contrast text color (#0F172A in light, #F8FAFC in dark).
 * - Secondary domain subtitle (#64748B in light, #94A3B8 in dark).
 * - Tap to edit URL / open search.
 * - Long press to initiate horizontal drag reordering across tabs when moved > touchSlop (8dp);
 *   or opens contextual actions menu if released without moving.
 * - Close tab button.
 */
@Composable
fun ActiveTabPill(
    title: String,
    domain: String,
    url: String,
    faviconUrl: String?,
    isHome: Boolean,
    backgroundColor: Color,
    border: BorderStroke? = null,
    isDark: Boolean,
    isLoading: Boolean = false,
    loadingProgress: Float = 0f,
    onOpenSearch: () -> Unit,
    onClose: () -> Unit,
    onAddFavorite: (String, String) -> Unit,
    onShare: (String) -> Unit,
    modifier: Modifier = Modifier,
    onSwipeNext: () -> Unit = {},
    onSwipePrev: () -> Unit = {},
    menuExpanded: Boolean = false,
    onMenuDismiss: () -> Unit = {},
    isCompact: Boolean = false,
    isRemovePending: Boolean = false,
    removeProgress: Float = 0f,
) {
    val context = LocalContext.current
    val typography = LocalProstoTypography.current

    val titleColor = if (isDark) DarkTextHighContrast else ProstoDarkText
    val domainColor = if (isDark) DarkOnSurfaceVariant else ProstoMutedSlate
    val iconTint = if (isDark) ProstoAccent else ProstoBrandPrimary
    val closeTint = if (isDark) DarkOnSurfaceVariant else ProstoMutedSlate
    val resolvedBg = backgroundColor
    val resolvedBorder = border
    val pillHeight by animateDpAsState(
        targetValue = if (isCompact) 38.dp else 42.dp,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium,
        ),
        label = "ActiveTabDragHeight",
    )

    val animatedProgress by animateFloatAsState(
        targetValue = if (isLoading) loadingProgress.coerceIn(0f, 1f) else 0f,
        animationSpec = tween(durationMillis = 200, easing = LinearOutSlowInEasing),
        label = "TabLoadingProgress",
    )

    Surface(
        shape = RoundedCornerShape(21.dp),
        color = resolvedBg,
        border = resolvedBorder,
        shadowElevation = 0.dp,
        modifier =
            modifier
                .height(pillHeight)
                .shadow(
                    elevation = 2.dp,
                    shape = RoundedCornerShape(21.dp),
                    clip = false,
                    ambientColor = if (isDark) Color.Black else Color.Black.copy(alpha = 0.60f),
                    spotColor = Color.Black,
                ),
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(21.dp)),
        ) {
            // Page Loading Progress Fill (height: 100%, 20% opacity ProstoBrandPrimary)
            AnimatedVisibility(
                visible = isLoading && animatedProgress > 0f,
                enter = fadeIn(animationSpec = tween(150)),
                exit = fadeOut(animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing)),
                modifier = Modifier.matchParentSize(),
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    Box(
                        modifier =
                            Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(fraction = animatedProgress)
                                .background(ProstoBrandPrimary.copy(alpha = 0.20f)),
                    )
                }
            }

            // Pill Content Row
            AnimatedVisibility(
                visible = !isCompact,
                enter = fadeIn(animationSpec = tween(120)),
                exit = fadeOut(animationSpec = tween(80)),
                modifier = Modifier.fillMaxSize(),
            ) {
                Row(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .clickable(onClick = onOpenSearch)
                            .padding(start = 10.dp, end = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                // Bare favicon container (no background box)
                if (isHome) {
                    Box(
                        modifier = Modifier.size(18.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Default.Home,
                            contentDescription = "Home",
                            tint = iconTint,
                            modifier = Modifier.size(17.dp),
                        )
                    }
                } else {
                    SiteFavicon(
                        faviconUrl = faviconUrl,
                        imageSize = 18.dp,
                        placeholder = {
                            val initial = title.trim().firstOrNull()?.uppercaseChar()
                            if (initial != null && initial.isLetterOrDigit() && title != "Prosto Browser" && title != "Prosto" &&
                                title != "New Tab"
                            ) {
                                Text(
                                    text = initial.toString(),
                                    style =
                                        typography.labelMd.copy(
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp,
                                        ),
                                    color = iconTint,
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Lock,
                                    contentDescription = "Secure Connection",
                                    tint = iconTint,
                                    modifier = Modifier.size(15.dp),
                                )
                            }
                        },
                    )
                }

                // Truncated Title (Bold) & Domain (if not Home)
                Column(
                    modifier =
                        Modifier
                            .weight(1f)
                            .padding(vertical = 2.dp),
                    verticalArrangement = Arrangement.Center,
                ) {
                    FadingSingleLineText(
                        text = title,
                        style =
                            typography.labelMd.copy(
                                fontSize = if (domain.isEmpty()) 12.5.sp else 11.5.sp,
                                fontWeight = FontWeight.Bold,
                            ),
                        color = titleColor,
                    )
                    if (domain.isNotEmpty()) {
                        FadingSingleLineText(
                            text = domain,
                            style =
                                typography.bodySm.copy(
                                    fontSize = 9.5.sp,
                                    fontWeight = FontWeight.Normal,
                                ),
                            color = domainColor,
                        )
                    }
                }

                // Close Tab Action Glyph
                IconButton(
                    onClick = onClose,
                    modifier = Modifier.size(22.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close tab",
                        tint = closeTint,
                        modifier = Modifier.size(13.dp),
                    )
                }
                }
            }

            val crossAlpha = if (isRemovePending) 1f else ((removeProgress - 0.15f) / 0.70f).coerceIn(0f, 1f)

            AnimatedVisibility(
                visible = isCompact,
                enter = fadeIn(animationSpec = tween(100)),
                exit = fadeOut(animationSpec = tween(80)),
                modifier = Modifier.align(Alignment.Center),
            ) {
                Box(
                    modifier = Modifier.size(24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    if (crossAlpha > 0f) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Remove Tab",
                            tint = closeTint,
                            modifier = Modifier
                                .size(16.dp)
                                .graphicsLayer { alpha = crossAlpha },
                        )
                    }
                    if (crossAlpha < 1f) {
                        Box(
                            modifier = Modifier.graphicsLayer { alpha = 1f - crossAlpha },
                            contentAlignment = Alignment.Center,
                        ) {
                            if (isHome) {
                                Icon(
                                    imageVector = Icons.Default.Home,
                                    contentDescription = "Home",
                                    tint = iconTint,
                                    modifier = Modifier.size(18.dp),
                                )
                            } else {
                                SiteFavicon(
                                    faviconUrl = faviconUrl,
                                    imageSize = 18.dp,
                                    placeholder = {
                                        Icon(
                                            imageVector = Icons.Default.Language,
                                            contentDescription = title,
                                            tint = iconTint,
                                            modifier = Modifier.size(18.dp),
                                        )
                                    },
                                )
                            }
                        }
                    }
                }
            }

            TabContextMenu(
                expanded = menuExpanded,
                onDismissRequest = onMenuDismiss,
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
                onShareLink = { onShare(url) },
            )
        }
    }
}

@Composable
private fun FadingSingleLineText(
    text: String,
    style: TextStyle,
    color: Color,
) {
    var overflows by remember(text) { mutableStateOf(false) }
    val textStyle =
        if (overflows) {
            style.copy(
                brush =
                    Brush.horizontalGradient(
                        colorStops =
                            arrayOf(
                                0f to color,
                                0.8f to color,
                                1f to color.copy(alpha = 0f),
                            ),
                    ),
            )
        } else {
            style.copy(color = color)
        }

    Text(
        text = text,
        style = textStyle,
        maxLines = 1,
        overflow = TextOverflow.Clip,
        onTextLayout = { overflows = it.hasVisualOverflow },
    )
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
    border: BorderStroke? = null,
    isDark: Boolean,
    isLoading: Boolean = false,
    loadingProgress: Float = 0f,
    onOpenSearch: () -> Unit,
    onClose: () -> Unit,
    onAddFavorite: (String, String) -> Unit,
    onShare: (String) -> Unit,
    modifier: Modifier = Modifier,
    onSwipeNext: () -> Unit = {},
    onSwipePrev: () -> Unit = {},
    menuExpanded: Boolean = false,
    onMenuDismiss: () -> Unit = {},
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
        isLoading = isLoading,
        loadingProgress = loadingProgress,
        onOpenSearch = onOpenSearch,
        onClose = onClose,
        onAddFavorite = onAddFavorite,
        onShare = onShare,
        modifier = modifier,
        onSwipeNext = onSwipeNext,
        onSwipePrev = onSwipePrev,
        menuExpanded = menuExpanded,
        onMenuDismiss = onMenuDismiss,
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
    border: BorderStroke? = null,
    isDark: Boolean,
    isLoading: Boolean = false,
    loadingProgress: Float = 0f,
    onOpenSearch: () -> Unit,
    onClose: () -> Unit,
    onAddFavorite: (String, String) -> Unit,
    onShare: (String) -> Unit,
    modifier: Modifier = Modifier,
    onSwipeNext: () -> Unit = {},
    onSwipePrev: () -> Unit = {},
    menuExpanded: Boolean = false,
    onMenuDismiss: () -> Unit = {},
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
        isLoading = isLoading,
        loadingProgress = loadingProgress,
        onOpenSearch = onOpenSearch,
        onClose = onClose,
        onAddFavorite = onAddFavorite,
        onShare = onShare,
        modifier = modifier,
        onSwipeNext = onSwipeNext,
        onSwipePrev = onSwipePrev,
        menuExpanded = menuExpanded,
        onMenuDismiss = onMenuDismiss,
    )
}

internal fun copyUrlToClipboard(
    context: Context,
    url: String,
) {
    if (url.isNotBlank() && !url.startsWith("about:")) {
        ClipboardHelper.copyToClipboard(context, url)
    } else {
        Toast.makeText(context, "No URL to copy", Toast.LENGTH_SHORT).show()
    }
}

internal fun shareWebpageUrl(
    context: Context,
    urlToShare: String,
) {
    if (urlToShare.isNotBlank() && !urlToShare.startsWith("about:")) {
        try {
            val sendIntent =
                Intent().apply {
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

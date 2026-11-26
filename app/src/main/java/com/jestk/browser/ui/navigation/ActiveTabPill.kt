package com.jestk.browser.ui.navigation

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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jestk.browser.R
import com.jestk.browser.domain.model.Tab
import com.jestk.browser.ui.components.SiteFavicon
import com.jestk.browser.ui.theme.DarkOnSurfaceVariant
import com.jestk.browser.ui.theme.DarkTextHighContrast
import com.jestk.browser.ui.theme.IncognitoAccent
import com.jestk.browser.ui.theme.LocalProstoTypography
import com.jestk.browser.ui.theme.ProstoAccent
import com.jestk.browser.ui.theme.ProstoBrandPrimary
import com.jestk.browser.ui.theme.ProstoDarkText
import com.jestk.browser.ui.theme.ProstoMutedSlate

/**
 * Layout metrics and typography tokens for [ActiveTabPill].
 */
object ActiveTabPillDefaults {
    val MinWidth = 80.dp
    // Internal fixed overhead: start pad (10dp) + favicon (18dp) + spacing (8dp) + text-to-close spacing (4dp) + close button (22dp) + end pad (6dp) = 68dp
    val ContentOverhead = 68.dp
    val TitleFontSizeStandard = 12.5.sp
    val TitleFontSizeWithDomain = 11.5.sp
    val DomainFontSize = 9.5.sp
    // Fallback char width heuristics when platform TextMeasurer runs headless/unmeasured
    val FallbackTitleCharWidthSp = 7.5.sp
    val FallbackDomainCharWidthSp = 6.5.sp
}

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
    isDark: Boolean,
    onOpenSearch: () -> Unit,
    onClose: () -> Unit,
    onShare: (String) -> Unit,
    modifier: Modifier = Modifier,
    siteActions: TabMenuSiteActions = TabMenuSiteActions.None,
    border: BorderStroke? = null,
    isLoading: Boolean = false,
    loadingProgress: Float = 0f,
    menuExpanded: Boolean = false,
    onMenuDismiss: () -> Unit = {},
    isCompact: Boolean = false,
    isRemovePending: Boolean = false,
    removeProgress: Float = 0f,
    folderId: String? = null,
    availableFolders: List<com.jestk.browser.domain.model.TabFolder> = emptyList(),
    onCreateFolder: (() -> Unit)? = null,
    onAddToFolder: ((String) -> Unit)? = null,
    onRemoveFromFolder: (() -> Unit)? = null,
    availableWorkspaces: List<com.jestk.browser.domain.model.Workspace> = emptyList(),
    onMoveToWorkspace: ((String) -> Unit)? = null,
    isIncognito: Boolean = false,
) {
    val context = LocalContext.current
    val typography = LocalProstoTypography.current

    val titleColor = if (isDark) DarkTextHighContrast else ProstoDarkText
    val domainColor = if (isDark) DarkOnSurfaceVariant else ProstoMutedSlate
    val iconTint = if (isIncognito) IncognitoAccent else if (isDark) ProstoAccent else ProstoBrandPrimary
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
                )
                .incognitoPillMarks(isIncognito, 21.dp),
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
                ) {
                // Bare favicon container (no background box)
                if (isHome) {
                    Box(
                        modifier = Modifier.size(18.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = if (isIncognito) Icons.Outlined.VisibilityOff else Icons.Default.Home,
                            contentDescription = stringResource(if (isIncognito) R.string.tab_incognito_indicator else R.string.tab_home_title),
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
                            if (isIncognito) {
                                Icon(
                                    imageVector = Icons.Outlined.VisibilityOff,
                                    contentDescription = stringResource(R.string.tab_incognito_indicator),
                                    tint = iconTint,
                                    modifier = Modifier.size(16.dp),
                                )
                            } else if (initial != null && initial.isLetterOrDigit() && title != stringResource(R.string.app_name)) {
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
                                    contentDescription = stringResource(R.string.a11y_secure_connection),
                                    tint = iconTint,
                                    modifier = Modifier.size(15.dp),
                                )
                            }
                        },
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

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
                                fontSize = if (domain.isEmpty()) ActiveTabPillDefaults.TitleFontSizeStandard else ActiveTabPillDefaults.TitleFontSizeWithDomain,
                                fontWeight = FontWeight.Bold,
                            ),
                        color = titleColor,
                    )
                    if (domain.isNotEmpty()) {
                        FadingSingleLineText(
                            text = domain,
                            style =
                                typography.bodySm.copy(
                                    fontSize = ActiveTabPillDefaults.DomainFontSize,
                                    fontWeight = FontWeight.Normal,
                                ),
                            color = domainColor,
                        )
                    }
                }

                Spacer(modifier = Modifier.width(4.dp))

                // Close Tab Action Glyph (exact 22.dp with 4.dp text margin and 6.dp end pad)
                Box(
                    modifier =
                        Modifier
                            .size(22.dp)
                            .clip(CircleShape)
                            .clickable(
                                onClick = onClose,
                                role = Role.Button,
                                interactionSource = remember { MutableInteractionSource() },
                                indication = ripple(bounded = true, radius = 11.dp),
                            ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = stringResource(R.string.a11y_close_tab),
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
                            contentDescription = stringResource(R.string.a11y_remove_tab),
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
                                    imageVector = if (isIncognito) Icons.Outlined.VisibilityOff else Icons.Default.Home,
                                    contentDescription = stringResource(if (isIncognito) R.string.tab_incognito_indicator else R.string.tab_home_title),
                                    tint = iconTint,
                                    modifier = Modifier.size(18.dp),
                                )
                            } else {
                                SiteFavicon(
                                    faviconUrl = faviconUrl,
                                    imageSize = 18.dp,
                                    placeholder = {
                                        Icon(
                                            imageVector = if (isIncognito) Icons.Outlined.VisibilityOff else Icons.Default.Language,
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
                isFavorite = siteActions.isFavorite,
                onToggleFavorite = siteActions.onToggleFavorite,
                isSiteAllowlisted = siteActions.isSiteAllowlisted,
                onToggleBlocking = siteActions.onToggleBlocking,
                onShareLink = { onShare(url) },
                onReload = siteActions.onReload,
                folderId = folderId,
                availableFolders = availableFolders,
                onCreateFolder = onCreateFolder,
                onAddToFolder = onAddToFolder,
                onRemoveFromFolder = onRemoveFromFolder,
                availableWorkspaces = availableWorkspaces,
                onMoveToWorkspace = onMoveToWorkspace,
            )
        }
    }
}

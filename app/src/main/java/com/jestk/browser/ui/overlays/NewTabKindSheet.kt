package com.jestk.browser.ui.overlays

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import androidx.webkit.WebViewFeature
import com.jestk.browser.R
import com.jestk.browser.ui.navigation.DockShadowUnderClipShape
import com.jestk.browser.ui.navigation.PopupMenuItem
import com.jestk.browser.ui.theme.DarkSurface
import com.jestk.browser.ui.theme.ProstoBorders
import com.jestk.browser.ui.theme.ProstoBrandPrimary
import com.jestk.browser.ui.theme.ProstoCardSurface
import com.jestk.browser.ui.theme.ProstoShapes
import com.jestk.browser.ui.viewmodel.BrowserUiEvent
import com.jestk.browser.ui.viewmodel.OpenIncognitoTab

/** Incognito tabs need WebView profiles; without them the "+" long press does nothing extra. */
internal var isIncognitoSupported: () -> Boolean = {
    runCatching { WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE) }.getOrDefault(false)
}

/**
 * Owns the new-tab choice menu for a "+" button. [content] receives the long-press handler to
 * give the button (null when incognito is unavailable, so the long press does nothing).
 * [onChosen] runs after either choice, e.g. to close the tab grid. The menu anchors to the box
 * around [content], so it opens right above (or below) the button.
 */
@Composable
fun NewTabKindHost(
    isDark: Boolean,
    onEvent: (BrowserUiEvent) -> Unit,
    onChosen: () -> Unit = {},
    aboveDock: Boolean = true,
    modifier: Modifier = Modifier,
    content: @Composable (onLongClick: (() -> Unit)?) -> Unit,
) {
    var showMenu by remember { mutableStateOf(false) }
    val supported = remember { isIncognitoSupported() }
    // Placement modifiers (e.g. align in the dock) go on [modifier]: [content] sits inside this box.
    Box(modifier = modifier) {
        content(if (supported) ({ showMenu = true }) else null)
        if (showMenu) {
            NewTabKindMenu(
                isDark = isDark,
                aboveDock = aboveDock,
                onIncognito = {
                    showMenu = false
                    onEvent(OpenIncognitoTab)
                    onChosen()
                },
                onRegular = {
                    showMenu = false
                    onEvent(BrowserUiEvent.OpenNewTab(""))
                    onChosen()
                },
                onDismiss = { showMenu = false },
            )
        }
    }
}

/** The solid round "+" of the tab grid header; a long press opens the new-tab choice. */
@Composable
fun GridNewTabButton(
    onClick: () -> Unit,
    onLongClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(ProstoBrandPrimary)
                .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Default.Add,
            contentDescription = stringResource(R.string.action_new_tab),
            tint = Color.White,
            modifier = Modifier.size(22.dp),
        )
    }
}

/**
 * The new-tab choice as a floating card like the dock's Menu popup ([PopupMenuItem] rows on a
 * Card surface). [aboveDock]: the card sits above the dock's "+" and its shadow is clipped
 * [DOCK_GAP] below the card, so it falls under the dock (as in TabContextMenu); otherwise (the
 * tab grid header) it opens below the button with its full shadow.
 */
@Composable
fun NewTabKindMenu(
    isDark: Boolean,
    aboveDock: Boolean,
    onIncognito: () -> Unit,
    onRegular: () -> Unit,
    onDismiss: () -> Unit,
) {
    BackHandler(onBack = onDismiss)
    val density = LocalDensity.current
    val positionProvider = remember(density, aboveDock) { NewTabMenuPosition(density, aboveDock) }
    Popup(
        onDismissRequest = onDismiss,
        popupPositionProvider = positionProvider,
        properties = PopupProperties(focusable = true, dismissOnClickOutside = true, dismissOnBackPress = true),
    ) {
        val card = @Composable {
            NewTabKindSheetContent(isDark = isDark, onIncognito = onIncognito, onRegular = onRegular)
        }
        if (!aboveDock) {
            card()
            return@Popup
        }
        val dockGapPx = with(density) { DOCK_GAP.toPx() }
        val overflowPx = with(density) { SHADOW_OVERFLOW.toPx() }
        Box(
            modifier =
                Modifier
                    .graphicsLayer {
                        shape = DockShadowUnderClipShape(dockGapPx, overflowPx)
                        clip = true
                    }
                    .drawWithContent {
                        clipRect(
                            left = -overflowPx,
                            top = -overflowPx,
                            right = size.width + overflowPx,
                            bottom = size.height + dockGapPx,
                        ) {
                            this@drawWithContent.drawContent()
                        }
                    },
        ) {
            card()
        }
    }
}

// The same clip as TabContextMenu: the shadow may spread 32dp up and sideways but stops 10dp
// below the card, at the dock's top edge.
private val DOCK_GAP = 10.dp
private val SHADOW_OVERFLOW = 32.dp

/** The menu card, apart from the popup window so previews can render it. */
@Composable
internal fun NewTabKindSheetContent(
    isDark: Boolean,
    onIncognito: () -> Unit,
    onRegular: () -> Unit,
) {
    val dividerColor = if (isDark) Color(0xFF334155) else Color(0xFFF1F5F9)
    Surface(
        shape = ProstoShapes.Card,
        color = if (isDark) DarkSurface else ProstoCardSurface,
        shadowElevation = 12.dp,
        border = ProstoBorders.floatingBorder(isDark),
        modifier = Modifier.width(200.dp).testTag("new_tab_kind_sheet"),
    ) {
        Column(
            modifier = Modifier.padding(4.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            PopupMenuItem(
                icon = Icons.Outlined.VisibilityOff,
                label = stringResource(R.string.new_tab_incognito),
                isActive = false,
                isDark = isDark,
                onClick = onIncognito,
                modifier = Modifier.testTag("new_tab_kind_incognito"),
            )
            HorizontalDivider(color = dividerColor, thickness = 1.dp)
            PopupMenuItem(
                icon = Icons.Default.Add,
                label = stringResource(R.string.new_tab_regular),
                isActive = false,
                isDark = isDark,
                onClick = onRegular,
                modifier = Modifier.testTag("new_tab_kind_regular"),
            )
        }
    }
}

/**
 * Centres the card on the anchor. [above]: its bottom is `gap` above the anchor's top, like the
 * tab context menu above the dock; otherwise, or when that would leave the window, it goes `gap`
 * below the anchor.
 */
private class NewTabMenuPosition(private val density: Density, private val above: Boolean) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val margin = with(density) { 12.dp.roundToPx() }
        val gap = with(density) { 16.dp.roundToPx() }
        val x = (anchorBounds.left + (anchorBounds.width - popupContentSize.width) / 2)
            .coerceIn(margin, (windowSize.width - popupContentSize.width - margin).coerceAtLeast(margin))
        val aboveY = anchorBounds.top - gap - popupContentSize.height
        val y = if (above && aboveY >= margin) aboveY else anchorBounds.bottom + gap
        return IntOffset(x, y)
    }
}

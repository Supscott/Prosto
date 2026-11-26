package com.jestk.browser.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.Dp
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.gestures.detectTapGestures
import com.jestk.browser.ui.theme.DarkOnSurface
import com.jestk.browser.ui.theme.DarkOnSurfaceVariant
import com.jestk.browser.ui.theme.DarkSurface
import com.jestk.browser.ui.theme.DarkSurfaceVariant
import com.jestk.browser.ui.theme.LocalProstoTypography
import com.jestk.browser.ui.theme.ProstoBrandPrimary
import com.jestk.browser.ui.theme.ProstoCardSurface
import com.jestk.browser.ui.theme.ProstoDarkText
import com.jestk.browser.ui.theme.ProstoMutedSlate
import com.jestk.browser.ui.theme.ProstoShapes
import com.jestk.browser.ui.theme.prostoFloatingSurface

/**
 * One button of a [DockPromptCard]. A [primary] action is a filled button; the others are text
 * buttons. [color] overrides the primary fill (destructive actions) or the secondary label colour.
 */
data class DockPromptAction(
    val label: String,
    val onClick: () -> Unit,
    val primary: Boolean = false,
    val color: Color? = null,
    val testTag: String? = null,
    val enabled: Boolean = true,
)

/** Previews switch the enter animation off so a still frame shows the finished card. */
val LocalDockPromptAnimateEnter = staticCompositionLocalOf { true }

/** Previews set this to simulate an open keyboard (bottom inset of the IME, including the nav bar). */
val LocalDockPromptImeBottom = staticCompositionLocalOf<Dp?> { null }

/** Gap between the top edge of the keyboard and the card while the keyboard is open. */
private val CardAboveImeGap = 8.dp

/** Distance from the bottom navigation bar inset to the card: the same as the Menu card above the dock. */
private val CardBottomOffset = 64.dp

/** Top edge of the dock above the navigation bar inset (BottomBar: 54.dp row + 2.dp bottom padding). */
val DockTopAboveNavBar = 56.dp

/**
 * In-window confirmation card shown directly above the dock (not a Dialog / Popup window, so the
 * area behind it can get a blur later). It fills its parent: a light dim consumes every touch so the
 * page below cannot be used, a tap on the dim or system Back calls [onDismiss].
 *
 * Surface and enter animation mirror [com.jestk.browser.ui.navigation.ActionPopupMenu]. Put it
 * in a Box that spans the screen. Actions wrap onto further lines when they do not fit.
 */
@Composable
fun DockPromptCard(
    title: String,
    isDark: Boolean,
    onDismiss: () -> Unit,
    actions: List<DockPromptAction>,
    modifier: Modifier = Modifier,
    message: String? = null,
    content: (@Composable ColumnScope.() -> Unit)? = null,
    undimmedBottom: Dp = DockTopAboveNavBar,
) {
    val navBarBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    // The IME inset animates with the keyboard, so the card follows it frame by frame.
    val imeBottom = LocalDockPromptImeBottom.current ?: WindowInsets.ime.asPaddingValues().calculateBottomPadding()
    val cardBottom = maxOf(navBarBottom + CardBottomOffset, imeBottom + CardAboveImeGap)
    val animateEnter = LocalDockPromptAnimateEnter.current
    val typography = LocalProstoTypography.current
    val cardBg = if (isDark) DarkSurface else ProstoCardSurface
    val textColor = if (isDark) DarkOnSurface else ProstoDarkText
    val visibleState = remember { MutableTransitionState(!animateEnter).apply { targetState = true } }

    BackHandler(onBack = onDismiss)

    Box(
        modifier = modifier
            .fillMaxSize()
            .drawBehind {
                // Dim the page only: the dock strip stays clear, but a tap on it still dismisses.
                // The keyboard covers the strip as it rises, so the clear part shrinks with it.
                val clearBottom = (navBarBottom.toPx() + undimmedBottom.toPx() - imeBottom.toPx()).coerceAtLeast(0f)
                drawRect(Color.Black.copy(alpha = 0.32f), size = Size(size.width, (size.height - clearBottom).coerceAtLeast(0f)))
            }
            .pointerInput(onDismiss) { detectTapGestures(onTap = { onDismiss() }) },
        contentAlignment = Alignment.BottomCenter,
    ) {
        AnimatedVisibility(
            visibleState = visibleState,
            enter =
                fadeIn(animationSpec = tween(180, easing = FastOutSlowInEasing)) +
                    scaleIn(initialScale = 0.90f, animationSpec = tween(200, easing = FastOutSlowInEasing)) +
                    slideInVertically(animationSpec = tween(220, easing = FastOutSlowInEasing)) { it / 3 },
            exit = androidx.compose.animation.ExitTransition.None,
            modifier = Modifier
                .statusBarsPadding()
                .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = cardBottom),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .prostoFloatingSurface(
                        shape = ProstoShapes.Card,
                        isDark = isDark,
                        backgroundColor = cardBg,
                        elevation = 12.dp,
                    )
                    .pointerInput(Unit) { detectTapGestures { } }
                    .semantics { paneTitle = title }
                    .padding(20.dp),
            ) {
                Text(
                    text = title,
                    style = typography.headlineSm,
                    color = textColor,
                    fontWeight = FontWeight.Bold,
                )
                if (message != null) {
                    Text(
                        text = message,
                        style = typography.bodyMd,
                        color = if (isDark) DarkOnSurfaceVariant else ProstoMutedSlate,
                        modifier = Modifier.padding(top = 10.dp),
                    )
                }
                if (content != null) {
                    Column(
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .padding(top = 12.dp)
                            .verticalScroll(rememberScrollState()),
                        content = content,
                    )
                }
                // A card whose content draws its own buttons passes no actions and gets no gap.
                if (actions.isNotEmpty()) {
                    PromptActions(
                        modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                    ) {
                        actions.sortedBy { it.primary }.forEach { action -> PromptButton(action, isDark) }
                    }
                }
            }
        }
    }
}

/**
 * Lays the buttons out in one row aligned to the end when they all fit; otherwise stacks them at
 * full width with the last child (the primary action) on top and the rest below it in order.
 */
@Composable
private fun PromptActions(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Layout(content = content, modifier = modifier) { measurables, constraints ->
        val gap = 8.dp.roundToPx()
        val rowWidth = measurables.sumOf { it.maxIntrinsicWidth(constraints.maxHeight) } + gap * (measurables.size - 1).coerceAtLeast(0)
        if (rowWidth <= constraints.maxWidth) {
            val placeables = measurables.map { it.measure(constraints.copy(minWidth = 0)) }
            val height = placeables.maxOfOrNull { it.height } ?: 0
            layout(constraints.maxWidth, height) {
                var x = constraints.maxWidth - rowWidth
                placeables.forEach { p ->
                    p.placeRelative(x, (height - p.height) / 2)
                    x += p.width + gap
                }
            }
        } else {
            val full = constraints.copy(minWidth = constraints.maxWidth)
            val measured = measurables.map { it.measure(full) }
            // The primary action is the last child: it goes on top, the others keep their order.
            val placeables = measured.takeLast(1) + measured.dropLast(1)
            val height = placeables.sumOf { it.height } + gap / 2 * (placeables.size - 1)
            layout(constraints.maxWidth, height) {
                var y = 0
                placeables.forEach { p ->
                    p.placeRelative(0, y)
                    y += p.height + gap / 2
                }
            }
        }
    }
}

@Composable
private fun PromptButton(action: DockPromptAction, isDark: Boolean) {
    val typography = LocalProstoTypography.current
    val tag = if (action.testTag != null) Modifier.testTag(action.testTag) else Modifier
    if (action.primary) {
        Button(
            onClick = action.onClick,
            enabled = action.enabled,
            shape = ProstoShapes.Pill,
            colors = ButtonDefaults.buttonColors(
                containerColor = action.color ?: ProstoBrandPrimary,
                contentColor = Color.White,
                disabledContainerColor = if (isDark) DarkSurfaceVariant else Color(0xFFE2E8F0),
                disabledContentColor = if (isDark) DarkOnSurfaceVariant else ProstoMutedSlate,
            ),
            modifier = tag,
        ) {
            Text(text = action.label, style = typography.labelMd, fontWeight = FontWeight.SemiBold)
        }
    } else {
        TextButton(onClick = action.onClick, enabled = action.enabled, modifier = tag) {
            Text(
                text = action.label,
                style = typography.labelMd,
                color = action.color ?: if (isDark) DarkOnSurfaceVariant else ProstoMutedSlate,
            )
        }
    }
}

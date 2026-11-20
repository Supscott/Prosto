package com.jestk.browser.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.gestures.detectTapGestures
import com.jestk.browser.ui.theme.DarkOnSurface
import com.jestk.browser.ui.theme.DarkSurface
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
)

/** Previews switch the enter animation off so a still frame shows the finished card. */
val LocalDockPromptAnimateEnter = staticCompositionLocalOf { true }

/** Distance from the bottom navigation bar inset to the card: the same as the Menu card above the dock. */
private val CardBottomOffset = 64.dp

/**
 * In-window confirmation card shown directly above the dock (not a Dialog / Popup window, so the
 * area behind it can get a blur later). It fills its parent: a light dim consumes every touch so the
 * page below cannot be used, a tap on the dim or system Back calls [onDismiss].
 *
 * Surface and enter animation mirror [com.jestk.browser.ui.navigation.ActionPopupMenu]. Put it
 * in a Box that spans the screen. Actions wrap onto further lines when they do not fit.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DockPromptCard(
    title: String,
    isDark: Boolean,
    onDismiss: () -> Unit,
    actions: List<DockPromptAction>,
    modifier: Modifier = Modifier,
    message: String? = null,
    content: (@Composable ColumnScope.() -> Unit)? = null,
) {
    val animateEnter = LocalDockPromptAnimateEnter.current
    val typography = LocalProstoTypography.current
    val cardBg = if (isDark) DarkSurface else ProstoCardSurface
    val textColor = if (isDark) DarkOnSurface else ProstoDarkText
    val visibleState = remember { MutableTransitionState(!animateEnter).apply { targetState = true } }

    BackHandler(onBack = onDismiss)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.32f))
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
                .navigationBarsPadding()
                .padding(start = 16.dp, end = 16.dp, bottom = CardBottomOffset),
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
                        color = ProstoMutedSlate,
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
                FlowRow(
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    actions.sortedBy { it.primary }.forEach { action -> PromptButton(action) }
                }
            }
        }
    }
}

@Composable
private fun PromptButton(action: DockPromptAction) {
    val typography = LocalProstoTypography.current
    val tag = if (action.testTag != null) Modifier.testTag(action.testTag) else Modifier
    if (action.primary) {
        Button(
            onClick = action.onClick,
            shape = ProstoShapes.Pill,
            colors = ButtonDefaults.buttonColors(
                containerColor = action.color ?: ProstoBrandPrimary,
                contentColor = Color.White,
            ),
            modifier = tag,
        ) {
            Text(text = action.label, style = typography.labelMd, fontWeight = FontWeight.SemiBold)
        }
    } else {
        TextButton(onClick = action.onClick, modifier = tag) {
            Text(text = action.label, style = typography.labelMd, color = action.color ?: ProstoMutedSlate)
        }
    }
}

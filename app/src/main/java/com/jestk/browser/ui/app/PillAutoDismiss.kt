package com.jestk.browser.ui.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalAccessibilityManager
import kotlinx.coroutines.delay

/**
 * Timeout for an auto-dismissing pill: [baseTimeoutMs], or what Android recommends when the user
 * set "Time to take action" (which also feeds `accessibility_interactive_ui_timeout_ms`).
 */
@Composable
internal fun rememberPillTimeoutMillis(
    baseTimeoutMs: Long,
    containsIcons: Boolean,
    containsControls: Boolean,
): Long =
    LocalAccessibilityManager.current?.calculateRecommendedTimeoutMillis(
        baseTimeoutMs,
        containsIcons = containsIcons,
        containsText = true,
        containsControls = containsControls,
    ) ?: baseTimeoutMs

/** Calls [onDismiss] once [rememberPillTimeoutMillis] has passed; restarts when [restartKey] changes. */
@Composable
internal fun PillAutoDismissEffect(
    active: Boolean,
    restartKey: Any?,
    baseTimeoutMs: Long,
    containsIcons: Boolean,
    containsControls: Boolean,
    onDismiss: () -> Unit,
) {
    val timeoutMs = rememberPillTimeoutMillis(baseTimeoutMs, containsIcons, containsControls)
    val currentOnDismiss by rememberUpdatedState(onDismiss)
    LaunchedEffect(active, restartKey, timeoutMs) {
        if (active) {
            delay(timeoutMs)
            currentOnDismiss()
        }
    }
}

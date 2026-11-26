package com.jestk.browser.ui.overlays

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.DriveFileMove
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.jestk.browser.ui.theme.DarkSurface
import com.jestk.browser.ui.theme.ProstoActiveTabContentDark
import com.jestk.browser.ui.theme.ProstoBrandPrimary
import com.jestk.browser.ui.theme.ProstoShapes

private const val SCRIM_FADE_MS = 120
private const val SCRIM_ALPHA_LIGHT = 0.18f
private const val SCRIM_ALPHA_DARK = 0.28f

/**
 * The "drop here to make a folder" hint drawn over the card under the dragged ghost: a primary-tinted
 * scrim with a centred folder icon on a small surface-coloured disc, so it reads on any thumbnail.
 * It fades in and out, and does not take touches. [joinsFolder] swaps the icon when the target is
 * already a folder (the drop joins it instead of creating one).
 */
@Composable
internal fun BoxScope.GroupingHintScrim(
    visible: Boolean,
    joinsFolder: Boolean,
    isDark: Boolean,
) {
    val fade by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(SCRIM_FADE_MS),
        label = "overviewGroupingScrim",
    )
    if (fade <= 0f) return
    val scrimAlpha = if (isDark) SCRIM_ALPHA_DARK else SCRIM_ALPHA_LIGHT
    Box(
        modifier =
            Modifier
                .matchParentSize()
                .graphicsLayer { alpha = fade }
                .clip(ProstoShapes.Card)
                .background(ProstoBrandPrimary.copy(alpha = scrimAlpha)),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier =
                Modifier
                    .size(52.dp)
                    .shadow(4.dp, CircleShape)
                    .clip(CircleShape)
                    .background(if (isDark) DarkSurface else Color.White),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector =
                    if (joinsFolder) Icons.AutoMirrored.Outlined.DriveFileMove else Icons.Outlined.CreateNewFolder,
                contentDescription = null,
                tint = if (isDark) ProstoActiveTabContentDark else ProstoBrandPrimary,
                modifier = Modifier.size(32.dp),
            )
        }
    }
}

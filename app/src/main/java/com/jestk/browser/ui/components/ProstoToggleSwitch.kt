package com.jestk.browser.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.jestk.browser.ui.theme.DarkSurfaceVariant
import com.jestk.browser.ui.theme.LocalIsDarkTheme
import com.jestk.browser.ui.theme.ProstoBrandPrimary

/**
 * Prosto Canvas toggle switch: a solid track with a crisp white thumb.
 * No blur, no haze — the switch follows the strict Prosto Canvas surface rules.
 */
@Composable
fun ProstoToggleSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val isDark = LocalIsDarkTheme.current
    val thumbOffset by animateDpAsState(
        targetValue = if (checked) 22.dp else 2.dp,
        animationSpec = tween(durationMillis = 220),
        label = "SwitchThumbTranslation",
    )

    Box(
        modifier =
            modifier
                .size(width = 51.dp, height = 31.dp)
                .clip(CircleShape)
                .background(
                    if (checked) {
                        ProstoBrandPrimary
                    } else {
                        (if (isDark) DarkSurfaceVariant else Color(0xFFE2E8F0))
                    },
                ).clickable { onCheckedChange(!checked) }
                .padding(vertical = 2.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Surface(
            shape = CircleShape,
            color = Color.White,
            border = BorderStroke(0.5.dp, Color.Black.copy(alpha = 0.08f)),
            shadowElevation = 1.dp,
            modifier =
                Modifier
                    .offset { IntOffset(thumbOffset.roundToPx(), 0) }
                    .size(27.dp),
        ) {}
    }
}

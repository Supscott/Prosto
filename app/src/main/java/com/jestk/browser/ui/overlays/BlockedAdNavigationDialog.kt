package com.jestk.browser.ui.overlays

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jestk.browser.R
import com.jestk.browser.ui.components.swipeToDismissPill
import com.jestk.browser.ui.theme.DarkOnSurface
import com.jestk.browser.ui.theme.DarkOnSurfaceVariant
import com.jestk.browser.ui.theme.DarkSurface
import com.jestk.browser.ui.theme.ProstoBorders
import com.jestk.browser.ui.theme.ProstoBrandPrimary
import com.jestk.browser.ui.theme.ProstoDarkText
import com.jestk.browser.ui.theme.ProstoMutedSlate
import com.jestk.browser.util.UrlSecurityUtils
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first

/**
 * Compact, single-row floating banner displayed directly above the action dock
 * when an unwanted ad redirect is intercepted.
 *
 * Requirements:
 * - 100% solid, opaque background (zero blur, zero transparency).
 * - Floats above the dock with a spring overshoot bump on enter.
 * - Auto-dismisses after 5 seconds.
 * - Horizontal swipe-to-dismiss gesture.
 * - Single row: [Shield + Target Host] ... [Proceed (no bg)] [Stay (solid bg)]
 */
@Composable
fun BlockedAdNavigationBanner(
    isDark: Boolean,
    url: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val currentOnDismiss by rememberUpdatedState(onDismiss)
    val host = remember(url) {
        UrlSecurityUtils.extractDisplayHost(url).ifEmpty { url }
    }

    var isDragging by remember { mutableStateOf(false) }

    // Auto-dismiss after 5 seconds; a banner held by the finger at that moment goes once released.
    LaunchedEffect(url) {
        delay(5000L)
        snapshotFlow { isDragging }.first { !it }
        currentOnDismiss()
    }

    val bannerBg = if (isDark) DarkSurface else Color.White
    val textColor = if (isDark) DarkOnSurface else ProstoDarkText
    val mutedColor = if (isDark) DarkOnSurfaceVariant else ProstoMutedSlate

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp)
            .swipeToDismissPill(url, onDismiss = { currentOnDismiss() }, onInteraction = { isDragging = it }),
        shape = RoundedCornerShape(16.dp),
        color = bannerBg,
        border = ProstoBorders.floatingBorder(isDark),
        shadowElevation = 8.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start,
        ) {
            Icon(
                imageVector = Icons.Default.Shield,
                contentDescription = null,
                tint = ProstoBrandPrimary,
                modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = host,
                color = textColor,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Spacer(modifier = Modifier.width(6.dp))
            // "Proceed" (left of "Stay", no background)
            TextButton(
                onClick = onConfirm,
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
            ) {
                Text(
                    text = stringResource(R.string.blocked_ad_navigation_proceed),
                    color = mutedColor,
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Medium),
                )
            }
            Spacer(modifier = Modifier.width(4.dp))
            // "Stay" (right, accent background)
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(
                    containerColor = ProstoBrandPrimary,
                    contentColor = Color.White,
                ),
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                elevation = null,
            ) {
                Text(
                    text = stringResource(R.string.blocked_ad_navigation_stay),
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                )
            }
        }
    }
}

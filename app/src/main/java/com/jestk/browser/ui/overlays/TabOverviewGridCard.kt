package com.jestk.browser.ui.overlays

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Public
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.jestk.browser.domain.model.Tab
import com.jestk.browser.R
import com.jestk.browser.ui.components.SiteFavicon
import com.jestk.browser.ui.theme.DarkBackground
import com.jestk.browser.ui.theme.DarkOnSurface
import com.jestk.browser.ui.theme.DarkSurface
import com.jestk.browser.ui.theme.DarkSurfaceVariant
import com.jestk.browser.ui.theme.LocalProstoTypography
import com.jestk.browser.ui.theme.ProstoBorders
import com.jestk.browser.ui.theme.ProstoBrandPrimary
import com.jestk.browser.ui.theme.ProstoCanvasFloor
import com.jestk.browser.ui.theme.ProstoDarkText
import com.jestk.browser.ui.theme.ProstoMutedSlate
import com.jestk.browser.ui.theme.ProstoPaleBlue
import com.jestk.browser.ui.theme.ProstoShapes
import java.io.File
import kotlin.math.roundToInt

/**
 * A single tab preview card in the tab overview grid.
 *
 * Swiping the card horizontally dismisses the tab; tapping it activates the tab.
 */
@Composable
internal fun GridTabCard(
    tab: Tab,
    isActive: Boolean,
    isDark: Boolean,
    onSelect: () -> Unit,
    onClose: () -> Unit,
) {
    var rawOffsetX by remember { mutableFloatStateOf(0f) }
    var isDismissed by remember { mutableStateOf(false) }
    val animatedOffsetX by animateFloatAsState(
        targetValue = rawOffsetX,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "TabDismissDrag",
    )
    val density = LocalDensity.current
    val dismissThresholdPx = remember(density) { with(density) { 90.dp.toPx() } }

    val baseCardBg = if (isDark) DarkSurface else Color.White
    val borderStroke = if (isActive) BorderStroke(2.dp, ProstoBrandPrimary) else ProstoBorders.cardBorder(isDark)

    AnimatedVisibility(
        visible = !isDismissed,
        exit = shrinkVertically(animationSpec = tween(200)) + fadeOut(),
    ) {
        Surface(
            shape = ProstoShapes.Card,
            color = baseCardBg,
            border = borderStroke,
            shadowElevation = if (isActive) 6.dp else 2.dp,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(9f / 16f)
                    .offset { IntOffset(animatedOffsetX.roundToInt(), 0) }
                    .clip(ProstoShapes.Card)
                    .clickable(onClick = onSelect)
                    .pointerInput(tab.id) {
                        detectHorizontalDragGestures(
                            onDragEnd = {
                                if (rawOffsetX > dismissThresholdPx || rawOffsetX < -dismissThresholdPx) {
                                    isDismissed = true
                                    onClose()
                                } else {
                                    rawOffsetX = 0f
                                }
                            },
                            onDragCancel = { rawOffsetX = 0f },
                            onHorizontalDrag = { _, dragAmount ->
                                rawOffsetX += dragAmount
                            },
                        )
                    },
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header Strip (Favicon + Truncated Title + Close X)
                val headerBg =
                    if (isActive) {
                        if (isDark) ProstoBrandPrimary.copy(alpha = 0.20f) else ProstoPaleBlue
                    } else {
                        if (isDark) DarkSurfaceVariant.copy(alpha = 0.50f) else ProstoCanvasFloor
                    }

                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(38.dp)
                            .background(headerBg)
                            .padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    // Favicon / Icon
                    SiteFavicon(
                        faviconUrl = tab.faviconUrl,
                        imageSize = 16.dp,
                        containerSize = 18.dp,
                        shape = CircleShape,
                        placeholder = {
                            Box(
                                modifier =
                                    Modifier
                                        .size(18.dp)
                                        .clip(CircleShape)
                                        .background(if (isDark) DarkSurfaceVariant else ProstoPaleBlue),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Language,
                                    contentDescription = null,
                                    tint = ProstoBrandPrimary,
                                    modifier = Modifier.size(12.dp),
                                )
                            }
                        },
                    )

                    // Title
                    Text(
                        text = tab.title.ifEmpty { "New Tab" },
                        style =
                            LocalProstoTypography.current.labelMd.copy(
                                fontSize = 12.sp,
                                fontWeight = if (isActive) FontWeight.Bold else FontWeight.SemiBold,
                            ),
                        color = if (isActive) ProstoBrandPrimary else (if (isDark) DarkOnSurface else ProstoDarkText),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )

                    // Close Button
                    IconButton(
                        onClick = onClose,
                        modifier = Modifier.size(24.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = stringResource(R.string.close_tab),
                            tint = if (isActive) ProstoBrandPrimary else ProstoMutedSlate,
                            modifier = Modifier.size(14.dp),
                        )
                    }
                }

                // Thumbnail Snapshot Preview
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .background(if (isDark) DarkBackground else ProstoCanvasFloor),
                ) {
                    val file = tab.snapshotPath?.let { File(it) }
                    if (file != null && file.exists()) {
                        AsyncImage(
                            model = file,
                            contentDescription = tab.title,
                            contentScale = ContentScale.Crop,
                            alignment = Alignment.TopCenter,
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else {
                        // Clean Placeholder
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Default.Public,
                                contentDescription = null,
                                tint = ProstoMutedSlate.copy(alpha = 0.40f),
                                modifier = Modifier.size(36.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

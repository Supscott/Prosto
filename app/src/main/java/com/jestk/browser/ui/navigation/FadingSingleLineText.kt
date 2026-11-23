package com.jestk.browser.ui.navigation

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
internal fun FadingSingleLineText(
    text: String,
    style: TextStyle,
    color: Color,
    modifier: Modifier = Modifier,
    fadeWidth: Dp = 8.dp,
) {
    var overflows by remember(text) { mutableStateOf(false) }
    val density = LocalDensity.current

    Text(
        text = text,
        style = style.copy(color = color),
        maxLines = 1,
        softWrap = false,
        overflow = TextOverflow.Clip,
        onTextLayout = { layoutResult ->
            val overflowThresholdPx = with(density) { 6.dp.toPx() }
            overflows = layoutResult.multiParagraph.maxIntrinsicWidth > (layoutResult.size.width + overflowThresholdPx)
        },
        modifier =
            modifier.then(
                if (overflows) {
                    Modifier
                        .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                        .drawWithContent {
                            drawContent()
                            if (size.width > 0f) {
                                val fadePx = fadeWidth.toPx()
                                val startX = (size.width - fadePx).coerceAtLeast(0f)
                                val stopFraction = (startX / size.width).coerceIn(0f, 1f)
                                drawRect(
                                    brush =
                                        Brush.horizontalGradient(
                                            0f to Color.Black,
                                            stopFraction to Color.Black,
                                            1f to Color.Transparent,
                                            startX = 0f,
                                            endX = size.width,
                                        ),
                                    blendMode = BlendMode.DstIn,
                                )
                            }
                        }
                } else {
                    Modifier
                },
            ),
    )
}

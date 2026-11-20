package com.jestk.browser.ui.navigation

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.jestk.browser.R
import com.jestk.browser.ui.theme.IncognitoAccent
import com.jestk.browser.ui.theme.IncognitoDockSurface

/** Violet ring that marks an incognito tab button or card. */
val IncognitoRingStroke = BorderStroke(1.5.dp, IncognitoAccent)

/** Small mask badge for the bottom-end corner of an incognito tab button or card. */
@Composable
fun IncognitoBadge(modifier: Modifier = Modifier) {
    Box(
        modifier =
            modifier
                .size(14.dp)
                .clip(CircleShape)
                .background(IncognitoDockSurface)
                .border(1.dp, IncognitoAccent, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Outlined.VisibilityOff,
            contentDescription = stringResource(R.string.tab_incognito_indicator),
            tint = IncognitoAccent,
            modifier = Modifier.size(9.dp),
        )
    }
}

/**
 * Draws the violet ring and the mask badge over a rounded tab pill. Drawn beyond the pill's own
 * clip, so it is applied to the modifier chain outside the pill surface.
 */
@Composable
fun Modifier.incognitoPillMarks(enabled: Boolean, cornerRadius: androidx.compose.ui.unit.Dp): Modifier {
    if (!enabled) return this
    val painter = rememberVectorPainter(Icons.Outlined.VisibilityOff)
    return drawWithContent {
        drawContent()
        val ringWidth = 1.5.dp.toPx()
        drawRoundRect(
            color = IncognitoAccent,
            topLeft = Offset(ringWidth / 2, ringWidth / 2),
            size = Size(size.width - ringWidth, size.height - ringWidth),
            cornerRadius = CornerRadius(cornerRadius.toPx() - ringWidth / 2),
            style = Stroke(width = ringWidth),
        )
        val radius = 7.dp.toPx()
        val center = Offset(size.width - 4.dp.toPx(), size.height - 3.dp.toPx())
        drawCircle(IncognitoDockSurface, radius, center)
        drawCircle(IncognitoAccent, radius - 0.5.dp.toPx(), center, style = Stroke(1.dp.toPx()))
        val iconSize = 9.dp.toPx()
        translate(center.x - iconSize / 2, center.y - iconSize / 2) {
            with(painter) {
                draw(Size(iconSize, iconSize), colorFilter = ColorFilter.tint(IncognitoAccent))
            }
        }
    }
}

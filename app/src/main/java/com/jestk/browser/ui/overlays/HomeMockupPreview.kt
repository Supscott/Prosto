package com.jestk.browser.ui.overlays

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.jestk.browser.ui.theme.DarkSurfaceVariant
import com.jestk.browser.ui.theme.ProstoBorders
import com.jestk.browser.ui.theme.ProstoMutedSlate
import com.jestk.browser.ui.theme.ProstoPaleBlue
import com.jestk.browser.ui.theme.ProstoShapes

/**
 * Stylized vector silhouette mockup of the Home screen (Prosto pill, search bar, favorites grid)
 * displayed when no Home snapshot has been captured to disk yet.
 */
@Composable
fun HomeMockupPreview(
    isDark: Boolean,
    workspaceColor: Color,
    modifier: Modifier = Modifier,
) {
    val pillBg = if (isDark) DarkSurfaceVariant.copy(alpha = 0.7f) else Color.White
    val borderCol = ProstoBorders.hairlineBorder(isDark)

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        // Mockup brand emblem
        Box(
            modifier = Modifier
                .width(44.dp)
                .height(8.dp)
                .background(workspaceColor.copy(alpha = 0.85f), ProstoShapes.Pill),
        )

        Spacer(modifier = Modifier.height(20.dp))

        // Mockup search bar silhouette
        Row(
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .height(34.dp)
                .background(pillBg, ProstoShapes.Dock)
                .border(borderCol.width, borderCol.brush, ProstoShapes.Dock)
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(14.dp)
                    .clip(CircleShape)
                    .background(ProstoMutedSlate.copy(alpha = 0.4f)),
            )
            Box(
                modifier = Modifier
                    .width(70.dp)
                    .height(6.dp)
                    .background(ProstoMutedSlate.copy(alpha = 0.25f), ProstoShapes.Pill),
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Mockup favorites grid (2 rows x 4 items)
        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            repeat(2) { row ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    repeat(4) { col ->
                        val tileAlpha = if (row == 0 && col < 2) 0.6f else 0.3f
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .background(
                                    if (isDark) DarkSurfaceVariant.copy(alpha = tileAlpha)
                                    else ProstoPaleBlue.copy(alpha = tileAlpha),
                                    RoundedCornerShape(8.dp),
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (row == 0 && col == 0) workspaceColor.copy(alpha = 0.7f)
                                        else ProstoMutedSlate.copy(alpha = 0.35f),
                                    ),
                            )
                        }
                    }
                }
            }
        }
    }
}

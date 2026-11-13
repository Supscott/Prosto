package com.jestk.browser.ui.views

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jestk.browser.R
import com.jestk.browser.ui.theme.LocalProstoTypography
import com.jestk.browser.ui.theme.ProstoBrandPrimary
import com.jestk.browser.ui.theme.ProstoShapes

/**
 * Large first-run card that opens the Stories-style tour. Disappears once the tour is finished
 * or dismissed; the tour stays available from Settings.
 */
@Composable
internal fun TutorialCard(
    isDark: Boolean,
    onStartClick: () -> Unit,
    onDismissClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = ProstoShapes.Card,
        color = ProstoBrandPrimary,
        shadowElevation = if (isDark) 0.dp else 2.dp,
        modifier = modifier.testTag("tutorial_card").clickable(onClick = onStartClick),
    ) {
        Box {
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 24.dp)) {
                Text(
                    text = stringResource(R.string.home_tutorial_card_title),
                    style = LocalProstoTypography.current.headlineLg,
                    color = Color.White,
                    modifier = Modifier.padding(end = 28.dp),
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = stringResource(R.string.home_tutorial_card_desc),
                    style = LocalProstoTypography.current.bodyMd,
                    color = Color.White.copy(alpha = 0.85f),
                )
                Spacer(modifier = Modifier.height(20.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier =
                        Modifier
                            .clip(CircleShape)
                            .background(Color.White)
                            .padding(start = 12.dp, end = 18.dp, top = 10.dp, bottom = 10.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = ProstoBrandPrimary,
                        modifier = Modifier.size(20.dp),
                    )
                    Text(
                        text = stringResource(R.string.home_tutorial_card_action),
                        style = LocalProstoTypography.current.labelMd.copy(fontWeight = FontWeight.SemiBold),
                        color = ProstoBrandPrimary,
                    )
                }
            }
            Box(
                modifier =
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .size(32.dp)
                        .clip(CircleShape)
                        .clickable(onClick = onDismissClick),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = stringResource(R.string.dismiss),
                    tint = Color.White.copy(alpha = 0.85f),
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

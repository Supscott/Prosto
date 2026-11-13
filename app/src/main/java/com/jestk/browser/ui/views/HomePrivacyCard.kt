package com.jestk.browser.ui.views

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jestk.browser.R
import com.jestk.browser.ui.theme.DarkOnSurface
import com.jestk.browser.ui.theme.DarkOnSurfaceVariant
import com.jestk.browser.ui.theme.DarkPrimary
import com.jestk.browser.ui.theme.DarkSurface
import com.jestk.browser.ui.theme.DarkSurfaceVariant
import com.jestk.browser.ui.theme.LocalProstoTypography
import com.jestk.browser.ui.theme.ProstoBorders
import com.jestk.browser.ui.theme.ProstoBrandPrimary
import com.jestk.browser.ui.theme.ProstoCanvasFloor
import com.jestk.browser.ui.theme.ProstoCardSurface
import com.jestk.browser.ui.theme.ProstoDarkText
import com.jestk.browser.ui.theme.ProstoMutedSlate
import com.jestk.browser.ui.theme.ProstoPaleBlue
import com.jestk.browser.ui.theme.ProstoShapes

fun formatStatCount(count: Long): String {
    if (count <= 0) return "0"
    if (count < 1000) return count.toString()
    if (count < 1_000_000) {
        val thousands = count / 1000.0
        val formatted = String.format(java.util.Locale.US, "%.1f", thousands)
        return if (formatted.endsWith(".0")) "${count / 1000}k" else "${formatted}k"
    }
    val millions = count / 1_000_000.0
    val formatted = String.format(java.util.Locale.US, "%.1f", millions)
    return if (formatted.endsWith(".0")) "${count / 1_000_000}M" else "${formatted}M"
}

@Composable
fun PrivacyProtectionCard(
    blockedAdsCount: Long,
    blockedTrackersCount: Long,
    isDark: Boolean,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = ProstoShapes.Card,
        color = if (isDark) DarkSurface else ProstoCardSurface,
        border = ProstoBorders.hairlineBorder(isDark),
        shadowElevation = 2.dp,
        modifier = modifier,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
        ) {
            // Header Row: Shield icon and "Protection Active"
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(
                    modifier =
                        Modifier
                            .size(32.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isDark) DarkSurfaceVariant else ProstoPaleBlue),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Default.Shield,
                        contentDescription = null,
                        tint = if (isDark) DarkPrimary else ProstoBrandPrimary,
                        modifier = Modifier.size(18.dp),
                    )
                }

                Text(
                    text = stringResource(R.string.home_protection_active),
                    style = LocalProstoTypography.current.headlineSm.copy(fontSize = 15.sp, fontWeight = FontWeight.Bold),
                    color = if (isDark) DarkOnSurface else ProstoDarkText,
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Two Stat Chips Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                ProtectionStatChip(
                    title = stringResource(R.string.privacy_stat_ads_blocked),
                    count = formatStatCount(blockedAdsCount),
                    isDark = isDark,
                    modifier = Modifier.weight(1f),
                )

                ProtectionStatChip(
                    title = stringResource(R.string.privacy_stat_trackers_stopped),
                    count = formatStatCount(blockedTrackersCount),
                    isDark = isDark,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun ProtectionStatChip(
    title: String,
    count: String,
    isDark: Boolean,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (isDark) DarkSurfaceVariant else ProstoCanvasFloor,
        border = ProstoBorders.hairlineBorder(isDark),
        shadowElevation = 0.dp,
        modifier = modifier,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = count,
                style = LocalProstoTypography.current.headlineSm.copy(fontWeight = FontWeight.Bold),
                color = if (isDark) DarkPrimary else ProstoBrandPrimary,
            )
            Text(
                text = title,
                style = LocalProstoTypography.current.labelSm,
                color = if (isDark) DarkOnSurfaceVariant else ProstoMutedSlate,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

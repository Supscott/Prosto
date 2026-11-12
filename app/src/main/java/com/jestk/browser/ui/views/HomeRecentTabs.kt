package com.jestk.browser.ui.views

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jestk.browser.R
import com.jestk.browser.domain.model.RecentTabItem
import com.jestk.browser.ui.components.ProstoRecentTabCard
import com.jestk.browser.ui.theme.DarkOnSurface
import com.jestk.browser.ui.theme.DarkSurface
import com.jestk.browser.ui.theme.DarkSurfaceVariant
import com.jestk.browser.ui.theme.LocalProstoTypography
import com.jestk.browser.ui.theme.ProstoBorders
import com.jestk.browser.ui.theme.ProstoBrandPrimary
import com.jestk.browser.ui.theme.ProstoDarkText
import com.jestk.browser.ui.theme.ProstoMutedSlate
import com.jestk.browser.ui.theme.ProstoPaleBlue

@Composable
internal fun RecentTabsSection(
    recentTabs: List<RecentTabItem>,
    isDark: Boolean,
    onOpenRecent: (RecentTabItem) -> Unit,
    onMoreClick: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        // Header
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.home_recently_closed),
                style = LocalProstoTypography.current.headlineSm,
                color = if (isDark) DarkOnSurface else ProstoDarkText,
            )

            Text(
                text = stringResource(R.string.home_recent_total, recentTabs.size),
                style = LocalProstoTypography.current.labelMd,
                color = ProstoMutedSlate,
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Horizontal Snap Carousel (136x184dp cards)
        LazyRow(
            contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            items(recentTabs, key = { it.url }) { item ->
                ProstoRecentTabCard(
                    item = item,
                    isDark = isDark,
                    onClick = { onOpenRecent(item) },
                )
            }

            item {
                // More / Full History Card
                MoreHistoryCard(
                    isDark = isDark,
                    onClick = onMoreClick,
                )
            }
        }
    }
}


@Composable
private fun MoreHistoryCard(
    isDark: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = if (isDark) DarkSurface else Color.White,
        border = ProstoBorders.cardBorder(isDark),
        shadowElevation = 2.dp,
        modifier =
            Modifier
                .width(110.dp)
                .height(184.dp)
                .clickable(onClick = onClick),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(
                modifier =
                    Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(if (isDark) DarkSurfaceVariant else ProstoPaleBlue),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = stringResource(R.string.action_more),
                    tint = ProstoBrandPrimary,
                    modifier = Modifier.size(20.dp),
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = stringResource(R.string.action_more),
                style =
                    LocalProstoTypography.current.headlineSm.copy(
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                    ),
                color = if (isDark) DarkOnSurface else ProstoDarkText,
                textAlign = TextAlign.Center,
            )

            Text(
                text = stringResource(R.string.expand_history),
                style = LocalProstoTypography.current.bodySm.copy(fontSize = 10.sp),
                color = ProstoMutedSlate,
                textAlign = TextAlign.Center,
            )
        }
    }
}

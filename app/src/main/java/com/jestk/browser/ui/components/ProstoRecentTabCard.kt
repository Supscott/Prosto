package com.jestk.browser.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Architecture
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Public
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.jestk.browser.domain.model.RecentTabItem
import com.jestk.browser.ui.theme.CardPreviewBlueBrush
import com.jestk.browser.ui.theme.CardPreviewPeachBrush
import com.jestk.browser.ui.theme.CardPreviewRoseBrush
import com.jestk.browser.ui.theme.DarkOnSurface
import com.jestk.browser.ui.theme.DarkSurface
import com.jestk.browser.ui.theme.LocalProstoTypography
import com.jestk.browser.ui.theme.ProstoAccent
import com.jestk.browser.ui.theme.ProstoBorders
import com.jestk.browser.ui.theme.ProstoBrandPrimary
import com.jestk.browser.ui.theme.ProstoDarkText
import com.jestk.browser.ui.theme.ProstoMutedSlate
import java.io.File
import java.net.URI

/**
 * Prosto Canvas recent-tab card: a snapshot preview header above the page title,
 * host and relative timestamp. Surfaces are solid with a hairline micro-border -
 * no blur, no haze.
 */
@Composable
fun ProstoRecentTabCard(
    item: RecentTabItem,
    isDark: Boolean,
    onClick: () -> Unit,
) {
    val previewGradient =
        when (kotlin.math.abs(item.url.hashCode()) % 3) {
            0 -> CardPreviewBlueBrush
            1 -> CardPreviewRoseBrush
            else -> CardPreviewPeachBrush
        }

    val iconTint = ProstoBrandPrimary

    val previewIcon =
        when (kotlin.math.abs(item.url.hashCode()) % 4) {
            0 -> Icons.Default.Code
            1 -> Icons.Default.Architecture
            2 -> Icons.Default.Public
            else -> Icons.Default.Palette
        }

    val hostDomain =
        try {
            URI(item.url).host?.removePrefix("www.") ?: item.url.removePrefix("https://").removePrefix("http://")
        } catch (_: Exception) {
            item.url
        }

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = if (isDark) DarkSurface else Color.White,
        border = ProstoBorders.cardBorder(isDark),
        shadowElevation = 2.dp,
        modifier =
            Modifier
                .width(136.dp)
                .height(184.dp)
                .clickable(onClick = onClick),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(8.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            // Top Preview Header (92dp)
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(92.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(previewGradient),
            ) {
                val file = item.snapshotPath?.let { File(it) }
                if (file != null && file.exists()) {
                    AsyncImage(
                        model = file,
                        contentDescription = item.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    // Decorative header with category glyph and wireframe preview bars
                    Column(
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .padding(8.dp),
                        verticalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                modifier =
                                    Modifier
                                        .size(24.dp)
                                        .clip(CircleShape)
                                        .background(Color.White),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    imageVector = previewIcon,
                                    contentDescription = null,
                                    tint = iconTint,
                                    modifier = Modifier.size(14.dp),
                                )
                            }

                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                                contentDescription = null,
                                tint = iconTint.copy(alpha = 0.70f),
                                modifier = Modifier.size(14.dp),
                            )
                        }

                        // Wireframe mockup bars
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Box(
                                modifier =
                                    Modifier
                                        .width(56.dp)
                                        .height(5.dp)
                                        .clip(RoundedCornerShape(3.dp))
                                        .background(ProstoAccent.copy(alpha = 0.35f)),
                            )
                            Box(
                                modifier =
                                    Modifier
                                        .width(76.dp)
                                        .height(5.dp)
                                        .clip(RoundedCornerShape(3.dp))
                                        .background(ProstoAccent.copy(alpha = 0.20f)),
                            )
                        }
                    }
                }
            }

            // Bottom Info
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                verticalArrangement = Arrangement.spacedBy(1.dp),
            ) {
                val displayTitle = item.title.ifEmpty { hostDomain }
                val displaySubtitle = if (item.title.isNotEmpty() && !item.title.equals(hostDomain, ignoreCase = true)) hostDomain else null

                Text(
                    text = displayTitle,
                    style =
                        LocalProstoTypography.current.labelMd.copy(
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                        ),
                    color = if (isDark) DarkOnSurface else ProstoDarkText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (displaySubtitle != null) {
                    Text(
                        text = displaySubtitle,
                        style = LocalProstoTypography.current.bodySm.copy(fontSize = 10.sp),
                        color = ProstoMutedSlate,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                val relativeTime = formatRelativeTime(item.lastVisitedTimestamp)
                if (relativeTime.isNotEmpty()) {
                    Text(
                        text = relativeTime,
                        style = LocalProstoTypography.current.bodySm.copy(fontSize = 9.sp),
                        color = ProstoMutedSlate,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}
internal fun formatRelativeTime(timestamp: Long): String {
    if (timestamp <= 0L) return ""
    val now = System.currentTimeMillis()
    val diff = now - timestamp
    if (diff < 0) return "Just now"
    val minutes = diff / (1000 * 60)
    val hours = diff / (1000 * 60 * 60)
    val days = diff / (1000 * 60 * 60 * 24)

    return when {
        minutes < 5 -> "Just now"
        minutes < 60 -> "${minutes}m ago"
        hours < 24 -> "${hours}h ago"
        days == 1L -> "Yesterday"
        else -> "${days}d ago"
    }
}

package com.jestk.browser.ui.overlays

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.jestk.browser.ui.theme.DarkSurface
import com.jestk.browser.ui.theme.ProstoBrandPrimary
import com.jestk.browser.ui.theme.prostoFloatingSurface
import com.jestk.browser.ui.views.PrivacyProtectionCard

internal object ProtectionSceneMetrics {
    const val PAGE_HEIGHT = 162f
    const val HEADER_HEIGHT = 36f
    const val SHIELD = 40f
    const val TRACKER = 24f
    const val AD_HEIGHT = 26f
    const val STATS_TOP = 176f
    const val SCENE_HEIGHT = 312f
    const val ADS_BEFORE = 128L
    const val TRACKERS_BEFORE = 342L

    fun shieldCenter(sceneWidth: Float): Pair<Float, Float> = (sceneWidth - 30f) to (HEADER_HEIGHT / 2)
}

internal const val PROTECTION_SCENE_DURATION_MS = 7000

/** Where each tracker sits on the page before it is caught, as fractions of the scene width and dp. */
private val TrackerStarts = listOf(0.16f to 66f, 0.58f to 92f, 0.34f to 128f, 0.82f to 146f)

/** End of each tracker's flight to the shield, and of each ad slot's collapse, in story progress. */
internal val TrackerCatches = listOf(0.18f, 0.30f, 0.46f, 0.58f)
internal val AdCollapses = listOf(0.38f, 0.66f)
private const val EVENT_LENGTH = 0.09f

internal data class TrackerFrame(val x: Float, val y: Float, val alpha: Float, val scale: Float)

internal data class ProtectionSceneFrame(
    val trackers: List<TrackerFrame>,
    /** 0 = ad slot shown, 1 = collapsed away. */
    val adCollapse: List<Float>,
    val shieldBump: Float,
    val adsBlocked: Long,
    val trackersStopped: Long,
)

/**
 * Protection story: trackers on a loading page fly into the shield one by one and ad slots fold
 * away so the content closes up; the Home protection card counts each one as it is stopped.
 */
internal fun protectionSceneFrame(p: Float, sceneWidth: Float): ProtectionSceneFrame {
    val (shieldX, shieldY) = ProtectionSceneMetrics.shieldCenter(sceneWidth)
    val trackers =
        TrackerStarts.zip(TrackerCatches).map { (start, end) ->
            val flight = eased(p, end - EVENT_LENGTH, end)
            TrackerFrame(
                x = lerp(sceneWidth * start.first, shieldX, flight),
                y = lerp(start.second, shieldY, flight),
                alpha = 1f - phase(p, end - 0.02f, end),
                scale = lerp(1f, 0.5f, flight),
            )
        }
    val ads = AdCollapses.map { end -> eased(p, end - EVENT_LENGTH, end) }
    val events = TrackerCatches + AdCollapses
    return ProtectionSceneFrame(
        trackers = trackers,
        adCollapse = ads,
        shieldBump = events.maxOf { visible(p, it - 0.01f, it + 0.08f) },
        adsBlocked = ProtectionSceneMetrics.ADS_BEFORE + AdCollapses.count { p >= it },
        trackersStopped = ProtectionSceneMetrics.TRACKERS_BEFORE + TrackerCatches.count { p >= it },
    )
}

@Composable
internal fun TutorialProtectionScene(progress: Float, isDark: Boolean, modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier = modifier.fillMaxWidth().height(ProtectionSceneMetrics.SCENE_HEIGHT.dp)) {
        val sceneWidth = maxWidth.value
        val frame = protectionSceneFrame(progress, sceneWidth)
        ProtectedPage(frame, isDark)
        val (shieldX, shieldY) = ProtectionSceneMetrics.shieldCenter(sceneWidth)
        val shield = ProtectionSceneMetrics.SHIELD
        Box(
            modifier =
                Modifier
                    .offset((shieldX - shield / 2).dp, (shieldY - shield / 2).dp)
                    .size(shield.dp)
                    .graphicsLayer {
                        val scale = 1f + 0.14f * frame.shieldBump
                        scaleX = scale
                        scaleY = scale
                    }
                    .shadow(6.dp, CircleShape, clip = false)
                    .background(ProstoBrandPrimary, CircleShape)
                    .border(2.dp, Color.White.copy(alpha = 0.9f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(imageVector = Icons.Default.Shield, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
        }
        frame.trackers.forEach { tracker ->
            val size = ProtectionSceneMetrics.TRACKER
            Box(
                modifier =
                    Modifier
                        .offset((tracker.x - size / 2).dp, (tracker.y - size / 2).dp)
                        .size(size.dp)
                        .graphicsLayer {
                            alpha = tracker.alpha
                            scaleX = tracker.scale
                            scaleY = tracker.scale
                        }
                        .background(TrackerColor, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(imageVector = Icons.Default.Visibility, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
            }
        }
        PrivacyProtectionCard(
            blockedAdsCount = frame.adsBlocked,
            blockedTrackersCount = frame.trackersStopped,
            isDark = isDark,
            modifier = Modifier.fillMaxWidth().offset(y = ProtectionSceneMetrics.STATS_TOP.dp),
        )
    }
}

private val TrackerColor = Color(0xFFEF4444)
private val AdColor = Color(0xFFF59E0B)

/** A page with text lines and two ad slots that fold away when they are blocked. */
@Composable
private fun ProtectedPage(frame: ProtectionSceneFrame, isDark: Boolean) {
    val line = if (isDark) Color.White.copy(alpha = 0.10f) else Color.Black.copy(alpha = 0.07f)
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(ProtectionSceneMetrics.PAGE_HEIGHT.dp)
                .prostoFloatingSurface(
                    shape = RoundedCornerShape(20.dp),
                    isDark = isDark,
                    backgroundColor = if (isDark) DarkSurface else Color.White,
                    elevation = 4.dp,
                ),
    ) {
        Box(
            modifier = Modifier.fillMaxWidth().height(ProtectionSceneMetrics.HEADER_HEIGHT.dp).background(Color(0xFF475569)),
            contentAlignment = Alignment.CenterStart,
        ) {
            Box(modifier = Modifier.padding(start = 16.dp).size(width = 72.dp, height = 10.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.85f)))
        }
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            PageLine(1f, line)
            AdSlot(frame.adCollapse[0])
            PageLine(0.85f, line)
            PageLine(0.7f, line)
            AdSlot(frame.adCollapse[1])
        }
    }
}

@Composable
private fun PageLine(fraction: Float, color: Color) {
    Box(modifier = Modifier.fillMaxWidth(fraction).height(9.dp).clip(CircleShape).background(color))
    Spacer(modifier = Modifier.height(7.dp))
}

@Composable
private fun AdSlot(collapse: Float) {
    val open = 1f - collapse
    val height = ProtectionSceneMetrics.AD_HEIGHT * open
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(height.dp)
                .graphicsLayer {
                    alpha = open
                    scaleY = open.coerceAtLeast(0.01f)
                }
                .clip(RoundedCornerShape(8.dp))
                .background(AdColor.copy(alpha = 0.16f))
                .border(1.dp, AdColor.copy(alpha = 0.45f), RoundedCornerShape(8.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(imageVector = Icons.Default.Campaign, contentDescription = null, tint = AdColor, modifier = Modifier.size(16.dp))
    }
    Spacer(modifier = Modifier.height((7f * open).dp))
}

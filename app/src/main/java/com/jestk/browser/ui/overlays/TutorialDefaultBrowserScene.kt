package com.jestk.browser.ui.overlays

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jestk.browser.R
import com.jestk.browser.ui.theme.DarkOnSurface
import com.jestk.browser.ui.theme.DarkPrimary
import com.jestk.browser.ui.theme.DarkSurface
import com.jestk.browser.ui.theme.DarkSurfaceVariant
import com.jestk.browser.ui.theme.LocalProstoTypography
import com.jestk.browser.ui.theme.ProstoBrandPrimary
import com.jestk.browser.ui.theme.ProstoDarkText
import com.jestk.browser.ui.theme.ProstoMutedSlate
import com.jestk.browser.ui.theme.prostoFloatingSurface
import com.jestk.browser.ui.overlays.MockDockMetrics as M

internal object DefaultSceneMetrics {
    const val SCENE_HEIGHT = 236f
    const val TOP_BAR = 48f
    const val ROW = 52f
    const val CHAT_LINK_BUBBLE_TOP = 96f
    const val CHAT_LINK_X = 96f
    const val CHAT_LINK_Y = CHAT_LINK_BUBBLE_TOP + 22f

    /** Vertical centre of browser row [index] in the settings list. */
    fun rowCenter(index: Int): Float = TOP_BAR + ROW * index + ROW / 2
}

internal const val DEFAULT_SCENE_DURATION_MS = 7000
internal const val DEFAULT_SCENE_PROSTO_ROW = 1
private const val CHAT_LINK = "wikipedia.org/wiki/Web_browser"

/**
 * [selected] moves the radio from the previous default (row 0) to Prosto (row 1); [toChat] swaps
 * the settings screen for a chat; [opened] raises Prosto over the chat with the tapped link.
 */
internal data class DefaultSceneFrame(
    val selected: Float,
    val toChat: Float,
    val opened: Float,
    val touch: TouchFrame?,
)

/**
 * Default browser story: in the system "Default browser app" screen, tap Prosto and the radio moves
 * to it; then a link tapped in a chat opens straight in Prosto.
 */
internal fun defaultSceneFrame(p: Float, sceneWidth: Float): DefaultSceneFrame {
    val touches =
        listOf(
            TouchFrame(sceneWidth * 0.45f, DefaultSceneMetrics.rowCenter(DEFAULT_SCENE_PROSTO_ROW), visible(p, 0.08f, 0.32f), pressed(p, 0.16f, 0.21f), phase(p, 0.17f, 0.28f)),
            TouchFrame(DefaultSceneMetrics.CHAT_LINK_X, DefaultSceneMetrics.CHAT_LINK_Y, visible(p, 0.52f, 0.70f), pressed(p, 0.58f, 0.62f), phase(p, 0.59f, 0.68f)),
        )
    return DefaultSceneFrame(
        selected = eased(p, 0.19f, 0.27f),
        toChat = eased(p, 0.38f, 0.48f),
        opened = eased(p, 0.64f, 0.80f),
        touch = touches.current(),
    )
}

private data class MockBrowser(val letter: String, val name: String, val color: Color)

private val DefaultSceneDock =
    listOf(DockSceneSites[0], DockSceneSites[1], DockSceneSites[2], DockSceneSites[3])

@Composable
internal fun TutorialDefaultBrowserScene(progress: Float, isDark: Boolean, modifier: Modifier = Modifier) {
    val browsers =
        listOf(
            MockBrowser("C", "Chrome", Color(0xFF4285F4)),
            MockBrowser("P", stringResource(R.string.app_name), ProstoBrandPrimary),
            MockBrowser("F", "Firefox", Color(0xFFFF7139)),
        )
    MockSceneBox(modifier) { capsuleWidth ->
        val sceneWidth = capsuleWidth + 2 * (M.SIDE_BUTTON + M.ROW_SPACING)
        val frame = defaultSceneFrame(progress, sceneWidth)
        val widths = rememberPillWidths(DefaultSceneDock, capsuleWidth)
        val dock =
            DockSceneFrame(
                entries = DefaultSceneDock.mapIndexed { i, site -> StripEntry.TabEntry(site, widths[i], if (i == 0) 1f else 0f) },
                scroll = 0f,
                maxScroll = 0f,
                touch = null,
            )
        Box(modifier = Modifier.fillMaxWidth().height(DefaultSceneMetrics.SCENE_HEIGHT.dp).clipToBounds()) {
            DefaultAppsScreen(
                browsers = browsers,
                selected = frame.selected,
                isDark = isDark,
                modifier =
                    Modifier.graphicsLayer {
                        translationX = -frame.toChat * size.width * 0.3f
                        alpha = 1f - frame.toChat
                    },
            )
            ChatScreen(
                isDark = isDark,
                modifier =
                    Modifier.graphicsLayer {
                        translationX = (1f - frame.toChat) * size.width
                        alpha = frame.toChat * (1f - frame.opened)
                    },
            )
            MockScreen(
                frame = dock,
                isDark = isDark,
                workspaceColor = MockPersonalColor,
                modifier =
                    Modifier
                        .offset(y = ((DefaultSceneMetrics.SCENE_HEIGHT - MockScreenHeight) / 2).dp)
                        .graphicsLayer {
                            translationY = (1f - frame.opened) * size.height * 0.6f
                            alpha = frame.opened
                        },
            )
            frame.touch?.let { TouchIndicator(it, isDark) }
        }
    }
}

private fun cardColor(isDark: Boolean): Color = if (isDark) DarkSurface else Color.White

private fun titleColor(isDark: Boolean): Color = if (isDark) DarkOnSurface else ProstoDarkText

/** A plain system settings screen: back arrow, title and a radio list of installed browsers. */
@Composable
private fun DefaultAppsScreen(browsers: List<MockBrowser>, selected: Float, isDark: Boolean, modifier: Modifier) {
    val typography = LocalProstoTypography.current
    val accent = if (isDark) DarkPrimary else ProstoBrandPrimary
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .height(DefaultSceneMetrics.SCENE_HEIGHT.dp)
                .prostoFloatingSurface(shape = RoundedCornerShape(20.dp), isDark = isDark, backgroundColor = cardColor(isDark), elevation = 4.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().height(DefaultSceneMetrics.TOP_BAR.dp).padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, tint = titleColor(isDark), modifier = Modifier.size(20.dp))
            Text(
                text = stringResource(R.string.tutorial_mock_default_browser_app),
                style = typography.labelMd.copy(fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
                color = titleColor(isDark),
            )
        }
        browsers.forEachIndexed { i, browser ->
            val on = when (i) {
                0 -> 1f - selected
                DEFAULT_SCENE_PROSTO_ROW -> selected
                else -> 0f
            }
            Row(
                modifier = Modifier.fillMaxWidth().height(DefaultSceneMetrics.ROW.dp).padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier.size(32.dp).clip(CircleShape).background(browser.color),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(text = browser.letter, style = typography.labelMd.copy(fontWeight = FontWeight.Bold, fontSize = 15.sp), color = Color.White)
                }
                Spacer(modifier = Modifier.width(16.dp))
                Text(text = browser.name, style = typography.bodyMd, color = titleColor(isDark), modifier = Modifier.weight(1f))
                Radio(on = on, accent = accent)
            }
        }
    }
}

@Composable
private fun Radio(on: Float, accent: Color) {
    val ring = androidx.compose.ui.graphics.lerp(ProstoMutedSlate, accent, on)
    Box(modifier = Modifier.size(20.dp).border(2.dp, ring, CircleShape), contentAlignment = Alignment.Center) {
        Box(modifier = Modifier.size((10f * on).dp).clip(CircleShape).background(accent))
    }
}

/** A messenger with two incoming messages, the second one a link. */
@Composable
private fun ChatScreen(isDark: Boolean, modifier: Modifier) {
    val bubble = if (isDark) DarkSurfaceVariant.copy(alpha = 0.6f) else Color(0xFFF1F5F4)
    val line = if (isDark) Color.White.copy(alpha = 0.18f) else Color.Black.copy(alpha = 0.10f)
    val link = if (isDark) Color(0xFF7DB8FF) else Color(0xFF1A73E8)
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .height(DefaultSceneMetrics.SCENE_HEIGHT.dp)
                .prostoFloatingSurface(shape = RoundedCornerShape(20.dp), isDark = isDark, backgroundColor = cardColor(isDark), elevation = 4.dp),
    ) {
        Avatar(top = 20f)
        Column(
            modifier =
                Modifier
                    .offset(56.dp, 20.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(bubble)
                    .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(modifier = Modifier.size(width = 160.dp, height = 9.dp).clip(CircleShape).background(line))
            Box(modifier = Modifier.size(width = 110.dp, height = 9.dp).clip(CircleShape).background(line))
        }
        Avatar(top = DefaultSceneMetrics.CHAT_LINK_BUBBLE_TOP)
        Box(
            modifier =
                Modifier
                    .offset(56.dp, DefaultSceneMetrics.CHAT_LINK_BUBBLE_TOP.dp)
                    .widthIn(max = 260.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(bubble)
                    .padding(horizontal = 12.dp, vertical = 12.dp),
        ) {
            Text(
                text = CHAT_LINK,
                style = LocalProstoTypography.current.bodyMd.copy(textDecoration = TextDecoration.Underline),
                color = link,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun Avatar(top: Float) {
    Box(
        modifier =
            Modifier
                .offset(16.dp, top.dp)
                .size(30.dp)
                .clip(CircleShape)
                .background(Color(0xFF8B5CF6).copy(alpha = 0.7f)),
    )
}

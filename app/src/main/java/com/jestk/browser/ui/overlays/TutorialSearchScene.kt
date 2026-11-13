package com.jestk.browser.ui.overlays

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.FilterNone
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jestk.browser.R
import com.jestk.browser.ui.theme.DarkOnSurface
import com.jestk.browser.ui.theme.DarkPrimary
import com.jestk.browser.ui.theme.DarkSurface
import com.jestk.browser.ui.theme.DarkSurfaceVariant
import com.jestk.browser.ui.theme.LocalProstoTypography
import com.jestk.browser.ui.theme.ProstoBorders
import com.jestk.browser.ui.theme.ProstoBrandPrimary
import com.jestk.browser.ui.theme.ProstoDarkText
import com.jestk.browser.ui.theme.ProstoMutedSlate
import kotlin.math.abs
import kotlin.math.roundToInt

internal object SearchSceneMetrics {
    const val SUGGESTION_HEIGHT = 44f
    const val SUGGESTION_WIDTH = 230f
    const val SUGGESTION_START = 4f
    const val INPUT_TOP = 56f
    const val INPUT_HEIGHT = 52f
    const val INPUT_SPACING = 8f
    const val CAROUSEL_REST = 68f
    const val CAROUSEL_OPEN = 160f
    const val ENGINE_SPACING = 36f
    const val KEYBOARD_TOP = 120f
    const val KEY_HEIGHT = 26f
    const val KEY_GAP = 5f
    const val KEYBOARD_PAD = 5f
    val KeyRows = listOf("qwertyuiop", "asdfghjkl", "zxcvbnm")
    val SceneHeight: Float get() = KEYBOARD_TOP + 2 * KEYBOARD_PAD + (KeyRows.size + 1) * KEY_HEIGHT + KeyRows.size * KEY_GAP

    fun keyWidth(sceneWidth: Float): Float = (sceneWidth - 2 * KEYBOARD_PAD - 9 * KEY_GAP) / 10

    /** Centre of the key for [char] on the mock keyboard, in scene dp. */
    fun keyCenter(char: Char, sceneWidth: Float): Pair<Float, Float> {
        val row = KeyRows.indexOfFirst { char in it }
        val keys = KeyRows[row]
        val width = keyWidth(sceneWidth)
        val rowWidth = keys.length * width + (keys.length - 1) * KEY_GAP
        val startX = (sceneWidth - rowWidth) / 2
        val x = startX + keys.indexOf(char) * (width + KEY_GAP) + width / 2
        val y = KEYBOARD_TOP + KEYBOARD_PAD + row * (KEY_HEIGHT + KEY_GAP) + KEY_HEIGHT / 2
        return x to y
    }
}

/** A search provider in the carousel; [letter] on [color] stands in for its badge. */
internal data class MockEngine(val letter: String, val name: String, val color: Color)

internal val SearchSceneEngines =
    listOf(
        MockEngine("G", "Google", Color(0xFF4285F4)),
        MockEngine("D", "DuckDuckGo", Color(0xFFDE5833)),
        MockEngine("B", "Brave", Color(0xFFFB542B)),
        MockEngine("K", "Kagi", Color(0xFFFFB319)),
    )

internal const val SEARCH_SCENE_TYPED = "yo"
internal const val SEARCH_SCENE_MATCH = "youtube.com"
internal val SearchSceneOpenTab = MockSite("Y", "YouTube", "youtube.com", Color(0xFFEF4444))
internal const val SEARCH_SCENE_DURATION_MS = 8000

/**
 * [carouselOpen] widens the engine selector while it is flung, [enginePos] is the engine index
 * under the selector (fractional while it moves), [typed] the number of characters typed so far,
 * [suggestion] how far the open-tab capsule has appeared and [cardPress] squeezes it on the tap.
 */
internal data class SearchSceneFrame(
    val carouselOpen: Float,
    val enginePos: Float,
    val typed: Int,
    val suggestion: Float,
    val cardPress: Float,
    val cursorOn: Boolean,
    val touch: TouchFrame?,
)

/**
 * Search story: fling across the engine selector right of the input and DuckDuckGo takes Google's
 * place; type "yo" on the keyboard and the input completes it inline to youtube.com while the
 * already open YouTube tab is offered right above the input; tap it to switch to that tab.
 */
internal fun searchSceneFrame(p: Float, sceneWidth: Float): SearchSceneFrame {
    val m = SearchSceneMetrics
    val open = eased(p, 0.10f, 0.15f) * (1f - eased(p, 0.28f, 0.34f))
    val inputY = m.INPUT_TOP + m.INPUT_HEIGHT / 2
    val (yX, yY) = m.keyCenter(SEARCH_SCENE_TYPED[0], sceneWidth)
    val (oX, oY) = m.keyCenter(SEARCH_SCENE_TYPED[1], sceneWidth)
    val typed = when {
        p >= 0.51f -> 2
        p >= 0.43f -> 1
        else -> 0
    }
    val cardX = m.SUGGESTION_START + m.SUGGESTION_WIDTH / 2
    val cardY = m.SUGGESTION_HEIGHT / 2
    val touches =
        listOf(
            TouchFrame(lerp(sceneWidth - 30f, sceneWidth - 120f, eased(p, 0.13f, 0.27f)), inputY, visible(p, 0.06f, 0.34f), pressed(p, 0.10f, 0.29f)),
            TouchFrame(lerp(yX, oX, eased(p, 0.45f, 0.49f)), lerp(yY, oY, eased(p, 0.45f, 0.49f)), visible(p, 0.37f, 0.58f), maxOf(pressed(p, 0.41f, 0.44f), pressed(p, 0.49f, 0.52f))),
            TouchFrame(cardX, cardY, visible(p, 0.62f, 0.82f), pressed(p, 0.68f, 0.72f), phase(p, 0.69f, 0.79f)),
        )
    return SearchSceneFrame(
        carouselOpen = open,
        enginePos = eased(p, 0.14f, 0.27f),
        typed = typed,
        suggestion = eased(p, 0.44f, 0.50f),
        cardPress = pressed(p, 0.68f, 0.72f),
        cursorOn = ((p * 16f).toInt() % 2) == 0,
        touch = touches.current(),
    )
}

@Composable
internal fun TutorialSearchScene(progress: Float, isDark: Boolean, modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier = modifier.fillMaxWidth().height(SearchSceneMetrics.SceneHeight.dp)) {
        val sceneWidth = maxWidth.value
        val frame = searchSceneFrame(progress, sceneWidth)
        val m = SearchSceneMetrics
        Box(
            modifier =
                Modifier
                    .offset(m.SUGGESTION_START.dp, 0.dp)
                    .graphicsLayer {
                        alpha = frame.suggestion
                        translationY = (1f - frame.suggestion) * 12.dp.toPx()
                        val scale = 1f - 0.04f * frame.cardPress
                        scaleX = scale
                        scaleY = scale
                    },
        ) {
            OpenTabCapsule(isDark)
        }
        Row(
            modifier = Modifier.offset(0.dp, m.INPUT_TOP.dp).fillMaxWidth().height(m.INPUT_HEIGHT.dp),
            horizontalArrangement = Arrangement.spacedBy(m.INPUT_SPACING.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SearchInput(frame, isDark, Modifier.weight(1f))
            EngineSelector(frame, isDark)
        }
        MockKeyboard(sceneWidth, isDark, Modifier.offset(0.dp, m.KEYBOARD_TOP.dp))
        frame.touch?.let { TouchIndicator(it, isDark) }
    }
}

private fun surfaceColor(isDark: Boolean): Color = if (isDark) DarkSurface else Color.White

@Composable
private fun SearchInput(frame: SearchSceneFrame, isDark: Boolean, modifier: Modifier) {
    val engine = SearchSceneEngines[frame.enginePos.roundToInt()]
    val textColor = if (isDark) DarkOnSurface else ProstoDarkText
    val style = LocalProstoTypography.current.bodyMd
    val highlight = LocalTextSelectionColors.current.backgroundColor
    Row(
        modifier =
            modifier
                .fillMaxHeight()
                .shadow(4.dp, CircleShape, clip = false)
                .background(surfaceColor(isDark), CircleShape)
                .border(ProstoBorders.cardBorder(isDark), CircleShape)
                .padding(start = 14.dp, end = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(imageVector = Icons.Default.Search, contentDescription = null, tint = ProstoMutedSlate, modifier = Modifier.size(20.dp))
        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (frame.typed == 0) {
                Text(
                    text = stringResource(R.string.search_with_engine, engine.name),
                    style = style,
                    color = ProstoMutedSlate,
                    maxLines = 1,
                    softWrap = false,
                )
            } else {
                val typed = SEARCH_SCENE_TYPED.take(frame.typed)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = typed, style = style, color = textColor, maxLines = 1, softWrap = false)
                    Box(
                        modifier =
                            Modifier
                                .width(2.dp)
                                .height(20.dp)
                                .graphicsLayer { alpha = if (frame.cursorOn) 1f else 0f }
                                .background(ProstoBrandPrimary),
                    )
                    Text(
                        text =
                            buildAnnotatedString {
                                withStyle(SpanStyle(background = highlight)) { append(SEARCH_SCENE_MATCH.drop(typed.length)) }
                            },
                        style = style,
                        color = textColor,
                        maxLines = 1,
                        softWrap = false,
                    )
                }
            }
        }
        Icon(
            imageVector = if (frame.typed == 0) Icons.Default.Mic else Icons.Default.Clear,
            contentDescription = null,
            tint = ProstoMutedSlate,
            modifier = Modifier.size(if (frame.typed == 0) 20.dp else 18.dp),
        )
    }
}

/** The engine selector: one badge at rest, widening into a carousel of badges while it is flung. */
@Composable
private fun EngineSelector(frame: SearchSceneFrame, isDark: Boolean) {
    val m = SearchSceneMetrics
    val open = frame.carouselOpen
    val accent = if (isDark) DarkPrimary else ProstoBrandPrimary
    Box(
        modifier =
            Modifier
                .width(lerp(m.CAROUSEL_REST, m.CAROUSEL_OPEN, open).dp)
                .fillMaxHeight()
                .shadow(4.dp, CircleShape, clip = false)
                .background(surfaceColor(isDark), CircleShape)
                .border(ProstoBorders.cardBorder(isDark), CircleShape)
                .clip(CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        val restShift = -11f * (1f - open)
        SearchSceneEngines.forEachIndexed { i, engine ->
            val distance = i - frame.enginePos
            val centered = (1f - abs(distance) * 2f).coerceIn(0f, 1f)
            val scale = lerp(1f, 0.78f, (abs(distance)).coerceAtMost(1f))
            // Fade through the colours, not the layer: a translated layer with alpha is drawn offscreen.
            val itemAlpha = if (abs(distance) < 0.5f) 1f else open * (1f - (abs(distance) - 1f).coerceIn(0f, 1f) * 0.5f)
            Box(
                modifier =
                    Modifier.graphicsLayer {
                        translationX = (distance * m.ENGINE_SPACING + restShift).dp.toPx()
                        scaleX = scale
                        scaleY = scale
                    },
                contentAlignment = Alignment.Center,
            ) {
                Box(modifier = Modifier.size(32.dp).border(1.5.dp, accent.copy(alpha = centered), CircleShape))
                Box(
                    modifier = Modifier.size(26.dp).clip(CircleShape).background(engine.color.copy(alpha = itemAlpha)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = engine.letter,
                        style = LocalProstoTypography.current.labelMd.copy(fontWeight = FontWeight.Bold, fontSize = 13.sp, lineHeight = 13.sp),
                        color = Color.White.copy(alpha = itemAlpha),
                    )
                }
                Box(
                    modifier =
                        Modifier
                            .offset(y = 18.dp)
                            .size(width = 8.dp, height = 3.dp)
                            .background(accent.copy(alpha = centered), CircleShape),
                )
            }
        }
        Icon(
            imageVector = Icons.Default.KeyboardArrowDown,
            contentDescription = null,
            tint = ProstoMutedSlate,
            modifier = Modifier.align(Alignment.CenterEnd).padding(end = 8.dp).size(18.dp).graphicsLayer { alpha = 1f - open },
        )
    }
}

/** The open YouTube tab offered above the input, as OpenTabSwitchCarousel draws it. */
@Composable
private fun OpenTabCapsule(isDark: Boolean) {
    val textColor = if (isDark) DarkOnSurface else ProstoDarkText
    val typography = LocalProstoTypography.current
    Row(
        modifier =
            Modifier
                .width(SearchSceneMetrics.SUGGESTION_WIDTH.dp)
                .height(SearchSceneMetrics.SUGGESTION_HEIGHT.dp)
                .shadow(3.dp, CircleShape, clip = false)
                .background(surfaceColor(isDark), CircleShape)
                .border(ProstoBorders.cardBorder(isDark), CircleShape)
                .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        MockFavicon(SearchSceneOpenTab, isDark)
        Column(modifier = Modifier.weight(1f)) {
            Text(text = SearchSceneOpenTab.title, style = typography.labelMd, color = textColor, maxLines = 1)
            Text(text = SearchSceneOpenTab.domain, style = typography.bodySm.copy(fontSize = 12.sp), color = ProstoMutedSlate, maxLines = 1)
        }
        Row(
            modifier =
                Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(ProstoBrandPrimary.copy(alpha = if (isDark) 0.25f else 0.12f))
                    .border(1.dp, ProstoBrandPrimary.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
                    .padding(horizontal = 7.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Icon(imageVector = Icons.Default.FilterNone, contentDescription = null, tint = ProstoBrandPrimary, modifier = Modifier.size(11.dp))
            Text(
                text = stringResource(R.string.switch_to_tab),
                style = typography.labelSm,
                color = ProstoBrandPrimary,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

/** A plain QWERTY keyboard under the input, so the input reads as sitting right above it. */
@Composable
private fun MockKeyboard(sceneWidth: Float, isDark: Boolean, modifier: Modifier) {
    val m = SearchSceneMetrics
    val keyWidth = m.keyWidth(sceneWidth)
    val panel = if (isDark) Color(0xFF1B2321) else Color(0xFFE3EAE8)
    val key = if (isDark) DarkSurfaceVariant.copy(alpha = 0.55f) else Color.White
    val label = if (isDark) DarkOnSurface else ProstoDarkText
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(panel)
                .padding(m.KEYBOARD_PAD.dp),
        verticalArrangement = Arrangement.spacedBy(m.KEY_GAP.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        m.KeyRows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(m.KEY_GAP.dp)) {
                row.forEach { char ->
                    Box(
                        modifier = Modifier.size(keyWidth.dp, m.KEY_HEIGHT.dp).clip(RoundedCornerShape(5.dp)).background(key),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(text = char.toString(), style = LocalProstoTypography.current.labelMd.copy(fontSize = 13.sp), color = label)
                    }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(m.KEY_GAP.dp)) {
            Spacer(modifier = Modifier.size((keyWidth * 1.5f).dp, m.KEY_HEIGHT.dp).clip(RoundedCornerShape(5.dp)).background(key))
            Spacer(modifier = Modifier.size((keyWidth * 5f).dp, m.KEY_HEIGHT.dp).clip(RoundedCornerShape(5.dp)).background(key))
            Box(
                modifier = Modifier.size((keyWidth * 1.5f).dp, m.KEY_HEIGHT.dp).clip(RoundedCornerShape(5.dp)).background(ProstoBrandPrimary),
                contentAlignment = Alignment.Center,
            ) {
                Icon(imageVector = Icons.Default.Search, contentDescription = null, tint = Color.White, modifier = Modifier.size(15.dp))
            }
        }
    }
}

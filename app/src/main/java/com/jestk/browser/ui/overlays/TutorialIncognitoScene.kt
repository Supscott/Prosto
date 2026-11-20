package com.jestk.browser.ui.overlays

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.jestk.browser.R
import com.jestk.browser.ui.navigation.incognitoPillMarks
import com.jestk.browser.ui.theme.IncognitoAccent
import com.jestk.browser.ui.theme.IncognitoActivePill
import com.jestk.browser.ui.theme.IncognitoDockSurface
import com.jestk.browser.ui.theme.ProstoBorders
import com.jestk.browser.ui.overlays.MockDockMetrics as M

internal const val INCOGNITO_SCENE_DURATION_MS = 8000

internal object IncognitoSceneMetrics {
    /** Menu card of NewTabKindSheetContent: 200dp wide, two 48dp rows, a divider and 4dp padding. */
    const val CARD_WIDTH = 200f
    const val CARD_HEIGHT = 105f
    const val CARD_ROW = 48f
    const val CARD_PAD = 4f

    /** The card's bottom edge: the dock's top (page 140 + gap 16 + 2) minus the 16dp gap of NewTabMenuPosition. */
    const val CARD_BOTTOM = 142f
    const val WINDOW_MARGIN = 12f
    const val ACTIVE_INDEX = 2
    const val PILL_HEIGHT = M.PILL_HEIGHT

    fun plusX(capsuleWidth: Float): Float = capsuleWidth - M.ADD_BUTTON_END - M.TAB / 2

    /** Left edge of the card: centred on the "+", kept inside the window as NewTabMenuPosition does. */
    fun cardLeft(sceneWidth: Float, capsuleWidth: Float): Float =
        (plusX(capsuleWidth) - CARD_WIDTH / 2).coerceIn(WINDOW_MARGIN, sceneWidth - CARD_WIDTH - WINDOW_MARGIN)

    /** The "Incognito tab" row is the first one in the card. */
    fun incognitoRowY(): Float = CARD_BOTTOM - CARD_HEIGHT + CARD_PAD + CARD_ROW / 2
}

/**
 * [menu] 0..1 pops the new-tab card up; [incognito] 0 = the normal dock, 1 = the violet dock with
 * the incognito tab active. [entries] hold the normal tabs plus the incognito one at the end.
 */
internal data class IncognitoSceneFrame(
    val entries: List<StripEntry>,
    val scroll: Float,
    val maxScroll: Float,
    val menu: Float,
    val incognito: Float,
    val touch: TouchFrame?,
)

/**
 * Incognito story: long-press "+", the menu card pops up above the dock, tap "Incognito tab" and
 * the dock turns violet with a new ringed pill and the page goes dark. It then fades back so the
 * last frame equals the first and the loop is seamless.
 */
internal fun incognitoSceneFrame(
    p: Float,
    capsuleWidth: Float,
    pillWidths: List<Float>,
    incognitoSite: MockSite,
    incognitoPillWidth: Float,
): IncognitoSceneFrame {
    val sceneWidth = capsuleWidth + 2 * (M.SIDE_BUTTON + M.ROW_SPACING)
    val incognito = eased(p, 0.60f, 0.70f) * (1f - eased(p, 0.92f, 1f))
    val menu = eased(p, 0.30f, 0.38f) * (1f - eased(p, 0.58f, 0.64f))
    val entries =
        DockSceneSites.take(pillWidths.size).mapIndexed { i, site ->
            val active = i == IncognitoSceneMetrics.ACTIVE_INDEX
            StripEntry.TabEntry(site, pillWidths[i], if (active) 1f - incognito else 0f)
        } + StripEntry.TabEntry(incognitoSite, incognitoPillWidth, incognito, appear = incognito)
    val maxScroll = (entries.contentWidth() - capsuleWidth).coerceAtLeast(0f)
    val plus = IncognitoSceneMetrics.plusX(capsuleWidth)
    val cardX = IncognitoSceneMetrics.cardLeft(sceneWidth, capsuleWidth) + IncognitoSceneMetrics.CARD_WIDTH / 2
    val touches =
        listOf(
            TouchFrame(plus, M.dockCenterY, visible(p, 0.06f, 0.37f), pressed(p, 0.12f, 0.34f), phase(p, 0.28f, 0.36f)),
            TouchFrame(cardX, IncognitoSceneMetrics.incognitoRowY(), visible(p, 0.42f, 0.66f), pressed(p, 0.52f, 0.56f), phase(p, 0.53f, 0.62f)),
        )
    return IncognitoSceneFrame(entries, maxScroll * incognito, maxScroll, menu, incognito, touches.current())
}

/** Dock colours of an incognito tab: the violet dock and active pill of BottomBar, the same in both themes. */
internal fun incognitoDockStyle(): MockDockStyle =
    MockDockStyle(
        dockBg = IncognitoDockSurface.copy(alpha = 0.92f),
        dockBorder = ProstoBorders.floatingBorder(isDark = true),
        pillBg = IncognitoActivePill,
        pillBorderColors = listOf(IncognitoAccent.copy(alpha = 0.55f), IncognitoAccent.copy(alpha = 0.20f)),
    )

private val IncognitoSceneSite = MockSite("", "", "", Color.Transparent)

@Composable
internal fun TutorialIncognitoScene(progress: Float, isDark: Boolean, modifier: Modifier = Modifier) {
    val site = IncognitoSceneSite.copy(title = stringResource(R.string.tab_home_title), color = IncognitoDockSurface)
    MockSceneBox(modifier) { capsuleWidth ->
        val sceneWidth = capsuleWidth + 2 * (M.SIDE_BUTTON + M.ROW_SPACING)
        val widths = rememberPillWidths(DockSceneSites.take(4) + site, capsuleWidth)
        val frame = incognitoSceneFrame(progress, capsuleWidth, widths.take(4), site, widths.last())
        val normal = DockSceneFrame(frame.entries.dropLast(1), scroll = 0f, maxScroll = frame.maxScroll, touch = null)
        val violet = DockSceneFrame(frame.entries, frame.scroll, frame.maxScroll, touch = null)
        Box(modifier = Modifier.fillMaxWidth().height(MockScreenHeight.dp)) {
            MockScreen(frame = normal, isDark = isDark, workspaceColor = MockPersonalColor)
            Box(modifier = Modifier.graphicsLayer { alpha = frame.incognito }) {
                MockScreen(frame = violet, isDark = true, workspaceColor = IncognitoAccent, style = incognitoDockStyle())
                IncognitoPageGlyph()
                IncognitoPillMarks(frame, widths.last())
            }
            NewTabMenuCard(frame.menu, sceneWidth, capsuleWidth, isDark)
            frame.touch?.let { TouchIndicator(it, isDark) }
        }
    }
}

@Composable
private fun IncognitoPageGlyph() {
    Box(modifier = Modifier.fillMaxWidth().height((M.PAGE_HEIGHT + 44f).dp), contentAlignment = Alignment.Center) {
        Icon(
            imageVector = Icons.Outlined.VisibilityOff,
            contentDescription = null,
            tint = IncognitoAccent.copy(alpha = 0.55f),
            modifier = Modifier.size(44.dp),
        )
    }
}

/** The violet ring and mask badge over the incognito pill, plus the mask in place of its favicon. */
@Composable
private fun IncognitoPillMarks(frame: IncognitoSceneFrame, pillWidth: Float) {
    val index = frame.entries.lastIndex
    val entry = frame.entries[index] as StripEntry.TabEntry
    val left = frame.entries.startOf(index) - frame.scroll
    val top = M.dockCenterY - M.PILL_HEIGHT / 2
    Box(
        modifier =
            Modifier
                .offset(left.dp, top.dp)
                .size(entry.width.dp, M.PILL_HEIGHT.dp)
                .graphicsLayer { alpha = entry.expansion }
                .incognitoPillMarks(enabled = true, cornerRadius = 21.dp),
    )
    Box(
        modifier =
            Modifier
                .offset((left + 10f).dp, (M.dockCenterY - 9f).dp)
                .size(18.dp)
                .graphicsLayer { alpha = entry.expansion },
        contentAlignment = Alignment.Center,
    ) {
        Icon(imageVector = Icons.Outlined.VisibilityOff, contentDescription = null, tint = IncognitoAccent, modifier = Modifier.size(17.dp))
    }
}

/** The real [NewTabKindSheetContent] card, popping up from the "+" above the dock. */
@Composable
private fun NewTabMenuCard(menu: Float, sceneWidth: Float, capsuleWidth: Float, isDark: Boolean) {
    if (menu <= 0f) return
    val left = IncognitoSceneMetrics.cardLeft(sceneWidth, capsuleWidth)
    val originX = (IncognitoSceneMetrics.plusX(capsuleWidth) - left) / IncognitoSceneMetrics.CARD_WIDTH
    Box(
        modifier =
            Modifier
                .offset(left.dp, 0.dp)
                .size(IncognitoSceneMetrics.CARD_WIDTH.dp, IncognitoSceneMetrics.CARD_BOTTOM.dp),
        contentAlignment = Alignment.BottomStart,
    ) {
        Box(
            modifier =
                Modifier.graphicsLayer {
                    alpha = menu
                    val scale = 0.85f + 0.15f * menu
                    scaleX = scale
                    scaleY = scale
                    transformOrigin = TransformOrigin(originX.coerceIn(0f, 1f), 1f)
                },
        ) {
            NewTabKindSheetContent(isDark = isDark, onIncognito = {}, onRegular = {})
        }
    }
}

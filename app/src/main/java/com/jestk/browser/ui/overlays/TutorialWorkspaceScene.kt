package com.jestk.browser.ui.overlays

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jestk.browser.R
import com.jestk.browser.domain.model.Workspace
import com.jestk.browser.ui.navigation.getContrastingFolderContentColor
import com.jestk.browser.ui.theme.DarkBackground
import com.jestk.browser.ui.theme.DarkOnSurface
import com.jestk.browser.ui.theme.DarkSurface
import com.jestk.browser.ui.theme.LocalProstoTypography
import com.jestk.browser.ui.theme.ProstoCanvasFloor
import com.jestk.browser.ui.theme.ProstoDarkText
import com.jestk.browser.ui.theme.ProstoMutedSlate
import com.jestk.browser.ui.overlays.MockDockMetrics as M

internal object WorkspaceSceneMetrics {
    /** Card size relative to the screen at rest, close to the real overview card on a phone. */
    const val CARD_SCALE = 0.62f
    const val CARD_GAP = 16f
    const val CARD_TOP = 64f
    const val SCENE_HEIGHT = 222f

    /** Corner radius before scaling, so the card shows about 16dp. */
    const val CARD_CORNER = 26f
}

/**
 * [overview] 0 = the app at rest, 1 = the workspaces overview; [pan] 0..1 moves from Personal to
 * Work; [lift] (dp) raises the cards with the finger during the swipe up, then they settle.
 */
internal data class WorkspaceSceneFrame(
    val overview: Float,
    val pan: Float,
    val activeIndex: Int,
    val touch: TouchFrame?,
    val lift: Float = 0f,
)

internal data class CardPlacement(val centerX: Float, val centerY: Float, val scale: Float)

internal const val WORKSPACE_SCENE_DURATION_MS = 8000

/**
 * Workspaces story: swipe up on the dock and the app shrinks into its card, like Android Recents,
 * with the next workspace peeking from the right; swipe the cards to centre Work and tap it: the
 * card grows back into the app, now with Work's tabs and its amber dock.
 */
internal fun workspaceSceneFrame(p: Float, sceneWidth: Float): WorkspaceSceneFrame {
    val open = eased(p, 0.10f, 0.30f)
    val close = eased(p, 0.65f, 0.80f)
    val pan = eased(p, 0.41f, 0.53f)
    val lift = SWIPE_LIFT * eased(p, 0.10f, 0.24f) * (1f - eased(p, 0.26f, 0.34f))
    val base = WorkspaceSceneFrame(open * (1f - close), pan, activeIndex = if (p >= 0.64f) 1 else 0, touch = null, lift = lift)
    // The finger holds the dock of the shrinking card while it swipes up.
    val held = workspaceCardPlacement(0, base, sceneWidth)
    val swipeX = sceneWidth / 2 + (sceneWidth * 0.35f - sceneWidth / 2) * held.scale
    val swipeY = held.centerY + (M.dockCenterY - MockScreenHeight / 2) * held.scale
    val cardY = cardCenterY()
    val touches =
        listOf(
            TouchFrame(swipeX, swipeY, visible(p, 0.04f, 0.30f), pressed(p, 0.08f, 0.26f)),
            TouchFrame(lerp(sceneWidth * 0.78f, sceneWidth * 0.22f, eased(p, 0.40f, 0.52f)), cardY, visible(p, 0.36f, 0.56f), pressed(p, 0.39f, 0.54f)),
            TouchFrame(sceneWidth / 2, cardY, visible(p, 0.58f, 0.74f), pressed(p, 0.62f, 0.66f), phase(p, 0.63f, 0.72f)),
        )
    return base.copy(touch = touches.current())
}

/** How far the cards follow the finger up before settling into the overview. */
private const val SWIPE_LIFT = 46f

private fun cardCenterY(): Float = WorkspaceSceneMetrics.CARD_TOP + MockScreenHeight * WorkspaceSceneMetrics.CARD_SCALE / 2

/** Where workspace [index] is drawn: the centred one fills the scene at rest and shrinks into a card. */
internal fun workspaceCardPlacement(index: Int, frame: WorkspaceSceneFrame, sceneWidth: Float): CardPlacement {
    val scale = lerp(1f, WorkspaceSceneMetrics.CARD_SCALE, frame.overview)
    val step = sceneWidth * scale + WorkspaceSceneMetrics.CARD_GAP
    return CardPlacement(
        centerX = sceneWidth / 2 + (index - frame.pan) * step,
        centerY = lerp(MockScreenHeight / 2, cardCenterY(), frame.overview) - frame.lift,
        scale = scale,
    )
}

private val PersonalSites = DockSceneSites.take(5)
private const val PERSONAL_ACTIVE = 1
private val WorkSites = FolderSceneMembers + MockSite("N", "Notion", "notion.so", Color(0xFF37352F)) + DockSceneSites[3]
private const val WORK_ACTIVE = 0

private class MockWorkspace(
    val name: String,
    val color: Color,
    val dock: DockSceneFrame,
    val activeTab: MockSite,
    val activeIndex: Int,
    val tabCount: Int,
)

private fun staticDock(sites: List<MockSite>, widths: List<Float>, active: Int, capsuleWidth: Float): DockSceneFrame {
    val entries = sites.mapIndexed { i, site -> StripEntry.TabEntry(site, widths[i], if (i == active) 1f else 0f) }
    return DockSceneFrame(entries, scroll = 0f, maxScroll = (entries.contentWidth() - capsuleWidth).coerceAtLeast(0f), touch = null)
}

@Composable
internal fun TutorialWorkspaceScene(progress: Float, isDark: Boolean, modifier: Modifier = Modifier) {
    val personalName = stringResource(R.string.workspace_personal)
    val workName = stringResource(R.string.workspace_work)
    val title = stringResource(R.string.workspaces_title)
    MockSceneBox(modifier) { capsuleWidth ->
        val sceneWidth = capsuleWidth + 2 * (M.SIDE_BUTTON + M.ROW_SPACING)
        val personalWidths = rememberPillWidths(PersonalSites, capsuleWidth)
        val workWidths = rememberPillWidths(WorkSites, capsuleWidth)
        val workspaces =
            listOf(
                MockWorkspace(
                    personalName,
                    MockPersonalColor,
                    staticDock(PersonalSites, personalWidths, PERSONAL_ACTIVE, capsuleWidth),
                    PersonalSites[PERSONAL_ACTIVE],
                    PERSONAL_ACTIVE,
                    PersonalSites.size,
                ),
                MockWorkspace(
                    workName,
                    Color(Workspace.DEFAULT_WORK.color),
                    staticDock(WorkSites, workWidths, WORK_ACTIVE, capsuleWidth),
                    WorkSites[WORK_ACTIVE],
                    WORK_ACTIVE,
                    WorkSites.size,
                ),
            )
        val frame = workspaceSceneFrame(progress, sceneWidth)
        Box(modifier = Modifier.fillMaxWidth().height(WorkspaceSceneMetrics.SCENE_HEIGHT.dp).clipToBounds()) {
            OverviewHeader(title = title, alpha = phase(frame.overview, 0.6f, 1f), isDark = isDark)
            workspaces.forEachIndexed { i, workspace ->
                MockWorkspaceCard(
                    workspace = workspace,
                    placement = workspaceCardPlacement(i, frame, sceneWidth),
                    chromeAlpha = phase(frame.overview, 0.6f, 1f),
                    cardAlpha = frame.overview,
                    isActive = i == frame.activeIndex,
                    sceneWidth = sceneWidth,
                    isDark = isDark,
                )
            }
            frame.touch?.let { TouchIndicator(it, isDark) }
        }
    }
}

@Composable
private fun OverviewHeader(title: String, alpha: Float, isDark: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth().height(28.dp).graphicsLayer { this.alpha = alpha },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = title,
            style = LocalProstoTypography.current.labelMd.copy(fontSize = 17.sp, fontWeight = FontWeight.SemiBold),
            color = if (isDark) DarkOnSurface else ProstoDarkText,
        )
        Box(
            modifier = Modifier.size(28.dp).clip(CircleShape).background(if (isDark) DarkSurface else Color.White),
            contentAlignment = Alignment.Center,
        ) {
            Icon(imageVector = Icons.Default.Close, contentDescription = null, tint = ProstoMutedSlate, modifier = Modifier.size(16.dp))
        }
    }
}

/**
 * One workspace: its screen drawn at full size and scaled into the card ([cardAlpha] fades in the
 * card surface), with the overview chrome (name capsule and options above, active tab and its
 * index below) fading in around it with [chromeAlpha] once the card has nearly settled.
 */
@Composable
private fun MockWorkspaceCard(
    workspace: MockWorkspace,
    placement: CardPlacement,
    chromeAlpha: Float,
    cardAlpha: Float,
    isActive: Boolean,
    sceneWidth: Float,
    isDark: Boolean,
) {
    val cardWidth = sceneWidth * placement.scale
    val cardHeight = MockScreenHeight * placement.scale
    val left = placement.centerX - cardWidth / 2
    val top = placement.centerY - cardHeight / 2
    val shape = RoundedCornerShape((WorkspaceSceneMetrics.CARD_CORNER * cardAlpha).dp)
    val canvas = if (isDark) DarkBackground else ProstoCanvasFloor
    val borderColor = if (isActive) workspace.color else ProstoMutedSlate.copy(alpha = 0.25f)
    val textColor = if (isDark) DarkOnSurface else ProstoDarkText

    Box(
        modifier =
            Modifier
                .offset((placement.centerX - sceneWidth / 2).dp, (placement.centerY - MockScreenHeight / 2).dp)
                .size(sceneWidth.dp, MockScreenHeight.dp)
                .graphicsLayer {
                    scaleX = placement.scale
                    scaleY = placement.scale
                }
                .clip(shape)
                .background(canvas.copy(alpha = cardAlpha))
                .border(if (isActive) 3.dp else 1.5.dp, borderColor.copy(alpha = borderColor.alpha * cardAlpha), shape),
    ) {
        MockScreen(frame = workspace.dock, isDark = isDark, workspaceColor = workspace.color)
    }

    Row(
        modifier =
            Modifier
                .offset(left.dp, (top - 30f).dp)
                .width(cardWidth.dp)
                .height(24.dp)
                .graphicsLayer { alpha = chromeAlpha },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Box(modifier = Modifier.clip(CircleShape).background(workspace.color).padding(horizontal = 12.dp, vertical = 4.dp)) {
            Text(
                text = workspace.name,
                style = LocalProstoTypography.current.labelMd.copy(fontWeight = FontWeight.Bold),
                color = getContrastingFolderContentColor(workspace.color),
            )
        }
        Icon(imageVector = Icons.Default.MoreVert, contentDescription = null, tint = ProstoMutedSlate, modifier = Modifier.size(16.dp))
    }

    Row(
        modifier =
            Modifier
                .offset(left.dp, (top + cardHeight + 6f).dp)
                .width(cardWidth.dp)
                .height(18.dp)
                .padding(horizontal = 4.dp)
                .graphicsLayer { alpha = chromeAlpha },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MockFavicon(workspace.activeTab, isDark)
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = workspace.activeTab.title,
            style = LocalProstoTypography.current.bodySm.copy(fontWeight = FontWeight.Medium),
            color = textColor,
            maxLines = 1,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = stringResource(R.string.workspace_tab_index_counter, workspace.activeIndex + 1, workspace.tabCount),
            style = LocalProstoTypography.current.labelSm.copy(fontSize = 11.sp),
            color = ProstoMutedSlate,
        )
    }
}

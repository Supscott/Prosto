package com.jestk.browser.ui.overlays

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import com.jestk.browser.R
import com.jestk.browser.domain.model.TabFolder
import com.jestk.browser.ui.theme.ProstoBrandPrimary
import com.jestk.browser.ui.overlays.MockDockMetrics as M

internal val DockSceneSites =
    listOf(
        MockSite("W", "Wikipedia", "wikipedia.org", Color(0xFF475569)),
        MockSite("Y", "YouTube", "youtube.com", Color(0xFFEF4444)),
        MockSite("G", "GitHub", "github.com", Color(0xFF24292F)),
        MockSite("F", "Figma", "figma.com", Color(0xFFA259FF)),
        MockSite("R", "Reddit", "reddit.com", Color(0xFFFF4500)),
        MockSite("S", "Spotify", "spotify.com", Color(0xFF1DB954)),
    )

internal val FolderSceneMembers =
    listOf(
        MockSite("J", "Jira", "jira.com", Color(0xFF2684FF)),
        MockSite("S", "Slack", "slack.com", Color(0xFF611F69)),
    )

internal val FolderSceneActive = MockSite("Y", "YouTube", "youtube.com", Color(0xFFEF4444))
internal val FolderSceneIncoming = MockSite("W", "Wikipedia", "wikipedia.org", Color(0xFF475569))
internal val FolderSceneWorkColor = Color(TabFolder.PALETTE[3])
internal val FolderSceneSecondColor = Color(TabFolder.PALETTE[6])

/** Lifted tab scale at rest above the strip, as in TabStripDragOverlays. */
private const val LIFT_SCALE = 1.08f

/** Distance the tab is pulled up in the dock story, a little past the 50dp remove threshold. */
private const val PULL_UP = 60f
private const val REMOVE_THRESHOLD = 50f

/**
 * Dock story ([DOCK_SCENE_DURATION_MS]): tap YouTube and it grows into the active pill while GitHub shrinks back to a
 * circle; swipe the strip to scroll to the tabs behind the edge; tap + and the new tab opens at
 * the end as the active pill, pushing the others behind the left edge; finally long-press Reddit,
 * pull it up above the dock until it turns into a cross and let go: it is closed and the gap closes.
 */
internal fun dockSceneFrame(
    p: Float,
    capsuleWidth: Float,
    pillWidths: List<Float>,
    home: MockSite,
    homePillWidth: Float,
): DockSceneFrame {
    val switch = eased(p, 0.14f, 0.23f)
    val swipe = eased(p, 0.33f, 0.45f)
    val newTabAppear = eased(p, 0.52f, 0.60f)
    val newTab = eased(p, 0.55f, 0.63f)
    val lift = eased(p, 0.69f, 0.72f)
    val pull = eased(p, 0.73f, 0.81f)
    val vanish = eased(p, 0.84f, 0.88f)
    val closeGap = eased(p, 0.85f, 0.93f)
    val removed = DOCK_SCENE_REMOVED_INDEX
    val expansion = listOf(0f, switch * (1f - newTab), 1f - switch, 0f, 0f, 0f)
    val entries =
        DockSceneSites.mapIndexed { i, site ->
            StripEntry.TabEntry(
                site = site,
                pillWidth = pillWidths[i],
                expansion = expansion[i],
                appear = if (i == removed) 1f - closeGap else 1f,
                lifted = i == removed && p >= 0.69f,
            )
        } + StripEntry.TabEntry(home, homePillWidth, newTab, appear = newTabAppear)
    val maxScroll = (entries.contentWidth() - capsuleWidth).coerceAtLeast(0f)
    val scroll = maxScroll * swipe
    val y = M.dockCenterY
    val youTubeX = M.PAD_START + M.TAB + M.SPACING + M.TAB / 2
    val addX = capsuleWidth - M.ADD_BUTTON_END - M.TAB / 2
    val removedX = entries.centerOf(removed, scroll)
    val removedY = y - PULL_UP * pull
    val remove = (PULL_UP * pull / REMOVE_THRESHOLD).coerceAtMost(1f)
    val touches =
        listOf(
            TouchFrame(youTubeX, y, visible(p, 0.06f, 0.26f), pressed(p, 0.12f, 0.17f), phase(p, 0.13f, 0.24f)),
            TouchFrame(lerp(capsuleWidth - 70f, 70f, swipe), y, visible(p, 0.28f, 0.48f), pressed(p, 0.31f, 0.47f)),
            TouchFrame(addX, y, visible(p, 0.49f, 0.64f), pressed(p, 0.52f, 0.56f), phase(p, 0.53f, 0.62f)),
            TouchFrame(removedX, removedY, visible(p, 0.65f, 0.88f), pressed(p, 0.68f, 0.84f)),
        )
    val dragged =
        if (p >= 0.69f && vanish < 1f) {
            DraggedTab(
                site = DockSceneSites[removed],
                x = removedX,
                y = removedY,
                scale = (lerp(1f, LIFT_SCALE, lift) - remove * 0.20f) * (1f - 0.5f * vanish),
                alpha = (1f - remove * 0.15f) * (1f - vanish),
                remove = remove,
            )
        } else {
            null
        }
    return DockSceneFrame(entries, scroll, maxScroll, touches.current(), dragged)
}

internal const val DOCK_SCENE_REMOVED_INDEX = 4

/**
 * Folder story ([FOLDER_SCENE_DURATION_MS]): long-press Wikipedia, drag it onto the collapsed "Work" chip (arrow right),
 * which blinks while the tab hovers over it, and let go: the tab drops into the folder and the dock
 * closes the gap. A tap then expands the folder (arrow turns left) and its tabs, Wikipedia now the
 * last of them, unfold underlined in the folder colour. An untitled folder shows the circle form.
 */
internal fun folderSceneFrame(
    p: Float,
    capsuleWidth: Float,
    workTitle: String,
    workChipWidth: Float,
    activePillWidth: Float,
): DockSceneFrame {
    val lift = eased(p, 0.10f, 0.14f)
    val carry = eased(p, 0.15f, 0.26f)
    val blink = if (p >= 0.26f && p < 0.40f) 1f - 0.6f * pulse(p, 0.26f, FOLDER_BLINK_HALF_PERIOD) else 1f
    val drop = eased(p, 0.40f, 0.45f)
    val closeGap = eased(p, 0.40f, 0.48f)
    val joined = p >= 0.48f
    val expanded = eased(p, 0.60f, 0.74f)
    val members =
        FolderSceneMembers.mapIndexed { i, site ->
            StripEntry.TabEntry(
                site = site,
                pillWidth = M.TAB,
                expansion = 0f,
                appear = expanded,
                folderColor = FolderSceneWorkColor,
                isLastInFolder = !joined && i == FolderSceneMembers.lastIndex,
            )
        }
    val incoming =
        StripEntry.TabEntry(
            site = FolderSceneIncoming,
            pillWidth = M.TAB,
            expansion = 0f,
            appear = if (joined) expanded else 1f - closeGap,
            folderColor = FolderSceneWorkColor.takeIf { joined },
            isLastInFolder = joined,
            lifted = !joined && p >= 0.10f,
        )
    val entries =
        listOf(StripEntry.FolderEntry(workTitle, FolderSceneWorkColor, workChipWidth, expanded, blink)) +
            members +
            listOf(
                incoming,
                StripEntry.TabEntry(FolderSceneActive, activePillWidth, expansion = 1f),
                StripEntry.FolderEntry("", FolderSceneSecondColor, M.TAB, expanded = 0f),
            )
    val maxScroll = (entries.contentWidth() - capsuleWidth).coerceAtLeast(0f)
    val y = M.dockCenterY
    val chipX = M.PAD_START + workChipWidth / 2
    val incomingX = M.PAD_START + workChipWidth + M.SPACING + M.TAB / 2
    val dragX = lerp(incomingX, chipX, carry)
    val dragY = y - 4f * lift
    val touches =
        listOf(
            TouchFrame(dragX, dragY, visible(p, 0.04f, 0.46f), pressed(p, 0.08f, 0.40f)),
            TouchFrame(chipX, y, visible(p, 0.52f, 0.76f), pressed(p, 0.58f, 0.63f), phase(p, 0.59f, 0.70f)),
        )
    val dragged =
        if (p >= 0.10f && drop < 1f) {
            DraggedTab(
                site = FolderSceneIncoming,
                x = dragX,
                y = dragY,
                scale = lerp(1f, LIFT_SCALE, lift) * (1f - 0.5f * drop),
                alpha = 1f - drop,
            )
        } else {
            null
        }
    return DockSceneFrame(entries, scroll = 0f, maxScroll = maxScroll, touch = touches.current(), dragged = dragged)
}

internal const val DOCK_SCENE_DURATION_MS = 9000
internal const val FOLDER_SCENE_DURATION_MS = 8000

/** FolderDockIndicator blinks with a 300 ms ramp, expressed as a share of the folder story. */
private const val FOLDER_BLINK_HALF_PERIOD = 300f / FOLDER_SCENE_DURATION_MS

@Composable
internal fun TutorialDockScene(progress: Float, isDark: Boolean, modifier: Modifier = Modifier) {
    val home = MockSite(letter = "", title = stringResource(R.string.tab_home_title), domain = "", color = ProstoBrandPrimary, isHome = true)
    MockSceneBox(modifier) { capsuleWidth ->
        val widths = rememberPillWidths(DockSceneSites + home, capsuleWidth)
        val frame = dockSceneFrame(progress, capsuleWidth, widths.dropLast(1), home, widths.last())
        MockDockScene(frame = frame, isDark = isDark)
    }
}

@Composable
internal fun TutorialFolderScene(progress: Float, isDark: Boolean, modifier: Modifier = Modifier) {
    val workTitle = stringResource(R.string.workspace_work)
    MockSceneBox(modifier) { capsuleWidth ->
        val chipWidth = rememberFolderChipWidth(workTitle)
        val pillWidth = rememberPillWidths(listOf(FolderSceneActive), capsuleWidth).first()
        val frame = folderSceneFrame(progress, capsuleWidth, workTitle, chipWidth, pillWidth)
        MockDockScene(frame = frame, isDark = isDark)
    }
}

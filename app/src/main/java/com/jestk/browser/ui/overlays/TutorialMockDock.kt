package com.jestk.browser.ui.overlays

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Search
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp as lerpColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material3.Icon
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.jestk.browser.domain.model.Workspace
import com.jestk.browser.ui.navigation.tabStripEdgeFade
import com.jestk.browser.ui.theme.DarkDockSurface
import com.jestk.browser.ui.theme.DarkOnSurfaceVariant
import com.jestk.browser.ui.theme.DarkSurface
import com.jestk.browser.ui.theme.ProstoBorders
import com.jestk.browser.ui.theme.ProstoMutedSlate
import com.jestk.browser.ui.theme.ProstoShapes
import com.jestk.browser.ui.theme.prostoFloatingSurface
import com.jestk.browser.ui.overlays.MockDockMetrics as M

/**
 * A non-interactive copy of the browser screen bottom: a page card and the floating dock with the
 * search and menu buttons, plus a finger showing the gesture. It draws only, so taps fall through
 * to the stories screen. [frame] is computed from the story progress by the scene.
 */
@Composable
internal fun MockDockScene(
    frame: DockSceneFrame,
    isDark: Boolean,
    modifier: Modifier = Modifier,
    workspaceColor: Color = MockPersonalColor,
) {
    Box(modifier = modifier.fillMaxWidth().height(MockScreenHeight.dp)) {
        MockScreen(frame = frame, isDark = isDark, workspaceColor = workspaceColor)
        frame.dragged?.let { MockDraggedTab(it, isDark) }
        frame.touch?.let { TouchIndicator(it, isDark) }
    }
}

/** Default Personal workspace colour: the real dock is always tinted by the active workspace. */
internal val MockPersonalColor = Color(Workspace.DEFAULT_PERSONAL.color)

/** Height of the page card plus the dock row. */
internal val MockScreenHeight: Float get() = M.dockCenterY + M.DOCK_ROW_HEIGHT / 2

/** The page card and the dock row of one workspace, without the finger. */
@Composable
internal fun MockScreen(
    frame: DockSceneFrame,
    isDark: Boolean,
    workspaceColor: Color,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        MockPageCard(color = frame.entries.pageColor(), isDark = isDark)
        Spacer(modifier = Modifier.height(M.PAGE_TO_DOCK.dp))
        MockDockRow(frame = frame, isDark = isDark, style = mockDockStyle(isDark, workspaceColor))
    }
}

/** Dock colours tinted by the workspace, with the same alphas as BottomBar. */
internal data class MockDockStyle(
    val dockBg: Color,
    val dockBorder: BorderStroke,
    val pillBg: Color,
    /** Top-to-bottom gradient of the active pill outline. */
    val pillBorderColors: List<Color>,
)

internal fun mockDockStyle(isDark: Boolean, workspaceColor: Color): MockDockStyle {
    val baseDock = if (isDark) DarkDockSurface.copy(alpha = 0.80f) else Color.White.copy(alpha = 0.80f)
    val basePill = if (isDark) DarkDockSurface else Color.White
    val pillBorderColors =
        if (isDark) {
            listOf(workspaceColor.copy(alpha = 0.45f), workspaceColor.copy(alpha = 0.15f))
        } else {
            listOf(workspaceColor.copy(alpha = 0.35f), workspaceColor.copy(alpha = 0.10f))
        }
    return MockDockStyle(
        dockBg = workspaceColor.copy(alpha = if (isDark) 0.14f else 0.10f).compositeOver(baseDock),
        dockBorder = ProstoBorders.floatingBorder(isDark = isDark, workspaceColor = workspaceColor),
        pillBg = workspaceColor.copy(alpha = if (isDark) 0.08f else 0.04f).compositeOver(basePill),
        pillBorderColors = pillBorderColors,
    )
}

/** Measures the scene width once, so a scene can lay out its frame in dp. */
@Composable
internal fun MockSceneBox(
    modifier: Modifier = Modifier,
    content: @Composable (capsuleWidth: Float) -> Unit,
) {
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        content(M.capsuleWidth(maxWidth.value))
    }
}

/** The page of the active tab: a card tinted with the site colour, blending while tabs switch. */
@Composable
private fun MockPageCard(color: Color, isDark: Boolean) {
    val shape = RoundedCornerShape(20.dp)
    val line = if (isDark) Color.White.copy(alpha = 0.10f) else Color.Black.copy(alpha = 0.07f)
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(M.PAGE_HEIGHT.dp)
                .prostoFloatingSurface(
                    shape = shape,
                    isDark = isDark,
                    backgroundColor = if (isDark) DarkSurface else Color.White,
                    elevation = 4.dp,
                ),
    ) {
        Box(
            modifier = Modifier.fillMaxWidth().height(44.dp).background(color.copy(alpha = 0.88f)),
            contentAlignment = Alignment.CenterStart,
        ) {
            Box(
                modifier =
                    Modifier
                        .padding(start = 16.dp)
                        .size(width = 72.dp, height = 10.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.85f)),
            )
        }
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            listOf(1f, 0.82f, 0.64f).forEach { fraction ->
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth(fraction)
                            .height(9.dp)
                            .clip(CircleShape)
                            .background(line),
                )
            }
        }
    }
}

@Composable
private fun MockDockRow(frame: DockSceneFrame, isDark: Boolean, style: MockDockStyle) {
    val dockBg = style.dockBg
    val tint = if (isDark) DarkOnSurfaceVariant else ProstoMutedSlate
    val density = LocalDensity.current
    Row(
        modifier = Modifier.fillMaxWidth().height(M.DOCK_ROW_HEIGHT.dp),
        horizontalArrangement = Arrangement.spacedBy(M.ROW_SPACING.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier =
                Modifier
                    .weight(1f)
                    .height(M.DOCK_HEIGHT.dp)
                    .prostoFloatingSurface(shape = ProstoShapes.Dock, isDark = isDark, backgroundColor = dockBg, border = style.dockBorder),
        ) {
            val fadePx = with(density) { 32.dp.toPx() }
            val pinnedPx = with(density) { M.PAD_END.dp.toPx() }
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .clip(ProstoShapes.Dock)
                        .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                        .tabStripEdgeFade(
                            isFadeActive = frame.maxScroll > 0f,
                            canScrollBackward = frame.scroll > 0.5f,
                            canScrollForward = frame.scroll < frame.maxScroll - 0.5f,
                            leftFadeWidthPx = fadePx,
                            rightFadeWidthPx = fadePx,
                            rightPinnedOffsetPx = pinnedPx,
                        ),
            ) {
                Row(
                    modifier =
                        Modifier
                            .fillMaxHeight()
                            .wrapContentWidth(Alignment.Start, unbounded = true)
                            .offset { IntOffset(((M.PAD_START - frame.scroll).dp).roundToPx(), 0) },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    frame.entries.forEach { entry -> MockStripSlot(entry, isDark, style) }
                }
            }
            Box(
                modifier = Modifier.align(Alignment.CenterEnd).padding(end = M.ADD_BUTTON_END.dp).size(M.TAB.dp),
                contentAlignment = Alignment.Center,
            ) {
                Icon(imageVector = Icons.Default.Add, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
            }
        }
        MockSideButton(Icons.Default.Search, style, tint, isDark)
        MockSideButton(Icons.Default.Menu, style, tint, isDark)
    }
}

@Composable
private fun MockSideButton(icon: ImageVector, style: MockDockStyle, tint: Color, isDark: Boolean) {
    Box(
        modifier =
            Modifier
                .size(width = M.SIDE_BUTTON.dp, height = M.DOCK_HEIGHT.dp)
                .prostoFloatingSurface(shape = ProstoShapes.Card, isDark = isDark, backgroundColor = style.dockBg, border = style.dockBorder),
        contentAlignment = Alignment.Center,
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
    }
}

/** One strip slot: the room the entry takes (shrinking with [StripEntry.appear]) plus the folder underline. */
@Composable
private fun MockStripSlot(entry: StripEntry, isDark: Boolean, style: MockDockStyle) {
    val slot = entry.slot
    val underline: Color? = when (entry) {
        is StripEntry.TabEntry -> entry.folderColor?.copy(alpha = entry.appear)
        is StripEntry.FolderEntry -> entry.color.copy(alpha = entry.expanded).takeIf { entry.title.isNotBlank() }
    }
    Box(
        modifier =
            Modifier
                .width(slot.dp)
                .fillMaxHeight()
                .drawBehind {
                    if (underline == null || underline.alpha <= 0f) return@drawBehind
                    val y = size.height - 3.dp.toPx()
                    val (startX, endX) = when (entry) {
                        is StripEntry.FolderEntry -> 4.dp.toPx() to size.width
                        is StripEntry.TabEntry ->
                            0f to if (entry.isLastInFolder) size.width - (M.SPACING + 4f).dp.toPx() * entry.appear else size.width
                    }
                    if (endX > startX) {
                        drawLine(underline, Offset(startX, y), Offset(endX, y), strokeWidth = 2.5.dp.toPx(), cap = StrokeCap.Round)
                    }
                },
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            modifier =
                Modifier
                    .wrapContentWidth(Alignment.Start, unbounded = true)
                    .graphicsLayer {
                        alpha = when (entry) {
                            is StripEntry.TabEntry -> if (entry.lifted) 0f else entry.appear
                            is StripEntry.FolderEntry -> entry.blink
                        }
                        val scale = lerp(0.6f, 1f, entry.appear)
                        scaleX = scale
                        scaleY = scale
                    },
        ) {
            when (entry) {
                is StripEntry.TabEntry -> MockTab(entry, isDark, style.pillBg, style.pillBorderColors)
                is StripEntry.FolderEntry -> MockFolderChip(entry)
            }
        }
    }
}

/** A finger: a soft disc that squeezes while pressed and sends out a ring on a tap. */
@Composable
internal fun TouchIndicator(touch: TouchFrame, isDark: Boolean) {
    val base = if (isDark) Color.White else Color(0xFF0F172A)
    val sizeDp = 64f
    Canvas(
        modifier =
            Modifier
                .offset((touch.x - sizeDp / 2).dp, (touch.y - sizeDp / 2).dp)
                .size(sizeDp.dp)
                .graphicsLayer { alpha = touch.alpha },
    ) {
        val radius = 17.dp.toPx() * lerp(1f, 0.82f, touch.press)
        if (touch.ripple > 0f && touch.ripple < 1f) {
            drawCircle(
                color = base.copy(alpha = 0.35f * (1f - touch.ripple)),
                radius = lerp(17f, 31f, touch.ripple).dp.toPx(),
                style = Stroke(width = 2.dp.toPx()),
            )
        }
        drawCircle(color = base.copy(alpha = if (isDark) 0.30f else 0.18f), radius = radius)
        drawCircle(color = base.copy(alpha = 0.40f), radius = radius, style = Stroke(width = 1.5.dp.toPx()))
    }
}

/** Colour of the page on screen: the active tabs' colours weighted by how far each is expanded. */
private fun List<StripEntry>.pageColor(): Color {
    val tabs = filterIsInstance<StripEntry.TabEntry>().filter { it.expansion * it.appear > 0f }
    if (tabs.isEmpty()) return Color.Gray
    var color = tabs.first().site.color
    var weight = tabs.first().expansion * tabs.first().appear
    tabs.drop(1).forEach { tab ->
        val w = tab.expansion * tab.appear
        color = lerpColor(color, tab.site.color, w / (weight + w))
        weight += w
    }
    return color
}

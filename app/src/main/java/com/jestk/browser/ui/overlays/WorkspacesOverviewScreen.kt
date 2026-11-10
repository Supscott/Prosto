package com.jestk.browser.ui.overlays

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PageSize
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.PagerSnapDistance
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import androidx.compose.ui.zIndex
import com.jestk.browser.R
import com.jestk.browser.domain.model.Workspace
import com.jestk.browser.ui.theme.DarkOnSurface
import com.jestk.browser.ui.theme.DarkSurface
import com.jestk.browser.ui.theme.LocalIsDarkTheme
import com.jestk.browser.ui.theme.LocalProstoTypography
import com.jestk.browser.ui.theme.ProstoBorders
import com.jestk.browser.ui.theme.ProstoDarkText
import com.jestk.browser.ui.viewmodel.BrowserUiEvent
import com.jestk.browser.ui.viewmodel.BrowserUiState
import kotlinx.coroutines.launch

private const val PAGE_SPACING_DP = 16
private const val CARD_HEIGHT_FRACTION = 0.76f

/** Caps the card width so the neighbouring cards always peek in at both edges. */
private const val MAX_CARD_WIDTH_FRACTION = 0.72f
private const val CARD_HEADER_HEIGHT_DP = 52
private const val CARD_FOOTER_HEIGHT_DP = 44
private const val DISMISS_THRESHOLD_DP = 100
private const val DISMISS_FLING_VELOCITY_PX_PER_S = 1000f
private const val SELECT_FULL_DRAG_DP = 240
private const val SELECTION_DURATION_MS = 340
private const val WIDE_FALLBACK_ASPECT = 9f / 16f

/** Header and card chrome appear over the last part of the open transition. */
private fun chromeAlphaFor(progress: Float): Float = ((progress - 0.45f) / 0.55f).coerceIn(0f, 1f)

/** Neighbour cards slide in with the live card; they only fade at the very start. */
private fun neighbourAlphaFor(progress: Float): Float = (progress * 2.5f).coerceIn(0f, 1f)

/** Plain holder so layout callbacks can share coordinates without triggering recomposition. */
private class CoordinatesHolder {
    var root: LayoutCoordinates? = null
    var pagerOrigin: Offset = Offset.Zero
}

/**
 * Android Recents-style Workspaces Overview Screen: a horizontal carousel of workspace cards with
 * a trailing '+' card to add workspaces, and an options sheet to edit or delete.
 *
 * The transition is driven by [switcher] ([WorkspaceSwitcherState.progress]): the live app content
 * shrinks into the active card's slot and the whole carousel is transformed with it, so opening
 * from the dock follows the finger seamlessly. The active card is a transparent window over the
 * live content.
 *
 * - Tap / swipe down on the active card (or back / close) reverses the same transition.
 * - Tap / swipe down on another card expands that card's snapshot to full screen, then switches.
 */
@Composable
fun WorkspacesOverviewScreen(
    uiState: BrowserUiState,
    onEvent: (BrowserUiEvent) -> Unit,
    modifier: Modifier = Modifier,
    switcher: WorkspaceSwitcherState = rememberWorkspaceSwitcherState(),
) {
    val isDark = LocalIsDarkTheme.current
    val typography = LocalProstoTypography.current
    val density = LocalDensity.current
    val coroutineScope = rememberCoroutineScope()
    val currentOnEvent by rememberUpdatedState(onEvent)

    val workspaces = uiState.workspaces
    val activeWorkspaceId = uiState.activeWorkspaceId
    val activeWorkspaceIndex = remember(workspaces, activeWorkspaceId) {
        workspaces.indexOfFirst { it.id == activeWorkspaceId }.coerceAtLeast(0)
    }
    val currentActiveIndex by rememberUpdatedState(activeWorkspaceIndex)

    // Pager item count = workspaces.size + 1 (trailing '+' card)
    val pageCount = workspaces.size + 1
    val pagerState = rememberPagerState(initialPage = activeWorkspaceIndex, pageCount = { pageCount })
    LaunchedEffect(activeWorkspaceIndex) {
        if (activeWorkspaceIndex in 0 until pageCount && pagerState.currentPage != activeWorkspaceIndex) {
            pagerState.scrollToPage(activeWorkspaceIndex)
        }
    }

    // Programmatic opens (hint tap, tests) shrink the live app on their own;
    // a dock drag already drives the progress with the finger.
    LaunchedEffect(Unit) {
        if (!switcher.isDragging && switcher.progress.value < 1f) switcher.animateOpen()
    }

    // The live card follows the active card while the carousel scrolls.
    DisposableEffect(switcher, pagerState) {
        switcher.activeCardScrollX = {
            val info = pagerState.layoutInfo
            (currentActiveIndex - pagerState.currentPage - pagerState.currentPageOffsetFraction) *
                (info.pageSize + info.pageSpacing)
        }
        onDispose { switcher.activeCardScrollX = { 0f } }
    }

    var editingWorkspace by remember { mutableStateOf<Workspace?>(null) }
    var isNewWorkspaceSheetVisible by remember { mutableStateOf(false) }
    var workspaceToDelete by remember { mutableStateOf<Workspace?>(null) }

    val coordinates = remember { CoordinatesHolder() }
    var overviewSize by remember { mutableStateOf(androidx.compose.ui.unit.IntSize.Zero) }
    val isSettled by remember { derivedStateOf { switcher.progress.value >= 1f && !switcher.isDragging } }
    var isClosing by remember { mutableStateOf(false) }

    // Selecting another workspace: its snapshot scales from its own center to full screen.
    var selectingWorkspaceId by remember { mutableStateOf<String?>(null) }
    val selectProgress = remember { Animatable(0f) }
    var selectionTranslation by remember { mutableStateOf(Offset.Zero) }
    val cardCenters = remember { mutableStateMapOf<String, Offset>() }
    val selectFullDragPx = with(density) { SELECT_FULL_DRAG_DP.dp.toPx() }
    val dismissThresholdPx = with(density) { DISMISS_THRESHOLD_DP.dp.toPx() }

    val closeToActive: (scrollFirst: Boolean) -> Unit = { scrollFirst ->
        if (!isClosing && selectingWorkspaceId == null) {
            isClosing = true
            coroutineScope.launch {
                if (scrollFirst && pagerState.currentPage != activeWorkspaceIndex) {
                    pagerState.animateScrollToPage(activeWorkspaceIndex)
                }
                switcher.animateClose()
                currentOnEvent(BrowserUiEvent.CloseWorkspacesOverview)
            }
        }
    }
    val beginSelection: (String) -> Unit = { targetId ->
        selectingWorkspaceId = targetId
        val rootCenter = Offset(overviewSize.width / 2f, overviewSize.height / 2f)
        selectionTranslation = rootCenter - (cardCenters[targetId] ?: rootCenter)
        switcher.liveAlphaOverride = { 1f - selectProgress.value }
    }
    val completeSelection: (String) -> Unit = { targetId ->
        coroutineScope.launch {
            selectProgress.animateTo(1f, tween(SELECTION_DURATION_MS, easing = FastOutSlowInEasing))
            // The expanded snapshot now covers the screen: restore the live app to full screen
            // underneath, so the exit fade reveals the switched workspace in place.
            switcher.liveAlphaOverride = null
            switcher.progress.snapTo(0f)
            if (targetId != activeWorkspaceId) currentOnEvent(BrowserUiEvent.SwitchWorkspace(targetId))
            currentOnEvent(BrowserUiEvent.CloseWorkspacesOverview)
        }
    }
    val cancelSelection: () -> Unit = {
        coroutineScope.launch {
            selectProgress.animateTo(0f, spring(Spring.DampingRatioNoBouncy, Spring.StiffnessMedium))
            selectingWorkspaceId = null
            switcher.liveAlphaOverride = null
        }
    }
    val selectWorkspace: (Workspace) -> Unit = { workspace ->
        when {
            isClosing || selectingWorkspaceId != null -> Unit
            workspace.id == activeWorkspaceId -> closeToActive(false)
            isSettled -> {
                beginSelection(workspace.id)
                completeSelection(workspace.id)
            }
        }
    }

    BackHandler { closeToActive(true) }

    val selectionEase = selectProgress.value
    val selectionActive = selectingWorkspaceId != null
    // Chrome fades out faster than the selected card reaches full screen.
    val selectionChromeFade = (1f - selectionEase * 1.6f).coerceIn(0f, 1f)

    // Card size keeps the root aspect ratio so the live app scales into it uniformly.
    val configuration = LocalConfiguration.current
    val rootSize = switcher.rootSize
    val rootAspect = if (rootSize.width > 0 && rootSize.height > 0) {
        rootSize.width.toFloat() / rootSize.height.toFloat()
    } else {
        configuration.screenWidthDp.toFloat() / configuration.screenHeightDp.toFloat()
    }
    val cardAspect = if (rootAspect > 16f / 9f) WIDE_FALLBACK_ASPECT else rootAspect

    Box(
        modifier = modifier
            .fillMaxSize()
            .onGloballyPositioned { coordinates.root = it }
            .onSizeChanged {
                overviewSize = it
                if (switcher.rootSize == androidx.compose.ui.unit.IntSize.Zero) switcher.rootSize = it
            }
            // Blocks touches from reaching the (faded) dock and page underneath.
            .pointerInput(Unit) { awaitEachGesture { awaitFirstDown(requireUnconsumed = false) } }
            .statusBarsPadding()
            .navigationBarsPadding()
            .testTag("workspaces_overview_screen"),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 14.dp)
                    .graphicsLayer {
                        alpha = chromeAlphaFor(switcher.progress.value) * selectionChromeFade
                    },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.workspaces_title),
                    style = typography.headlineMd,
                    color = if (isDark) DarkOnSurface else ProstoDarkText,
                    fontWeight = FontWeight.Bold,
                )

                Surface(
                    shape = CircleShape,
                    color = if (isDark) DarkSurface else Color.White,
                    border = ProstoBorders.floatingBorder(isDark),
                    shadowElevation = 2.dp,
                    modifier = Modifier.size(36.dp),
                ) {
                    IconButton(
                        onClick = { closeToActive(true) },
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("workspaces_overview_close_button"),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = stringResource(R.string.folder_close),
                            tint = if (isDark) DarkOnSurface else ProstoDarkText,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }

            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .testTag("workspaces_carousel_box"),
                contentAlignment = Alignment.Center,
            ) {
                val headerPx = with(density) { CARD_HEADER_HEIGHT_DP.dp.toPx() }
                val chromePx = with(density) { (CARD_HEADER_HEIGHT_DP + CARD_FOOTER_HEIGHT_DP).dp.toPx() }
                val maxHeightPx = with(density) { maxHeight.toPx() }
                val maxWidthPx = with(density) { maxWidth.toPx() }
                // The preview keeps the root aspect; header and footer sit outside it.
                val cardWidthPx = minOf(
                    (maxHeightPx * CARD_HEIGHT_FRACTION - chromePx) * cardAspect,
                    maxWidthPx * MAX_CARD_WIDTH_FRACTION,
                )
                val cardHeightPx = cardWidthPx / cardAspect
                // Each page is exactly one card wide and centred, so neighbours show their edges.
                val sidePaddingDp = with(density) { ((maxWidthPx - cardWidthPx) / 2f).toDp() }
                val itemHeightPx = cardHeightPx + chromePx
                val cardWidthDp = with(density) { cardWidthPx.toDp() }
                val itemHeightDp = with(density) { itemHeightPx.toDp() }
                val cardHeightDp = with(density) { cardHeightPx.toDp() }

                HorizontalPager(
                    state = pagerState,
                    beyondViewportPageCount = 2,
                    pageSize = PageSize.Fixed(with(density) { cardWidthPx.toDp() }),
                    // Free scroll: a fling glides across several cards, then centres the nearest one.
                    flingBehavior = PagerDefaults.flingBehavior(
                        state = pagerState,
                        pagerSnapDistance = PagerSnapDistance.atMost(pageCount),
                    ),
                    userScrollEnabled = isSettled && !selectionActive && !isClosing,
                    contentPadding = PaddingValues(horizontal = sidePaddingDp),
                    pageSpacing = PAGE_SPACING_DP.dp,
                    modifier = Modifier
                        .fillMaxSize()
                        // Measured outside the carousel transform below.
                        .onGloballyPositioned { coords ->
                            val root = coordinates.root ?: return@onGloballyPositioned
                            val origin = root.localPositionOf(coords, Offset.Zero)
                            coordinates.pagerOrigin = origin
                            val centerX = origin.x + coords.size.width / 2f
                            val previewTop = origin.y + (coords.size.height - itemHeightPx) / 2f + headerPx
                            switcher.restCardRect = Rect(
                                centerX - cardWidthPx / 2f,
                                previewTop,
                                centerX + cardWidthPx / 2f,
                                previewTop + cardHeightPx,
                            )
                        }
                        // Outside the transform too, so finger travel is measured in screen space.
                        .carouselSwipeDownGesture(
                            onStart = start@{
                                if (isClosing || selectionActive || switcher.isDragging) return@start false
                                val workspace = workspaces.getOrNull(pagerState.currentPage) ?: return@start false
                                if (workspace.id != activeWorkspaceId) {
                                    if (!isSettled) return@start false
                                    beginSelection(workspace.id)
                                }
                                true
                            },
                            onDrag = { downPx ->
                                coroutineScope.launch {
                                    val target = selectingWorkspaceId
                                    if (target == null) {
                                        switcher.progress.snapTo((1f - downPx / switcher.fullDragDistancePx()).coerceIn(0f, 1f))
                                    } else {
                                        selectProgress.snapTo((downPx / selectFullDragPx).coerceIn(0f, 1f))
                                    }
                                }
                            },
                            onEnd = { downPx, velocity, cancelled ->
                                val complete = !cancelled &&
                                    (downPx >= dismissThresholdPx || velocity > DISMISS_FLING_VELOCITY_PX_PER_S)
                                val target = selectingWorkspaceId
                                when {
                                    target != null && complete -> completeSelection(target)
                                    target != null -> cancelSelection()
                                    complete -> closeToActive(false)
                                    else -> coroutineScope.launch { switcher.animateOpen() }
                                }
                            },
                        )
                        // Same transform as the live card: maps the active card rect onto the
                        // live rect, so neighbours travel with the shrinking screen.
                        .graphicsLayer {
                            if (selectingWorkspaceId != null) return@graphicsLayer
                            val active = switcher.activeCardRect
                            if (active.width <= 0f) return@graphicsLayer
                            val live = switcher.liveRect
                            val k = live.width / active.width
                            val origin = coordinates.pagerOrigin
                            transformOrigin = TransformOrigin(0f, 0f)
                            scaleX = k
                            scaleY = k
                            translationX = live.center.x - origin.x - k * (active.center.x - origin.x)
                            translationY = live.center.y - origin.y - k * (active.center.y - origin.y)
                        },
                ) { page ->
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        if (page < workspaces.size) {
                            val workspace = workspaces[page]
                            val isActive = workspace.id == activeWorkspaceId
                            val isSelected = selectingWorkspaceId == workspace.id

                            val allTabs = if (uiState.allTabs.isNotEmpty()) uiState.allTabs else uiState.tabs
                            val wsTabs = allTabs.filter { it.workspaceId == workspace.id }
                            val activeTab = if (isActive) {
                                uiState.activeTab ?: wsTabs.find { it.id == uiState.activeTabId } ?: wsTabs.firstOrNull()
                            } else {
                                wsTabs.find { it.isActive }
                                    ?: wsTabs.maxByOrNull { it.lastActiveTimestamp }
                                    ?: wsTabs.firstOrNull()
                            }
                            val activeTabIndex = activeTab?.let { tab ->
                                wsTabs.indexOfFirst { it.id == tab.id }.coerceAtLeast(0)
                            } ?: 0

                            val fullScale = if (overviewSize.width > 0) {
                                maxOf(overviewSize.width / cardWidthPx, overviewSize.height / cardHeightPx)
                            } else 1f
                            val cardScale = when {
                                isSelected -> lerp(1f, fullScale, selectionEase)
                                isActive -> 1f
                                else -> 1f - selectionEase * 0.1f
                            }
                            val cardCornerRadius = if (isSelected) {
                                (WORKSPACE_CARD_CORNER_RADIUS_DP * (1f - selectionEase)).dp
                            } else WORKSPACE_CARD_CORNER_RADIUS_DP.dp
                            val selectionFade = if (isSelected) 1f else 1f - selectionEase
                            val cardTranslation = if (isSelected) selectionTranslation * selectionEase else Offset.Zero

                            WorkspaceOverviewCard(
                                workspace = workspace,
                                isActive = isActive,
                                isDark = isDark,
                                tabCount = wsTabs.size,
                                activeTab = activeTab,
                                activeTabIndex = activeTabIndex,
                                uiState = uiState,
                                cornerRadiusDp = cardCornerRadius,
                                isLiveCard = isActive,
                                onSelect = { selectWorkspace(workspace) },
                                onEdit = { if (!selectionActive && !isClosing) editingWorkspace = workspace },
                                chromeAlpha = {
                                    chromeAlphaFor(switcher.progress.value) *
                                        if (isSelected) selectionChromeFade else selectionFade
                                },
                                modifier = Modifier.size(cardWidthDp, itemHeightDp),
                                previewModifier = Modifier
                                    .zIndex(if (isSelected) 1f else 0f)
                                    .graphicsLayer {
                                        scaleX = cardScale
                                        scaleY = cardScale
                                        alpha = when {
                                            isSelected || isActive -> 1f
                                            else -> neighbourAlphaFor(switcher.progress.value) * selectionFade
                                        }
                                        translationX = cardTranslation.x
                                        translationY = cardTranslation.y
                                        transformOrigin = TransformOrigin.Center
                                    }
                                    .onGloballyPositioned { coords ->
                                        val root = coordinates.root
                                        if (!selectionActive && root != null) {
                                            cardCenters[workspace.id] = root.localPositionOf(
                                                coords,
                                                Offset(coords.size.width / 2f, coords.size.height / 2f),
                                            )
                                        }
                                    },
                            )
                        } else {
                            AddWorkspaceCard(
                                isDark = isDark,
                                onClick = { if (isSettled && !selectionActive) isNewWorkspaceSheetVisible = true },
                                modifier = Modifier
                                    .size(cardWidthDp, cardHeightDp)
                                    .graphicsLayer {
                                        alpha = neighbourAlphaFor(switcher.progress.value) * (1f - selectionEase)
                                    },
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }

        val currentEditing = editingWorkspace
        if (currentEditing != null) {
            WorkspaceEditBottomSheet(
                isDark = isDark,
                workspace = currentEditing,
                isLastWorkspace = workspaces.size <= 1,
                onDismiss = { editingWorkspace = null },
                onSave = { newName, newColor ->
                    onEvent(BrowserUiEvent.UpdateWorkspace(currentEditing.copy(name = newName, color = newColor)))
                    editingWorkspace = null
                },
                onDelete = {
                    workspaceToDelete = currentEditing
                    editingWorkspace = null
                },
            )
        }

        if (isNewWorkspaceSheetVisible) {
            WorkspaceEditBottomSheet(
                isDark = isDark,
                workspace = null,
                isLastWorkspace = false,
                onDismiss = { isNewWorkspaceSheetVisible = false },
                onSave = { name, color ->
                    onEvent(BrowserUiEvent.CreateWorkspace(name, color))
                    isNewWorkspaceSheetVisible = false
                },
            )
        }

        val toDelete = workspaceToDelete
        if (toDelete != null) {
            WorkspaceDeleteConfirmDialog(
                workspaceName = toDelete.name,
                isDark = isDark,
                onConfirm = {
                    onEvent(BrowserUiEvent.DeleteWorkspace(toDelete.id))
                    workspaceToDelete = null
                },
                onDismiss = { workspaceToDelete = null },
            )
        }
    }
}

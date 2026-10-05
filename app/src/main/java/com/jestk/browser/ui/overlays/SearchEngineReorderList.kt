package com.jestk.browser.ui.overlays

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.jestk.browser.domain.model.SearchEngine
import com.jestk.browser.ui.theme.DarkOnSurface
import com.jestk.browser.ui.theme.DarkOnSurfaceVariant
import com.jestk.browser.ui.theme.DarkPrimary
import com.jestk.browser.ui.theme.DarkSurface
import com.jestk.browser.ui.theme.DarkSurfaceVariant
import com.jestk.browser.ui.theme.LocalSereneTypography
import com.jestk.browser.ui.theme.SereneBorders
import com.jestk.browser.ui.theme.SereneBrandPrimary
import com.jestk.browser.ui.theme.SereneDarkText
import com.jestk.browser.ui.theme.SereneMutedSlate
import com.jestk.browser.ui.theme.SerenePaleBlue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.roundToInt

/**
 * State and gesture controller for vertical drag & drop reordering in SearchEngineDropdownCard.
 * Provides real drag & drop with continuous finger tracking, fluid spring animated gap opening
 * under the dragged element, bounds clamping, and bouncy snap landing.
 */
internal class SearchEngineReorderState(
    val slotHeightPx: Float,
    val coroutineScope: CoroutineScope,
    val haptic: HapticFeedback,
    initialList: List<SearchEngine> = SearchEngine.DEFAULT_ENGINES,
    val onReorder: (List<SearchEngine>) -> Unit
) {
    var currentList by mutableStateOf(initialList)
        private set

    var isDragging by mutableStateOf(false)
        private set
    var isDropping by mutableStateOf(false)
        private set
    var draggingEngineId by mutableStateOf<String?>(null)
    var draggingIndex by mutableStateOf<Int?>(null)
    var targetIndex by mutableIntStateOf(-1)
    var dragOffsetY by mutableFloatStateOf(0f)
    val dropAnim = Animatable(0f, visibilityThreshold = 0.5f)

    private var dropJob: Job? = null
    private var pendingDropFinalTarget: Int = -1
    private var pendingDropStartIndex: Int = -1
    private var pendingOnListUpdated: ((List<SearchEngine>) -> Unit)? = null

    fun updateList(newList: List<SearchEngine>) {
        if (!isDragging && !isDropping) {
            currentList = newList
        }
    }

    fun finishPendingDropImmediately() {
        if (isDropping) {
            dropJob?.cancel()
            dropJob = null
            if (pendingDropFinalTarget != -1 && pendingDropStartIndex != -1 && pendingDropFinalTarget != pendingDropStartIndex) {
                if (pendingDropStartIndex in currentList.indices && pendingDropFinalTarget in currentList.indices) {
                    val mutable = currentList.toMutableList()
                    val moved = mutable.removeAt(pendingDropStartIndex)
                    mutable.add(pendingDropFinalTarget, moved)
                    currentList = mutable
                    pendingOnListUpdated?.invoke(mutable)
                    onReorder(mutable)
                }
            }
            pendingDropFinalTarget = -1
            pendingDropStartIndex = -1
            pendingOnListUpdated = null
            draggingEngineId = null
            draggingIndex = null
            targetIndex = -1
            dragOffsetY = 0f
            coroutineScope.launch {
                dropAnim.snapTo(0f)
            }
            isDropping = false
            isDragging = false
        }
    }

    fun onDragStart(engineId: String, list: List<SearchEngine>? = null) {
        if (isDropping) {
            finishPendingDropImmediately()
        }
        if (list != null && list.isNotEmpty() && !isDragging) {
            currentList = list
        }
        val startIndex = currentList.indexOfFirst { it.id == engineId }
        if (startIndex == -1) return
        isDragging = true
        isDropping = false
        draggingEngineId = engineId
        draggingIndex = startIndex
        targetIndex = startIndex
        dragOffsetY = 0f
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
    }

    fun onDragStart(index: Int, list: List<SearchEngine>? = null) {
        if (isDropping) {
            finishPendingDropImmediately()
        }
        if (list != null && list.isNotEmpty() && !isDragging) {
            currentList = list
        }
        isDragging = true
        isDropping = false
        draggingEngineId = "index_$index"
        draggingIndex = index
        targetIndex = index
        dragOffsetY = 0f
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
    }

    fun onDrag(deltaY: Float, list: List<SearchEngine>? = null) {
        val id = draggingEngineId ?: return
        if (isDropping) return
        if (list != null && list.isNotEmpty() && !isDragging) {
            currentList = list
        }
        val currentIndex = (if (!id.startsWith("index_")) currentList.indexOfFirst { it.id == id }.takeIf { it != -1 } else null)
            ?: draggingIndex ?: return
        draggingIndex = currentIndex
        dragOffsetY += deltaY

        // Strict clamp drag offset so the item stays within list bounds
        val minOffset = -currentIndex * slotHeightPx
        val maxOffset = (currentList.size - 1 - currentIndex) * slotHeightPx
        dragOffsetY = dragOffsetY.coerceIn(minOffset, maxOffset)

        // Calculate prospective slot index
        val currentSlotFloat = currentIndex + (dragOffsetY / slotHeightPx)
        val newTarget = currentSlotFloat.roundToInt().coerceIn(0, currentList.size - 1)
        if (newTarget != targetIndex) {
            targetIndex = newTarget
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
    }

    fun onDrag(deltaY: Float, listSize: Int) {
        val currentIndex = draggingIndex ?: return
        if (isDropping) return
        dragOffsetY += deltaY

        val minOffset = -currentIndex * slotHeightPx
        val maxOffset = (listSize - 1 - currentIndex) * slotHeightPx
        dragOffsetY = dragOffsetY.coerceIn(minOffset, maxOffset)

        val currentSlotFloat = currentIndex + (dragOffsetY / slotHeightPx)
        val newTarget = currentSlotFloat.roundToInt().coerceIn(0, listSize - 1)
        if (newTarget != targetIndex) {
            targetIndex = newTarget
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
    }

    fun onDragEnd(
        list: List<SearchEngine>? = null,
        onListUpdated: ((List<SearchEngine>) -> Unit)? = null
    ) {
        val id = draggingEngineId ?: return
        if (isDropping) return
        isDragging = false
        val baseList = if (list != null && list.isNotEmpty()) list else currentList
        val startIndex = (if (!id.startsWith("index_")) baseList.indexOfFirst { it.id == id }.takeIf { it != -1 } else null)
            ?: draggingIndex ?: return
        val finalTarget = targetIndex.coerceIn(0, baseList.lastIndex)

        if (finalTarget == startIndex) {
            isDropping = true
            dropJob = coroutineScope.launch {
                dropAnim.snapTo(dragOffsetY)
                dropAnim.animateTo(
                    targetValue = 0f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioLowBouncy,
                        stiffness = Spring.StiffnessMedium
                    )
                )
                draggingEngineId = null
                draggingIndex = null
                targetIndex = -1
                dragOffsetY = 0f
                dropAnim.snapTo(0f)
                isDropping = false
                dropJob = null
            }
            return
        }

        val targetOffset = (finalTarget - startIndex) * slotHeightPx
        pendingDropStartIndex = startIndex
        pendingDropFinalTarget = finalTarget
        pendingOnListUpdated = onListUpdated
        isDropping = true

        dropJob = coroutineScope.launch {
            dropAnim.snapTo(dragOffsetY)
            dropAnim.animateTo(
                targetValue = targetOffset,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioLowBouncy,
                    stiffness = Spring.StiffnessMedium
                )
            )
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)

            if (finalTarget != startIndex && startIndex in baseList.indices && finalTarget in baseList.indices) {
                val mutable = baseList.toMutableList()
                val moved = mutable.removeAt(startIndex)
                mutable.add(finalTarget, moved)
                currentList = mutable
                onListUpdated?.invoke(mutable)
                onReorder(mutable)
            }

            pendingDropStartIndex = -1
            pendingDropFinalTarget = -1
            pendingOnListUpdated = null
            draggingEngineId = null
            draggingIndex = null
            targetIndex = -1
            dragOffsetY = 0f
            dropAnim.snapTo(0f)
            isDropping = false
            dropJob = null
        }
    }

    fun onDragCancel() {
        if (draggingEngineId == null && draggingIndex == null) return
        isDragging = false
        isDropping = true
        dropJob = coroutineScope.launch {
            dropAnim.snapTo(dragOffsetY)
            dropAnim.animateTo(
                targetValue = 0f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioLowBouncy,
                    stiffness = Spring.StiffnessMedium
                )
            )
            draggingEngineId = null
            draggingIndex = null
            targetIndex = -1
            dragOffsetY = 0f
            dropAnim.snapTo(0f)
            isDropping = false
            dropJob = null
        }
    }

    fun getSiblingTargetOffset(engineId: String, list: List<SearchEngine>? = null): Float {
        val id = draggingEngineId ?: return 0f
        if (engineId == id) return 0f
        val listToUse = list ?: currentList
        val activeIndex = listToUse.indexOfFirst { it.id == id }
        val itemIndex = listToUse.indexOfFirst { it.id == engineId }
        if (activeIndex == -1 || itemIndex == -1) return 0f
        val target = targetIndex
        if (target == -1 || target == activeIndex) return 0f

        return if (target > activeIndex) {
            if (itemIndex in (activeIndex + 1)..target) -slotHeightPx else 0f
        } else {
            if (itemIndex in target until activeIndex) slotHeightPx else 0f
        }
    }

    fun getSiblingTargetOffset(index: Int): Float {
        val activeIndex = draggingIndex ?: return 0f
        if (index == activeIndex) return 0f
        val target = targetIndex
        if (target == -1 || target == activeIndex) return 0f

        return if (target > activeIndex) {
            if (index in (activeIndex + 1)..target) -slotHeightPx else 0f
        } else {
            if (index in target until activeIndex) slotHeightPx else 0f
        }
    }
}

@Composable
internal fun SearchEngineDropdownCard(
    selectedEngine: SearchEngine,
    onSelectEngine: (SearchEngine) -> Unit,
    onAddEngineClick: () -> Unit,
    onEditEngineClick: (SearchEngine) -> Unit,
    onDeleteEngineClick: (SearchEngine) -> Unit,
    onReorderEngines: (List<SearchEngine>) -> Unit,
    isDark: Boolean,
    modifier: Modifier = Modifier,
    engines: List<SearchEngine> = SearchEngine.DEFAULT_ENGINES,
    initialEditMode: Boolean = false,
    reorderState: SearchEngineReorderState? = null,
) {
    var isEditMode by remember { mutableStateOf(initialEditMode) }
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current
    val coroutineScope = rememberCoroutineScope()
    val currentOnReorderEngines by rememberUpdatedState(onReorderEngines)

    val itemHeight = 44.dp
    val itemSpacing = 4.dp
    val slotHeightPx = with(density) { (itemHeight + itemSpacing).toPx() }

    val activeReorderState = reorderState ?: remember(slotHeightPx) {
        SearchEngineReorderState(
            slotHeightPx = slotHeightPx,
            coroutineScope = coroutineScope,
            haptic = haptic,
            initialList = engines,
            onReorder = { reordered -> currentOnReorderEngines(reordered) }
        )
    }

    LaunchedEffect(engines) {
        activeReorderState.updateList(engines)
    }

    val bg = if (isDark) DarkSurface else Color.White
    val border = SereneBorders.cardBorder(isDark)

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = bg,
        border = border,
        shadowElevation = 10.dp,
        modifier = modifier.width(if (isEditMode) 250.dp else 210.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp, horizontal = 6.dp),
            verticalArrangement = Arrangement.spacedBy(itemSpacing)
        ) {
            activeReorderState.currentList.forEachIndexed { index, engine ->
                key(engine.id) {
                    ReorderableSearchEngineRow(
                        engine = engine,
                        index = index,
                        selectedEngine = selectedEngine,
                        isEditMode = isEditMode,
                        activeReorderState = activeReorderState,
                        itemHeight = itemHeight,
                        isDark = isDark,
                        onSelectEngine = onSelectEngine,
                        onEditEngineClick = onEditEngineClick,
                        onDeleteEngineClick = onDeleteEngineClick
                    )
                }
            }

            // Divider above bottom row
            HorizontalDivider(
                color = if (isDark) Color(0xFF334155) else Color(0xFFF1F5F9),
                thickness = 1.dp,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
            )

            // Pinned Bottom Actions
            if (!isEditMode) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(38.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { isEditMode = true },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Edit Search Engines",
                            tint = if (isDark) DarkOnSurfaceVariant else SereneMutedSlate,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    IconButton(
                        onClick = onAddEngineClick,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Add Search Engine",
                            tint = if (isDark) DarkPrimary else SereneBrandPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            } else {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (isDark) DarkSurfaceVariant else SerenePaleBlue,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(36.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable {
                            isEditMode = false
                            onReorderEngines(activeReorderState.currentList)
                        }
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = "Done",
                            style = LocalSereneTypography.current.labelMd.copy(fontWeight = FontWeight.Bold),
                            color = if (isDark) DarkPrimary else SereneBrandPrimary
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun SearchEngineDropdownCard(
    selectedEngine: SearchEngine,
    onSelectEngine: (SearchEngine) -> Unit,
    isDark: Boolean,
    modifier: Modifier = Modifier
) {
    SearchEngineDropdownCard(
        selectedEngine = selectedEngine,
        onSelectEngine = onSelectEngine,
        onAddEngineClick = {},
        onEditEngineClick = {},
        onDeleteEngineClick = {},
        onReorderEngines = {},
        isDark = isDark,
        modifier = modifier
    )
}

@Composable
internal fun ReorderableSearchEngineRow(
    engine: SearchEngine,
    index: Int,
    selectedEngine: SearchEngine,
    isEditMode: Boolean,
    activeReorderState: SearchEngineReorderState,
    itemHeight: androidx.compose.ui.unit.Dp,
    isDark: Boolean,
    onSelectEngine: (SearchEngine) -> Unit,
    onEditEngineClick: (SearchEngine) -> Unit,
    onDeleteEngineClick: (SearchEngine) -> Unit
) {
    val isSelected = engine.id == selectedEngine.id
    val isDraggingThis = activeReorderState.draggingEngineId == engine.id
    val isLifted = isDraggingThis && activeReorderState.isDragging
    val isReorderActive = activeReorderState.isDragging || activeReorderState.isDropping

    val targetSiblingOffset = if (isDraggingThis) 0f else activeReorderState.getSiblingTargetOffset(engine.id)
    val siblingOffsetAnim = remember(engine.id) { Animatable(0f, visibilityThreshold = 0.5f) }

    LaunchedEffect(targetSiblingOffset, isReorderActive) {
        if (!isReorderActive) {
            siblingOffsetAnim.snapTo(0f)
        } else {
            siblingOffsetAnim.animateTo(
                targetValue = targetSiblingOffset,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioLowBouncy,
                    stiffness = Spring.StiffnessMedium
                )
            )
        }
    }

    val itemTranslationY = if (isDraggingThis) {
        if (activeReorderState.isDropping) activeReorderState.dropAnim.value else activeReorderState.dragOffsetY
    } else {
        if (isReorderActive) siblingOffsetAnim.value else 0f
    }

    val animatedScale by animateFloatAsState(
        targetValue = if (isLifted) 1.04f else 1.0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "ItemScale_${engine.id}"
    )

    val animatedElevation by animateDpAsState(
        targetValue = if (isLifted) 8.dp else 0.dp,
        animationSpec = spring(
            stiffness = Spring.StiffnessMedium
        ),
        label = "ItemElevation_${engine.id}"
    )

    val itemBg = when {
        isLifted && isDark -> DarkSurfaceVariant
        isLifted && !isDark -> Color.White
        !isEditMode && isSelected && isDark -> DarkSurfaceVariant
        !isEditMode && isSelected && !isDark -> SerenePaleBlue
        else -> Color.Transparent
    }

    val itemBorder = when {
        isLifted -> BorderStroke(1.5.dp, if (isDark) DarkPrimary else SereneBrandPrimary)
        !isEditMode && isSelected -> BorderStroke(1.dp, if (isDark) DarkPrimary.copy(alpha = 0.5f) else SereneBrandPrimary.copy(alpha = 0.5f))
        else -> null
    }

    val textColor = when {
        !isEditMode && isSelected && isDark -> DarkOnSurface
        !isEditMode && isSelected && !isDark -> SereneBrandPrimary
        isDark -> DarkOnSurfaceVariant
        else -> SereneDarkText
    }

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = itemBg,
        border = itemBorder,
        shadowElevation = animatedElevation,
        modifier = Modifier
            .fillMaxWidth()
            .height(itemHeight)
            .zIndex(if (isDraggingThis) 10f else 0f)
            .graphicsLayer {
                translationY = itemTranslationY
                scaleX = animatedScale
                scaleY = animatedScale
            }
            .clip(RoundedCornerShape(14.dp))
            .then(
                if (!isEditMode) {
                    Modifier.clickable { onSelectEngine(engine) }
                } else {
                    Modifier.pointerInput(engine.id) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                            val pointerId = down.id

                            if (activeReorderState.isDropping) {
                                activeReorderState.finishPendingDropImmediately()
                            }

                            // If another item is actively being dragged, ignore
                            if (activeReorderState.isDragging && activeReorderState.draggingEngineId != engine.id) {
                                while (true) {
                                    val event = awaitPointerEvent(pass = PointerEventPass.Initial)
                                    val change = event.changes.firstOrNull { it.id == pointerId } ?: break
                                    if (!change.pressed) break
                                }
                                return@awaitEachGesture
                            }

                            // Hold detection: wait up to 200ms while finger remains pressed
                            val holdTriggered = withTimeoutOrNull(200L) {
                                while (true) {
                                    val event = awaitPointerEvent(pass = PointerEventPass.Initial)
                                    val change = event.changes.firstOrNull { it.id == pointerId } ?: return@withTimeoutOrNull false
                                    if (!change.pressed || activeReorderState.isDragging) {
                                        return@withTimeoutOrNull false
                                    }
                                }
                            } == null

                            if (holdTriggered && !activeReorderState.isDragging) {
                                activeReorderState.onDragStart(engine.id)

                                while (true) {
                                    val event = awaitPointerEvent(pass = PointerEventPass.Initial)
                                    val change = event.changes.firstOrNull { it.id == pointerId }
                                    if (change == null) {
                                        activeReorderState.onDragCancel()
                                        break
                                    }
                                    if (!change.pressed) {
                                        change.consume()
                                        activeReorderState.onDragEnd()
                                        break
                                    }
                                    val deltaY = change.position.y - change.previousPosition.y
                                    change.consume()
                                    activeReorderState.onDrag(deltaY)
                                }
                            }
                        }
                    }
                }
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (isEditMode) {
                // Left 1: Drag handle icon with instant drag
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .pointerInput(engine.id) {
                            awaitEachGesture {
                                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                                val pointerId = down.id
                                down.consume()
                                if (activeReorderState.isDropping) {
                                    activeReorderState.finishPendingDropImmediately()
                                }
                                activeReorderState.onDragStart(engine.id)

                                while (true) {
                                    val event = awaitPointerEvent(pass = PointerEventPass.Initial)
                                    val change = event.changes.firstOrNull { it.id == pointerId }
                                    if (change == null) {
                                        activeReorderState.onDragCancel()
                                        break
                                    }
                                    if (!change.pressed) {
                                        change.consume()
                                        activeReorderState.onDragEnd()
                                        break
                                    }
                                    val deltaY = change.position.y - change.previousPosition.y
                                    change.consume()
                                    activeReorderState.onDrag(deltaY)
                                }
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.DragHandle,
                        contentDescription = "Reorder",
                        tint = if (isLifted) {
                            if (isDark) DarkPrimary else SereneBrandPrimary
                        } else {
                            if (isDark) DarkOnSurfaceVariant else SereneMutedSlate
                        },
                        modifier = Modifier.size(18.dp)
                    )
                }

                // Left 2: Edit pencil button
                IconButton(
                    onClick = { onEditEngineClick(engine) },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Edit Provider",
                        tint = if (isDark) DarkPrimary else SereneBrandPrimary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            SearchEngineBadge(
                engine = engine,
                size = 22.dp,
                isDark = isDark
            )

            Text(
                text = engine.displayName,
                style = LocalSereneTypography.current.bodyMd.copy(
                    fontWeight = if (!isEditMode && isSelected) FontWeight.Bold else FontWeight.Medium
                ),
                color = textColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )

            if (!engine.shortcut.isNullOrBlank()) {
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = if (isDark) DarkSurfaceVariant else Color(0xFFF1F5F9),
                    modifier = Modifier.padding(end = 2.dp)
                ) {
                    Text(
                        text = engine.shortcut,
                        style = LocalSereneTypography.current.labelSm.copy(fontSize = 10.sp, fontWeight = FontWeight.SemiBold),
                        color = SereneMutedSlate,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                    )
                }
            }

            if (!isEditMode) {
                if (isSelected) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Selected",
                        tint = if (isDark) DarkPrimary else SereneBrandPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            } else {
                // Right: Delete trash icon (disabled if currentList.size <= 1)
                val canDelete = activeReorderState.currentList.size > 1
                IconButton(
                    onClick = { onDeleteEngineClick(engine) },
                    enabled = canDelete,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.DeleteOutline,
                        contentDescription = "Delete Provider",
                        tint = if (canDelete) Color(0xFFEF4444) else if (isDark) Color(0xFF475569) else Color(0xFFCBD5E1),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

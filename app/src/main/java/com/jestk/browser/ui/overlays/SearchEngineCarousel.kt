package com.jestk.browser.ui.overlays

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.jestk.browser.R
import com.jestk.browser.domain.model.SearchEngine
import com.jestk.browser.ui.theme.DarkOnSurfaceVariant
import com.jestk.browser.ui.theme.DarkPrimary
import com.jestk.browser.ui.theme.DarkSurface
import com.jestk.browser.ui.theme.ProstoBorders
import com.jestk.browser.ui.theme.ProstoBrandPrimary
import com.jestk.browser.ui.theme.ProstoMutedSlate
import com.jestk.browser.util.LocalVibrationFeedback
import kotlin.math.roundToInt

@Composable
internal fun SearchEngineSelectorButton(
    carouselState: SearchEngineCarouselState,
    isMenuOpen: Boolean,
    isDark: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onDoubleTap: (() -> Unit)? = null,
) {
    val bg = if (isDark) DarkSurface else Color.White
    val border = ProstoBorders.cardBorder(isDark)
    val density = carouselState.density
    val doubleTapAction = onDoubleTap ?: { carouselState.scrollToFirst() }

    val containerWidth by animateDpAsState(
        targetValue = if (carouselState.isScrolling) 160.dp else 68.dp,
        animationSpec =
            spring(
                dampingRatio = 0.85f,
                stiffness = Spring.StiffnessMedium,
            ),
        label = "containerWidth",
    )

    val arrowRotation by animateFloatAsState(
        targetValue = if (isMenuOpen) 180f else 0f,
        animationSpec = tween(durationMillis = 200),
        label = "arrowRotation",
    )

    val arrowAlpha by animateFloatAsState(
        targetValue = if (carouselState.isScrolling) 0f else 1f,
        animationSpec = tween(durationMillis = 150),
        label = "arrowAlpha",
    )

    val fadeAlpha by animateFloatAsState(
        targetValue = if (carouselState.isScrolling) 1f else 0f,
        animationSpec = tween(durationMillis = 150),
        label = "fadeAlpha",
    )

    val restingShiftPx = with(density) { (-11).dp.toPx() } * arrowAlpha
    val selectorDescription = stringResource(R.string.a11y_search_engine_selector)

    Surface(
        shape = CircleShape,
        color = bg,
        border = border,
        shadowElevation = 4.dp,
        modifier =
            modifier
                .width(containerWidth)
                .height(52.dp)
                .semantics(mergeDescendants = true) {
                    role = Role.Button
                    contentDescription = selectorDescription
                    onClick {
                        onClick()
                        true
                    }
                }.searchEngineButtonGestures(
                    carouselState = carouselState,
                    onClick = onClick,
                    onDoubleTap = doubleTapAction,
                ),
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            // Badges Carousel with Edge Fade Mask
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .clip(CircleShape)
                        .graphicsLayer {
                            compositingStrategy = CompositingStrategy.Offscreen
                        }.drawWithContent {
                            drawContent()
                            if (fadeAlpha > 0.01f) {
                                val fadeWidth = 24.dp.toPx() * fadeAlpha
                                val w = size.width
                                if (fadeWidth > 0f && w > 0f) {
                                    drawRect(
                                        brush =
                                            Brush.horizontalGradient(
                                                0f to Color.Transparent,
                                                (fadeWidth / w) to Color.Black,
                                                ((w - fadeWidth) / w) to Color.Black,
                                                1f to Color.Transparent,
                                            ),
                                        blendMode = BlendMode.DstIn,
                                    )
                                }
                            }
                        },
                contentAlignment = Alignment.Center,
            ) {
                val list = if (carouselState.engines.isNotEmpty()) carouselState.engines else SearchEngine.DEFAULT_ENGINES
                val baseIndex = list.indexOfFirst { it.id == carouselState.currentEngine.id }.let { if (it == -1) 0 else it }
                val centerSlotFloat = -carouselState.animOffset.value / carouselState.itemSpacingPx
                val centerSlotInt = centerSlotFloat.roundToInt()
                val scrollProgress = if (carouselState.isScrolling) 1f else fadeAlpha
                val accentColor = if (isDark) DarkPrimary else ProstoBrandPrimary

                for (k in (centerSlotInt - 3)..(centerSlotInt + 3)) {
                    key(k) {
                        val engineIndex = (baseIndex + k).mod(list.size)
                        val engine = list[engineIndex]
                        val xPos = k * carouselState.itemSpacingPx + carouselState.animOffset.value + restingShiftPx

                        val itemScale =
                            SearchEngineCarouselInterpolation.calculateScale(
                                slotIndex = k,
                                centerSlotFloat = centerSlotFloat,
                                scrollProgress = scrollProgress,
                            )
                        val itemAlpha =
                            SearchEngineCarouselInterpolation.calculateAlpha(
                                slotIndex = k,
                                centerSlotFloat = centerSlotFloat,
                                scrollProgress = scrollProgress,
                            )
                        val indicatorAlpha =
                            SearchEngineCarouselInterpolation.calculateIndicatorAlpha(
                                slotIndex = k,
                                centerSlotFloat = centerSlotFloat,
                                scrollProgress = scrollProgress,
                            )

                        Box(
                            modifier =
                                Modifier
                                    .graphicsLayer {
                                        translationX = xPos
                                        scaleX = itemScale
                                        scaleY = itemScale
                                        alpha = itemAlpha
                                    },
                            contentAlignment = Alignment.Center,
                        ) {
                            // Prosto Canvas active accent ring around badge
                            if (indicatorAlpha > 0.01f) {
                                Box(
                                    modifier =
                                        Modifier
                                            .size(32.dp)
                                            .graphicsLayer { alpha = indicatorAlpha }
                                            .border(
                                                width = 1.5.dp,
                                                color = accentColor,
                                                shape = CircleShape,
                                            ),
                                )
                            }

                            SearchEngineBadge(
                                engine = engine,
                                size = 26.dp,
                                isDark = isDark,
                            )

                            // Prosto Canvas active accent indicator pill below badge
                            if (indicatorAlpha > 0.01f) {
                                Box(
                                    modifier =
                                        Modifier
                                            .offset(y = 18.dp)
                                            .size(width = 8.dp, height = 3.dp)
                                            .graphicsLayer { alpha = indicatorAlpha }
                                            .background(
                                                color = accentColor,
                                                shape = CircleShape,
                                            ),
                                )
                            }
                        }
                    }
                }
            }

            // Dropdown Indicator Arrow
            Icon(
                imageVector = Icons.Default.KeyboardArrowDown,
                contentDescription = null,
                tint = if (isDark) DarkOnSurfaceVariant else ProstoMutedSlate,
                modifier =
                    Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = 10.dp)
                        .size(18.dp)
                        .graphicsLayer {
                            rotationZ = arrowRotation
                            alpha = arrowAlpha
                        },
            )
        }
    }
}

@Composable
internal fun SearchEngineSelectorButton(
    selectedEngine: SearchEngine,
    isMenuOpen: Boolean,
    isDark: Boolean,
    density: Density,
    onClick: () -> Unit,
    onSwipeLeft: () -> Unit,
    onSwipeRight: () -> Unit,
    modifier: Modifier = Modifier,
    engines: List<SearchEngine> = SearchEngine.DEFAULT_ENGINES,
    onSelectEngine: ((SearchEngine) -> Unit)? = null,
    onDoubleTap: (() -> Unit)? = null,
) {
    val coroutineScope = rememberCoroutineScope()
    val vibration = LocalVibrationFeedback.current
    val currentOnSwipeLeft by rememberUpdatedState(onSwipeLeft)
    val currentOnSwipeRight by rememberUpdatedState(onSwipeRight)
    val currentOnSelectEngine by rememberUpdatedState(onSelectEngine)

    val carouselState =
        remember(selectedEngine, density, engines) {
            SearchEngineCarouselState(
                initialEngine = selectedEngine,
                density = density,
                coroutineScope = coroutineScope,
                vibration = vibration,
                engines = engines,
                onEngineSelected = { target ->
                    currentOnSelectEngine?.invoke(target)
                    val list = if (engines.isNotEmpty()) engines else SearchEngine.DEFAULT_ENGINES
                    val oldIdx = list.indexOfFirst { it.id == selectedEngine.id }.let { if (it == -1) 0 else it }
                    val newIdx = list.indexOfFirst { it.id == target.id }.let { if (it == -1) 0 else it }
                    val diff = (newIdx - oldIdx).mod(list.size)
                    if (diff == 1) {
                        currentOnSwipeLeft()
                    } else if (diff == list.size - 1) {
                        currentOnSwipeRight()
                    }
                },
            )
        }

    SearchEngineSelectorButton(
        carouselState = carouselState,
        isMenuOpen = isMenuOpen,
        isDark = isDark,
        onClick = onClick,
        modifier = modifier,
        onDoubleTap = onDoubleTap,
    )
}

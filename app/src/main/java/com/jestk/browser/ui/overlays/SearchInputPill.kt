package com.jestk.browser.ui.overlays

import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imeNestedScroll
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollDispatcher
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.unit.Velocity
import kotlinx.coroutines.launch
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.jestk.browser.R
import com.jestk.browser.domain.model.SearchEngine
import com.jestk.browser.ui.theme.DarkOnSurface
import com.jestk.browser.ui.theme.DarkSurface
import com.jestk.browser.ui.theme.LocalProstoTypography
import com.jestk.browser.ui.theme.ProstoBorders
import com.jestk.browser.ui.theme.ProstoBrandPrimary
import com.jestk.browser.ui.theme.ProstoDarkText
import com.jestk.browser.ui.theme.ProstoMutedSlate
import kotlin.math.abs

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun HeroSearchInputField(
    textFieldValue: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    searchEngine: SearchEngine,
    isDark: Boolean,
    focusRequester: FocusRequester,
    onSubmit: () -> Unit,
    onClear: () -> Unit,
    onVoiceResult: (String) -> Unit,
    carouselState: SearchEngineCarouselState? = null,
    density: Density = LocalDensity.current,
    nestedScrollDispatcher: NestedScrollDispatcher? = null,
    onSwipeLeft: () -> Unit = {},
    onSwipeRight: () -> Unit = {},
    isKeyboardVisible: () -> Boolean = { false },
    onSwipeDown: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val internalDispatcher = remember { NestedScrollDispatcher() }
    val effectiveDispatcher = nestedScrollDispatcher ?: internalDispatcher
    val speechLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val spokenText = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
            if (!spokenText.isNullOrBlank()) {
                onVoiceResult(spokenText)
            }
        }
    }

    val bg = if (isDark) DarkSurface else Color.White
    val border = ProstoBorders.cardBorder(isDark)

    val gestureModifier = if (carouselState != null) {
        Modifier.searchEngineInputGestures(
            carouselState = carouselState,
            enabled = textFieldValue.text.isEmpty()
        )
    } else {
        Modifier.horizontalSwipeCycler(
            density = density,
            enabled = textFieldValue.text.isEmpty(),
            thresholdDp = 64.dp,
            onSwipeLeft = onSwipeLeft,
            onSwipeRight = onSwipeRight
        )
    }

    Surface(
        shape = CircleShape,
        color = bg,
        border = border,
        shadowElevation = 4.dp,
        modifier = modifier
            .height(52.dp)
            .imeNestedScroll()
            .nestedScroll(
                connection = remember { object : NestedScrollConnection {} },
                dispatcher = effectiveDispatcher
            )
            .then(gestureModifier)
            .swipeDownToDismiss(
                density = density,
                nestedScrollDispatcher = effectiveDispatcher,
                isKeyboardVisible = isKeyboardVisible,
                onSwipeDown = onSwipeDown
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 14.dp, end = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // Leading Search Icon
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = null,
                tint = ProstoMutedSlate,
                modifier = Modifier.size(20.dp)
            )

            // Input Text Field
            BasicTextField(
                value = textFieldValue,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = LocalProstoTypography.current.bodyMd.copy(
                    color = if (isDark) DarkOnSurface else ProstoDarkText
                ),
                cursorBrush = SolidColor(ProstoBrandPrimary),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Uri,
                    imeAction = ImeAction.Go
                ),
                keyboardActions = KeyboardActions(onGo = { onSubmit() }),
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .focusRequester(focusRequester),
                decorationBox = { innerTextField ->
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        if (textFieldValue.text.isEmpty()) {
                            Text(
                                text = stringResource(R.string.search_with_engine, searchEngine.displayName),
                                style = LocalProstoTypography.current.bodyMd,
                                color = ProstoMutedSlate,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        innerTextField()
                    }
                }
            )

            // Clear Button / Voice Button
            if (textFieldValue.text.isNotEmpty()) {
                IconButton(
                    onClick = {
                        onClear()
                        focusRequester.requestFocus()
                        keyboardController?.show()
                    },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Clear,
                        contentDescription = stringResource(R.string.clear_action),
                        tint = ProstoMutedSlate,
                        modifier = Modifier.size(18.dp)
                    )
                }
            } else {
                IconButton(
                    onClick = {
                        try {
                            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_WEB_SEARCH)
                            }
                            speechLauncher.launch(intent)
                        } catch (_: Exception) {
                            Toast.makeText(context, R.string.toast_voice_input_unavailable, Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Mic,
                        contentDescription = stringResource(R.string.a11y_voice_input),
                        tint = ProstoMutedSlate,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

/**
 * Downward swipe on the search bar. What it does is decided once, at touch-down, from the real
 * keyboard state ([isKeyboardVisible]), so a gesture never changes meaning mid-drag:
 *
 * - **Keyboard visible:** the drag is forwarded to [nestedScrollDispatcher] so
 *   [Modifier.imeNestedScroll] moves the keyboard under the finger, and on release the fling is
 *   dispatched so the IME animation settles to shown or hidden by position and velocity.
 *   [onSwipeDown] is not called: the overlay stays, and a short swipe simply snaps the keyboard back.
 * - **Keyboard hidden:** nothing to drag; [onSwipeDown] fires once the drag crosses 24.dp.
 *
 * Horizontal drags are left to the engine-cycling gesture: the vertical detector yields to them.
 */
@Composable
internal fun Modifier.swipeDownToDismiss(
    density: Density,
    nestedScrollDispatcher: NestedScrollDispatcher? = null,
    isKeyboardVisible: () -> Boolean,
    onSwipeDown: () -> Unit,
): Modifier {
    val currentOnSwipeDown by rememberUpdatedState(onSwipeDown)
    val currentIsKeyboardVisible by rememberUpdatedState(isKeyboardVisible)
    val currentDispatcher by rememberUpdatedState(nestedScrollDispatcher)
    val viewConfig = LocalViewConfiguration.current
    val settleScope = rememberCoroutineScope()
    val thresholdPx = with(density) { 24.dp.toPx() }

    return this.pointerInput(density, viewConfig) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            val dragsKeyboard = currentIsKeyboardVisible()
            val velocityTracker = VelocityTracker()
            velocityTracker.addPosition(down.uptimeMillis, down.position)

            var dispatchedY = 0f
            var offset = Offset.Zero
            var isHorizontalDrag = false
            var dismissed = false

            while (true) {
                val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                if (!change.pressed) break

                velocityTracker.addPosition(change.uptimeMillis, change.position)
                // The node moves with the keyboard while it is dragged, so positions are not comparable
                // across events; only per-event deltas are in a consistent frame.
                offset += change.position - change.previousPosition
                if (!isHorizontalDrag && abs(offset.x) > viewConfig.touchSlop && abs(offset.x) > abs(offset.y)) {
                    isHorizontalDrag = true
                }
                if (isHorizontalDrag) continue

                // Downward displacement only; moving back up unwinds the keyboard drag.
                val targetY = offset.y.coerceAtLeast(0f)
                if (dragsKeyboard) {
                    val delta = targetY - dispatchedY
                    if (delta != 0f) {
                        dispatchedY = targetY
                        currentDispatcher?.dispatchScroll(Offset(0f, delta))
                    }
                } else if (!dismissed && targetY >= thresholdPx) {
                    dismissed = true
                    change.consume()
                    currentOnSwipeDown()
                }
            }

            // Release or cancellation: let the keyboard settle instead of leaving it mid-drag.
            val dispatcher = currentDispatcher
            if (dragsKeyboard && dispatcher != null && dispatchedY > 0f) {
                val velocity = Velocity(0f, velocityTracker.calculateVelocity().y)
                settleScope.launch {
                    val consumed = dispatcher.dispatchPreFling(velocity)
                    dispatcher.dispatchPostFling(consumed, velocity - consumed)
                }
            }
        }
    }
}

private fun NestedScrollDispatcher.dispatchScroll(delta: Offset) {
    val preConsumed = dispatchPreScroll(delta, NestedScrollSource.UserInput)
    dispatchPostScroll(preConsumed, delta - preConsumed, NestedScrollSource.UserInput)
}

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
                            Toast.makeText(context, "Voice input not available", Toast.LENGTH_SHORT).show()
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
 * Detects a downward swipe on the search bar and dispatches vertical drag deltas to the nested
 * scroll hierarchy so the soft keyboard translates smoothly and interactively under the finger.
 *
 * Applied after the horizontal gesture modifiers, it lets a horizontal drag keep the touch
 * slop race (the vertical detector cancels itself), so engine cycling is untouched, and it
 * never claims a tap. As vertical deltas occur, they are dispatched to [nestedScrollDispatcher]
 * as [NestedScrollSource.UserInput], allowing [Modifier.imeNestedScroll] to translate the soft
 * keyboard in real time. The callback fires as soon as a downward drag crosses the initial
 * threshold (24.dp). If the downward drag continues further past the continuous threshold
 * (80.dp), a second action is triggered (transitioning from keyboard hide to overlay dismissal).
 */
@Composable
internal fun Modifier.swipeDownToDismiss(
    density: Density,
    nestedScrollDispatcher: NestedScrollDispatcher? = null,
    onSwipeDown: () -> Unit,
): Modifier {
    val currentOnSwipeDown by rememberUpdatedState(onSwipeDown)
    val currentDispatcher by rememberUpdatedState(nestedScrollDispatcher)
    val viewConfig = LocalViewConfiguration.current
    val thresholdPx = with(density) { 24.dp.toPx() }
    val continuousThresholdPx = with(density) { 80.dp.toPx() }

    return this.pointerInput(density, viewConfig) {
        val velocityTracker = VelocityTracker()
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            velocityTracker.resetTracking()
            velocityTracker.addPosition(down.uptimeMillis, down.position)

            var totalDragY = 0f
            var totalDragX = 0f
            var isHorizontalDrag = false
            var firstTriggered = false
            var secondTriggered = false

            while (true) {
                val event = awaitPointerEvent()
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                if (!change.pressed) {
                    if (!firstTriggered && totalDragY >= thresholdPx) {
                        firstTriggered = true
                        currentOnSwipeDown()
                    } else if (firstTriggered && !secondTriggered && totalDragY >= continuousThresholdPx) {
                        secondTriggered = true
                        currentOnSwipeDown()
                    }
                    break
                }

                velocityTracker.addPosition(change.uptimeMillis, change.position)
                val dragX = change.position.x - change.previousPosition.x
                val dragY = change.position.y - change.previousPosition.y
                totalDragX += dragX

                if (!isHorizontalDrag && abs(totalDragX) > viewConfig.touchSlop && abs(totalDragX) > abs(totalDragY)) {
                    isHorizontalDrag = true
                }

                if (!isHorizontalDrag) {
                    if (dragY < 0f && totalDragY <= 0f) {
                        totalDragY = 0f
                    } else {
                        totalDragY += dragY
                    }

                    if (dragY > 0f || totalDragY > 0f) {
                        val preConsumed = currentDispatcher?.dispatchPreScroll(
                            available = Offset(0f, dragY),
                            source = NestedScrollSource.UserInput
                        ) ?: Offset.Zero

                        val remaining = Offset(0f, dragY) - preConsumed

                        currentDispatcher?.dispatchPostScroll(
                            consumed = preConsumed,
                            available = remaining,
                            source = NestedScrollSource.UserInput
                        )
                    }

                    if (!firstTriggered && totalDragY >= thresholdPx) {
                        firstTriggered = true
                        change.consume()
                        currentOnSwipeDown()
                    } else if (firstTriggered && !secondTriggered && totalDragY >= continuousThresholdPx) {
                        secondTriggered = true
                        change.consume()
                        currentOnSwipeDown()
                    }
                }
            }
        }
    }
}

package com.jestk.browser.ui.overlays

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.jestk.browser.R
import com.jestk.browser.ui.theme.DarkOnSurface
import com.jestk.browser.ui.theme.LocalProstoTypography
import com.jestk.browser.ui.theme.ProstoBrandPrimary
import com.jestk.browser.ui.theme.ProstoDarkText
import com.jestk.browser.ui.theme.ProstoMutedSlate
import com.jestk.browser.ui.theme.prostoSkyBackground
import com.jestk.browser.util.DefaultBrowserHelper
import kotlinx.coroutines.launch

private const val CLOSE_DRAG_THRESHOLD_PX = 160f
private const val PREVIOUS_ZONE_FRACTION = 0.33f

/**
 * Full-screen, Instagram-Stories-style tour: segmented progress at the top, auto-advance,
 * tap the right side for the next story and the left side for the previous one, hold to pause,
 * swipe down to close. The last story waits for the user: it closes only on Done or once Prosto
 * has been made the default browser, never by a tap, a swipe or a close button.
 */
@Composable
internal fun TutorialStoriesScreen(
    isDark: Boolean,
    isDefaultBrowser: Boolean,
    onDefaultBrowserChanged: (Boolean) -> Unit,
    onClose: () -> Unit,
    onComplete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lastIndex = TutorialSlides.lastIndex
    var index by rememberSaveable { mutableIntStateOf(0) }
    var isHeld by remember { mutableStateOf(false) }
    val progress = remember(index) { Animatable(0f) }
    val scope = rememberCoroutineScope()

    // The last story stays open until the user taps Done or actually makes Prosto the default.
    var defaultRequested by rememberSaveable { mutableStateOf(false) }
    val defaultBrowserLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            onDefaultBrowserChanged(DefaultBrowserHelper.isDefaultBrowser(context))
        }
    LaunchedEffect(defaultRequested, isDefaultBrowser) {
        if (defaultRequested && isDefaultBrowser) onComplete()
    }

    LaunchedEffect(index, isHeld) {
        if (isHeld) return@LaunchedEffect
        val remaining = ((1f - progress.value) * TutorialSlides[index].durationMs).toInt()
        progress.animateTo(1f, tween(remaining, easing = LinearEasing))
        if (index < lastIndex) index++
    }

    fun next() {
        if (index < lastIndex) index++
    }

    fun previous() {
        if (index > 0) index-- else scope.launch { progress.snapTo(0f) }
    }

    Box(
        modifier =
            modifier
                .fillMaxSize()
                .prostoSkyBackground(isDark = isDark)
                .testTag("tutorial_stories")
                .pointerInput(Unit) {
                    detectTapGestures(
                        onPress = {
                            isHeld = true
                            tryAwaitRelease()
                            isHeld = false
                        },
                        onTap = { offset ->
                            if (offset.x < size.width * PREVIOUS_ZONE_FRACTION) previous() else next()
                        },
                    )
                }
                .pointerInput(Unit) {
                    var dragged = 0f
                    detectVerticalDragGestures(
                        onDragStart = { dragged = 0f },
                        onDragEnd = { if (dragged > CLOSE_DRAG_THRESHOLD_PX && index < lastIndex) onClose() },
                        onVerticalDrag = { _, delta -> dragged += delta },
                    )
                },
    ) {
        val textColor = if (isDark) DarkOnSurface else ProstoDarkText

        Crossfade(targetState = index, label = "tutorial_slide", modifier = Modifier.fillMaxSize()) { slideIndex ->
            val slide = TutorialSlides[slideIndex]
            // The outgoing story keeps its last frame while it fades out instead of jumping to the new progress.
            val lastProgress = remember { FloatArray(1) }
            if (slideIndex == index) lastProgress[0] = progress.value
            Column(
                modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                val scene = slide.scene
                if (scene != null) {
                    scene(lastProgress[0], isDark)
                } else {
                    SlideGlyph(slide = slide, isDark = isDark)
                }
                Spacer(modifier = Modifier.height(40.dp))
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    SlideText(slide = slide, textColor = textColor)
                    if (slideIndex == lastIndex) {
                        Spacer(modifier = Modifier.height(32.dp))
                        FinalActions(
                            isDefaultBrowser = isDefaultBrowser,
                            onSetDefault = {
                                defaultRequested = true
                                DefaultBrowserHelper.openDefaultBrowserSettings(context, defaultBrowserLauncher)
                            },
                            onDone = onComplete,
                        )
                    }
                }
            }
        }

        Column(modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
                TutorialSlides.indices.forEach { i ->
                    val stepDescription = stringResource(R.string.tutorial_step_a11y, i + 1, TutorialSlides.size)
                    val fill = when {
                        i < index -> 1f
                        i == index -> progress.value
                        else -> 0f
                    }
                    SegmentBar(
                        fill = fill,
                        isDark = isDark,
                        modifier =
                            Modifier
                                .weight(1f)
                                .semantics { contentDescription = stepDescription },
                    )
                }
            }
            Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.End) {
                // The last story is closed only through its own actions, so it has no close button.
                if (index < lastIndex) {
                    Box(
                        modifier = Modifier.size(40.dp).clip(CircleShape).clickable(onClick = onClose),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = stringResource(R.string.tutorial_close),
                            tint = ProstoMutedSlate,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                } else {
                    Spacer(modifier = Modifier.size(40.dp))
                }
            }
        }
    }
}

@Composable
private fun SlideGlyph(slide: TutorialSlide, isDark: Boolean) {
    Box(
        modifier =
            Modifier
                .size(168.dp)
                .clip(RoundedCornerShape(48.dp))
                .background(slide.accent.copy(alpha = if (isDark) 0.22f else 0.16f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = slide.icon,
            contentDescription = null,
            tint = slide.accent,
            modifier = Modifier.size(84.dp),
        )
    }
}

@Composable
private fun SlideText(slide: TutorialSlide, textColor: Color) {
    Text(
        text = stringResource(slide.title),
        style = LocalProstoTypography.current.headlineLg,
        color = textColor,
        textAlign = TextAlign.Center,
    )
    Spacer(modifier = Modifier.height(12.dp))
    Text(
        text = stringResource(slide.body),
        style = LocalProstoTypography.current.bodyMd,
        color = ProstoMutedSlate,
        textAlign = TextAlign.Center,
    )
}

/**
 * The last story's actions. Making Prosto the default is the main action; Done is a quiet text
 * button. Once Prosto already is the default, Done is the only action and becomes the main one.
 */
@Composable
private fun FinalActions(
    isDefaultBrowser: Boolean,
    onSetDefault: () -> Unit,
    onDone: () -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (isDefaultBrowser) {
            PrimaryAction(text = stringResource(R.string.tutorial_done), onClick = onDone, modifier = Modifier.testTag("tutorial_done"))
        } else {
            PrimaryAction(
                text = stringResource(R.string.tutorial_make_default),
                onClick = onSetDefault,
                modifier = Modifier.testTag("tutorial_make_default"),
            )
            TextButton(onClick = onDone, modifier = Modifier.testTag("tutorial_done")) {
                Text(
                    text = stringResource(R.string.tutorial_done),
                    style = LocalProstoTypography.current.labelMd,
                    color = ProstoMutedSlate,
                )
            }
        }
    }
}

@Composable
private fun PrimaryAction(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier =
            modifier
                .clip(CircleShape)
                .background(ProstoBrandPrimary)
                .clickable(onClick = onClick)
                .padding(horizontal = 36.dp, vertical = 14.dp),
    ) {
        Text(
            text = text,
            style = LocalProstoTypography.current.labelMd.copy(fontWeight = FontWeight.SemiBold),
            color = Color.White,
        )
    }
}

@Composable
private fun SegmentBar(
    fill: Float,
    isDark: Boolean,
    modifier: Modifier = Modifier,
) {
    val track = if (isDark) Color.White.copy(alpha = 0.18f) else Color.Black.copy(alpha = 0.10f)
    Box(modifier = modifier.height(3.dp).clip(CircleShape).background(track)) {
        Box(
            modifier =
                Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(fill.coerceIn(0f, 1f))
                    .background(ProstoBrandPrimary),
        )
    }
}

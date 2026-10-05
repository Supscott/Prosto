package com.jestk.browser.ui.overlays

import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
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
import com.jestk.browser.ui.theme.LocalSereneTypography
import com.jestk.browser.ui.theme.SereneBorders
import com.jestk.browser.ui.theme.SereneBrandPrimary
import com.jestk.browser.ui.theme.SereneDarkText
import com.jestk.browser.ui.theme.SereneMutedSlate

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
    onSwipeLeft: () -> Unit = {},
    onSwipeRight: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val keyboardController = LocalSoftwareKeyboardController.current
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
    val border = SereneBorders.cardBorder(isDark)

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
            .then(gestureModifier)
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
                tint = SereneMutedSlate,
                modifier = Modifier.size(20.dp)
            )

            // Input Text Field
            BasicTextField(
                value = textFieldValue,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = LocalSereneTypography.current.bodyMd.copy(
                    color = if (isDark) DarkOnSurface else SereneDarkText
                ),
                cursorBrush = SolidColor(SereneBrandPrimary),
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
                                style = LocalSereneTypography.current.bodyMd,
                                color = SereneMutedSlate,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        innerTextField()
                    }
                }
            )

            // Clear Button / Submit Button / Voice Button
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
                        contentDescription = "Clear",
                        tint = SereneMutedSlate,
                        modifier = Modifier.size(18.dp)
                    )
                }

                IconButton(
                    onClick = onSubmit,
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(SereneBrandPrimary)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = "Go",
                        tint = Color.White,
                        modifier = Modifier.size(17.dp)
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
                        contentDescription = "Voice Input",
                        tint = SereneMutedSlate,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

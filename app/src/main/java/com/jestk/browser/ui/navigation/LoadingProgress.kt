package com.jestk.browser.ui.navigation

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Page load progress of the active tab. It ticks on every WebView progress callback, so it is
 * provided as a [State] and read with `.value` only by the pill that draws it: providing the same
 * State instance never recomposes the other consumers of the app state.
 */
val LocalLoadingProgress = staticCompositionLocalOf<State<Float>> { mutableFloatStateOf(0f) }

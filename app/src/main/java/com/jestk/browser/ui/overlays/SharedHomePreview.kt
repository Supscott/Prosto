package com.jestk.browser.ui.overlays

import android.util.Log
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.jestk.browser.ui.viewmodel.BrowserUiState
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.receiveAsFlow

private const val TAG = "SharedHomePreview"

/** Waits for the Home render to settle (favicons, snapshots) before capturing it again. */
private const val CAPTURE_SETTLE_MS = 80L

/**
 * Home tab previews for the tab grid, one per workspace.
 *
 * Every Home tab of a workspace looks the same, but each grid card used to compose its own live
 * [HomePreview] (a full [com.jestk.browser.ui.views.HomeScreen]): with many tabs, scrolling the
 * grid composed a whole home page per card and stuttered. Now one hidden [HomePreview] per
 * workspace is rendered by [SharedHomePreviewRecorders] and captured to an image that the cards
 * draw with [SharedHomePreviewImage].
 */
@Stable
class SharedHomePreviews {
    private val images = mutableStateMapOf<String, ImageBitmap>()

    fun image(workspaceId: String): ImageBitmap? = images[workspaceId]

    internal fun publish(workspaceId: String, image: ImageBitmap) {
        images[workspaceId] = image
    }

    internal fun remove(workspaceId: String) {
        images.remove(workspaceId)
    }
}

/**
 * Renders the Home page of each of [workspaceIds] once, at the size of a card's preview area,
 * without drawing it on screen, and publishes a capture to [previews] after every redraw.
 */
@Composable
internal fun SharedHomePreviewRecorders(
    uiState: BrowserUiState,
    workspaceIds: Set<String>,
    previewSize: DpSize,
    previews: SharedHomePreviews,
) {
    for (workspaceId in workspaceIds) {
        key(workspaceId) {
            val layer = rememberGraphicsLayer()
            // Conflated: a redraw before the collector starts (the first frame) is kept, not lost.
            val redraws = remember { Channel<Unit>(Channel.CONFLATED) }
            LaunchedEffect(layer) {
                redraws.receiveAsFlow().collectLatest {
                    delay(CAPTURE_SETTLE_MS)
                    try {
                        previews.publish(workspaceId, layer.toImageBitmap())
                    } catch (t: Throwable) {
                        Log.w(TAG, "Failed to capture the Home preview", t)
                    }
                }
            }
            DisposableEffect(workspaceId) {
                onDispose { previews.remove(workspaceId) }
            }
            Box(
                modifier = Modifier
                    .size(previewSize)
                    .clearAndSetSemantics {}
                    // Own layer: scrolling the grid redraws the parent, which must not re-record Home.
                    .graphicsLayer()
                    .drawWithContent {
                        // Recorded only: the hidden render never reaches the screen itself.
                        layer.record { this@drawWithContent.drawContent() }
                        redraws.trySend(Unit)
                    },
            ) {
                HomePreview(uiState = uiState, workspaceId = workspaceId)
            }
        }
    }
}

/** The captured Home page of [workspaceId], filling the card width; empty until the first capture. */
@Composable
internal fun SharedHomePreviewImage(
    previews: SharedHomePreviews,
    workspaceId: String,
    modifier: Modifier = Modifier,
) {
    val image = previews.image(workspaceId) ?: return
    Image(
        bitmap = image,
        contentDescription = null,
        contentScale = ContentScale.FillWidth,
        alignment = Alignment.TopCenter,
        modifier = modifier.fillMaxSize(),
    )
}

/**
 * Size of a card's preview area in the tab grid for a grid [gridWidthPx] wide: three 9:16 cards
 * between 16.dp side padding with 10.dp gaps, minus the 38.dp title strip (see [GridTabCard]).
 */
internal fun homePreviewSize(gridWidthPx: Int, density: Density): DpSize = with(density) {
    val cardWidth = (gridWidthPx.toDp() - 32.dp - 20.dp) / 3
    DpSize(cardWidth, (cardWidth * 16f / 9f - 38.dp).coerceAtLeast(1.dp))
}

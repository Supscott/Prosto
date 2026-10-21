package com.jestk.browser.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.jestk.browser.engine.FaviconManager

/**
 * Favicon slot with a caller supplied placeholder (site initial, lock glyph, ...).
 *
 * Contract:
 * - The placeholder is rendered only when the icon is genuinely unavailable: no source at all, or a
 *   failed load. A cached local icon is known to exist, so it must not flash the placeholder while
 *   the bitmap is decoded - that produced a letter blinking over the site icon every time the dock
 *   was rebuilt.
 * - The placeholder is never drawn underneath a successfully loaded icon.
 */
@Composable
fun SiteFavicon(
    faviconUrl: String?,
    modifier: Modifier = Modifier,
    imageSize: Dp = 18.dp,
    containerSize: Dp = imageSize,
    shape: Shape = RoundedCornerShape(4.dp),
    placeholder: @Composable () -> Unit,
) {
    val imageModel = remember(faviconUrl) { FaviconManager.toImageModel(faviconUrl) }
    val isCachedLocalFile = remember(faviconUrl) { FaviconManager.isValidLocalFavicon(faviconUrl) }
    var isLoaded by remember(faviconUrl) { mutableStateOf(false) }
    var hasFailed by remember(faviconUrl) { mutableStateOf(false) }

    val showPlaceholder = when {
        imageModel == null -> true
        hasFailed -> true
        isLoaded -> false
        // Remote icons are unknown until they arrive, so their placeholder stays visible while loading.
        else -> !isCachedLocalFile
    }

    Box(
        modifier = modifier.size(containerSize),
        contentAlignment = Alignment.Center
    ) {
        if (showPlaceholder) {
            placeholder()
        }
        if (imageModel != null) {
            AsyncImage(
                model = imageModel,
                contentDescription = null,
                modifier = Modifier
                    .size(imageSize)
                    .clip(shape),
                contentScale = ContentScale.Fit,
                onSuccess = { isLoaded = true },
                onError = { hasFailed = true }
            )
        }
    }
}

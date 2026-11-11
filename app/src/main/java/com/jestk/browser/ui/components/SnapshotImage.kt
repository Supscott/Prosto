package com.jestk.browser.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import java.io.File

/**
 * A tab snapshot loaded from [path], or [fallback] when there is none or it cannot be read.
 * Coil reads the file off the main thread; never call `File.exists()` during composition.
 */
@Composable
fun SnapshotImage(
    path: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    alignment: Alignment = Alignment.Center,
    fallback: @Composable () -> Unit,
) {
    var failed by remember(path) { mutableStateOf(path.isNullOrBlank()) }
    if (failed || path == null) {
        fallback()
        return
    }
    AsyncImage(
        model = File(path),
        contentDescription = contentDescription,
        contentScale = ContentScale.Crop,
        alignment = alignment,
        modifier = modifier,
        onError = { failed = true },
    )
}

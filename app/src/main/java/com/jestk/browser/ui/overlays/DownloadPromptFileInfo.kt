package com.jestk.browser.ui.overlays

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jestk.browser.ui.theme.DarkOnSurface
import com.jestk.browser.ui.theme.DarkOnSurfaceVariant
import com.jestk.browser.ui.theme.LocalProstoTypography
import com.jestk.browser.ui.theme.ProstoDarkText
import com.jestk.browser.ui.theme.ProstoMutedSlate

/** A typographic separator, not a sentence: the same in every language. */
private const val DetailsSeparator = " · "

/**
 * Body of the download prompt cards: the file name in the primary text colour (it wraps freely),
 * and below it one line with the size and the host in the muted colour. The size always stays
 * whole; a long host is cut with an ellipsis. Each part is optional and the separator shows only
 * between two.
 */
@Composable
internal fun DownloadPromptFileInfo(
    fileName: String,
    isDark: Boolean,
    modifier: Modifier = Modifier,
    size: String? = null,
    host: String? = null,
) {
    val typography = LocalProstoTypography.current
    val mutedColor = if (isDark) DarkOnSurfaceVariant else ProstoMutedSlate
    val shownSize = size?.takeIf { it.isNotEmpty() }
    val shownHost = host?.takeIf { it.isNotEmpty() }
    Column(modifier = modifier) {
        Text(
            text = fileName,
            style = typography.bodyMd,
            color = if (isDark) DarkOnSurface else ProstoDarkText,
        )
        if (shownSize != null || shownHost != null) {
            Row(modifier = Modifier.padding(top = 2.dp)) {
                if (shownSize != null) {
                    Text(text = shownSize, style = typography.bodyMd, color = mutedColor, maxLines = 1, softWrap = false)
                }
                if (shownSize != null && shownHost != null) {
                    Text(text = DetailsSeparator, style = typography.bodyMd, color = mutedColor, maxLines = 1, softWrap = false)
                }
                if (shownHost != null) {
                    Text(
                        text = shownHost,
                        style = typography.bodyMd,
                        color = mutedColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                }
            }
        }
    }
}

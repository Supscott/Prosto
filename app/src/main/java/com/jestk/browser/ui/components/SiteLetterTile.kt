package com.jestk.browser.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.jestk.browser.engine.SiteIcon
import com.jestk.browser.ui.theme.LocalProstoTypography

/** Tile colours: dark and saturated enough for a white letter in the light and the dark theme. */
private val LetterTilePalette =
    listOf(
        Color(0xFF1E6FD9),
        Color(0xFFC2352F),
        Color(0xFF2E8B4A),
        Color(0xFFC46A00),
        Color(0xFF8E3AA8),
        Color(0xFF00796B),
        Color(0xFF5C6BC0),
        Color(0xFFAD1457),
    )

/**
 * The icon of a site that has none: the first letter of its host (without `www.`) on a colour
 * derived from the host, so the same site always looks the same. [site] is a page URL, a stored
 * icon reference or a bare host. The letter is sized in dp, so a larger font scale cannot push it
 * out of the tile.
 */
@Composable
fun SiteLetterTile(
    site: String?,
    size: Dp,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(size * 0.25f),
) {
    val letter = remember(site) { SiteIcon.letterFor(site) }
    val color = remember(site) { LetterTilePalette[SiteIcon.colorIndexFor(site, LetterTilePalette.size)] }
    val fontSize = with(LocalDensity.current) { (size * 0.55f).toSp() }
    Box(
        modifier = modifier.size(size).clip(shape).background(color),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = letter,
            color = Color.White,
            style = LocalProstoTypography.current.labelSm.copy(fontWeight = FontWeight.Bold, fontSize = fontSize),
            maxLines = 1,
        )
    }
}

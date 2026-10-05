package com.jestk.browser.ui.overlays

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.jestk.browser.domain.model.FavoriteItem
import com.jestk.browser.engine.FaviconManager
import com.jestk.browser.ui.theme.DarkOnSurface
import com.jestk.browser.ui.theme.DarkSurface
import com.jestk.browser.ui.theme.DarkSurfaceVariant
import com.jestk.browser.ui.theme.LocalSereneTypography
import com.jestk.browser.ui.theme.SereneBorders
import com.jestk.browser.ui.theme.SereneBrandPrimary
import com.jestk.browser.ui.theme.SereneCanvasFloor
import com.jestk.browser.ui.theme.SereneDarkText
import com.jestk.browser.ui.theme.SereneShapes

@Composable
internal fun SearchFavoritesSection(
    favorites: List<FavoriteItem>,
    isDark: Boolean,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    if (favorites.isEmpty()) return

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Star,
                contentDescription = null,
                tint = SereneBrandPrimary,
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = "Favorites",
                style = LocalSereneTypography.current.headlineSm.copy(fontSize = 13.sp),
                color = if (isDark) DarkOnSurface else SereneDarkText
            )
        }

        Surface(
            shape = SereneShapes.Card,
            color = if (isDark) DarkSurface else Color.White,
            border = SereneBorders.cardBorder(isDark),
            shadowElevation = 2.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            val rows = favorites.take(8).chunked(4)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                rows.forEach { rowItems ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        rowItems.forEach { item ->
                            SearchFavoriteSquircleCard(
                                item = item,
                                isDark = isDark,
                                onClick = { onSelect(item.url) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                        val emptySlots = 4 - rowItems.size
                        if (emptySlots > 0) {
                            repeat(emptySlots) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun SearchFavoriteSquircleCard(
    item: FavoriteItem,
    isDark: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val squircleBg = if (isDark) DarkSurfaceVariant else SereneCanvasFloor
    val faviconUrl = item.iconUrl ?: FaviconManager.extractFallbackFaviconUrl(item.url)

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = squircleBg,
            border = null,
            shadowElevation = 0.dp,
            modifier = Modifier.size(44.dp)
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                var isImageSuccess by remember(faviconUrl) { mutableStateOf(false) }

                if (!isImageSuccess) {
                    Icon(
                        imageVector = Icons.Default.Public,
                        contentDescription = item.title,
                        tint = SereneBrandPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                }

                val imageModel = remember(faviconUrl) {
                    FaviconManager.toImageModel(faviconUrl)
                }

                if (imageModel != null) {
                    AsyncImage(
                        model = imageModel,
                        contentDescription = item.title,
                        contentScale = ContentScale.Fit,
                        onSuccess = { isImageSuccess = true },
                        onError = { isImageSuccess = false },
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        Text(
            text = item.title.ifEmpty { item.url },
            style = LocalSereneTypography.current.bodySm.copy(
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold
            ),
            color = if (isDark) DarkOnSurface else SereneDarkText,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

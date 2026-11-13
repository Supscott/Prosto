package com.jestk.browser.ui.overlays

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.decode.SvgDecoder
import coil.request.ImageRequest
import com.jestk.browser.domain.model.SearchEngine
import com.jestk.browser.engine.FaviconManager
import com.jestk.browser.ui.theme.DarkPrimary
import com.jestk.browser.ui.theme.DarkSurfaceVariant
import com.jestk.browser.ui.theme.LocalProstoTypography
import com.jestk.browser.ui.theme.ProstoBrandPrimary
import com.jestk.browser.ui.theme.ProstoPaleBlue

@Composable
internal fun SearchEngineBadge(
    engine: SearchEngine,
    modifier: Modifier = Modifier,
    size: Dp = 32.dp,
    isDark: Boolean = false,
) {
    val context = LocalContext.current
    var isImageError by remember(engine.iconUrl) { mutableStateOf(false) }

    val imageModel =
        remember(engine.iconUrl) {
            FaviconManager.toImageModel(engine.iconUrl)
        }

    val imageRequest =
        remember(imageModel, context) {
            ImageRequest
                .Builder(context)
                .data(imageModel)
                .decoderFactory(SvgDecoder.Factory())
                .crossfade(false)
                .build()
        }

    Box(
        modifier =
            modifier
                .size(size)
                .clip(CircleShape)
                .background(if (isDark) DarkSurfaceVariant else ProstoPaleBlue),
        contentAlignment = Alignment.Center,
    ) {
        if (!isImageError && imageModel != null) {
            AsyncImage(
                model = imageRequest,
                contentDescription = engine.displayName,
                contentScale = ContentScale.Fit,
                onError = { isImageError = true },
                onSuccess = { isImageError = false },
                modifier = Modifier.size(size),
            )
        } else {
            Text(
                text = engine.displayName.take(1).uppercase(),
                style =
                    LocalProstoTypography.current.labelSm.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = (size.value * 0.45f).sp,
                    ),
                color = if (isDark) DarkPrimary else ProstoBrandPrimary,
            )
        }
    }
}

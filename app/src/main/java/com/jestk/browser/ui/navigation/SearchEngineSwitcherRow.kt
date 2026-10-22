package com.jestk.browser.ui.navigation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jestk.browser.domain.model.SearchEngine
import com.jestk.browser.ui.overlays.SearchEngineBadge
import com.jestk.browser.ui.components.ProstoToggleSwitch
import com.jestk.browser.ui.theme.DarkOnSurface
import com.jestk.browser.ui.theme.DarkOnSurfaceVariant
import com.jestk.browser.ui.theme.DarkSurface
import com.jestk.browser.ui.theme.DarkSurfaceVariant
import com.jestk.browser.ui.theme.LocalProstoTypography
import com.jestk.browser.ui.theme.ProstoBrandPrimary
import com.jestk.browser.ui.theme.ProstoCardSurface
import com.jestk.browser.ui.theme.ProstoDarkText
import com.jestk.browser.ui.theme.ProstoMutedSlate
import com.jestk.browser.ui.theme.ProstoPaleBlue
import com.jestk.browser.ui.theme.ProstoShapes
import com.jestk.browser.ui.theme.prostoFloatingSurface

@Composable
internal fun SearchEngineSwitcherRow(
    selectedEngine: SearchEngine,
    onSelectEngine: (SearchEngine) -> Unit,
    isDark: Boolean,
    modifier: Modifier = Modifier,
    engines: List<SearchEngine> = SearchEngine.DEFAULT_ENGINES,
    elevation: Dp = 0.dp,
    onEditClick: () -> Unit = {},
    isEditActive: Boolean = false,
) {
    val scrollState = rememberScrollState()

    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.CenterStart
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(end = 52.dp)
                .horizontalScroll(scrollState)
                .padding(start = 16.dp, end = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.Start),
            verticalAlignment = Alignment.CenterVertically
        ) {
            engines.forEach { engine ->
                val isSelected = engine.id == selectedEngine.id
                SearchEngineChip(
                    engine = engine,
                    isSelected = isSelected,
                    isDark = isDark,
                    elevation = elevation,
                    onClick = { onSelectEngine(engine) }
                )
            }
        }

        SearchEngineSwitcherEditButton(
            isDark = isDark,
            isEditActive = isEditActive,
            onClick = onEditClick,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 12.dp)
        )
    }
}

@Composable
internal fun SearchEngineSwitcherEditButton(
    isDark: Boolean,
    isEditActive: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val containerColor = if (isEditActive) {
        if (isDark) DarkSurfaceVariant else ProstoPaleBlue
    } else {
        if (isDark) DarkSurface else Color.White
    }
    val border = if (isEditActive) {
        BorderStroke(1.5.dp, ProstoBrandPrimary)
    } else {
        if (isDark) BorderStroke(1.dp, DarkSurfaceVariant)
        else BorderStroke(1.dp, Color(0x2664748B))
    }
    val iconTint = if (isEditActive) {
        ProstoBrandPrimary
    } else {
        if (isDark) DarkOnSurfaceVariant else ProstoDarkText
    }

    Surface(
        shape = CircleShape,
        color = containerColor,
        border = border,
        shadowElevation = 2.dp,
        onClick = onClick,
        modifier = modifier.size(32.dp)
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Edit,
                contentDescription = "Edit Search Providers",
                tint = iconTint,
                modifier = Modifier.size(15.dp)
            )
        }
    }
}

@Composable
internal fun SearchEngineSwitcherEditPopup(
    expanded: Boolean,
    engines: List<SearchEngine>,
    hiddenEngineIds: Set<String>,
    isDark: Boolean,
    onToggleEngine: (String, Boolean) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val menuBg = if (isDark) DarkSurface else ProstoCardSurface
    val dividerColor = if (isDark) Color(0xFF334155) else Color(0xFFF1F5F9)

    AnimatedVisibility(
        visible = expanded,
        enter = fadeIn(animationSpec = tween(180, easing = FastOutSlowInEasing)) +
                scaleIn(initialScale = 0.90f, animationSpec = tween(200, easing = FastOutSlowInEasing)) +
                slideInVertically(animationSpec = tween(220, easing = FastOutSlowInEasing)) { it / 3 },
        exit = fadeOut(animationSpec = tween(150, easing = FastOutSlowInEasing)) +
                scaleOut(targetScale = 0.90f, animationSpec = tween(160, easing = FastOutSlowInEasing)) +
                slideOutVertically(animationSpec = tween(180, easing = FastOutSlowInEasing)) { it / 3 },
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .width(250.dp)
                .prostoFloatingSurface(
                    shape = ProstoShapes.Card,
                    isDark = isDark,
                    backgroundColor = menuBg,
                    elevation = 12.dp
                )
        ) {
            Column(
                modifier = Modifier.padding(vertical = 8.dp, horizontal = 12.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 6.dp, top = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Search Providers",
                        style = LocalProstoTypography.current.labelMd.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        ),
                        color = if (isDark) DarkOnSurface else ProstoDarkText
                    )
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = ProstoMutedSlate,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                HorizontalDivider(color = dividerColor, thickness = 1.dp)

                // Engine List with bounded vertical scroll
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 280.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    engines.forEachIndexed { index, engine ->
                        val isVisible = engine.id !in hiddenEngineIds
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                SearchEngineBadge(
                                    engine = engine,
                                    size = 22.dp,
                                    isDark = isDark
                                )
                                Text(
                                    text = engine.displayName,
                                    style = LocalProstoTypography.current.bodyMd.copy(
                                        fontWeight = FontWeight.Medium,
                                        fontSize = 13.sp
                                    ),
                                    color = if (isDark) DarkOnSurface else ProstoDarkText,
                                    maxLines = 1
                                )
                            }

                            ProstoToggleSwitch(
                                checked = isVisible,
                                onCheckedChange = { onToggleEngine(engine.id, it) }
                            )
                        }

                        if (index < engines.lastIndex) {
                            HorizontalDivider(color = dividerColor, thickness = 0.5.dp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun SearchEngineChip(
    engine: SearchEngine,
    isSelected: Boolean,
    isDark: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    elevation: Dp = 0.dp
) {
    val containerColor = when {
        isSelected && isDark -> DarkSurfaceVariant
        isSelected && !isDark -> ProstoPaleBlue
        !isSelected && isDark -> DarkSurface
        else -> Color.White
    }

    val border = when {
        isSelected -> BorderStroke(1.5.dp, ProstoBrandPrimary)
        isDark -> BorderStroke(1.dp, DarkSurfaceVariant)
        else -> BorderStroke(1.dp, Color(0x2664748B))
    }

    val contentColor = when {
        isSelected && isDark -> DarkOnSurface
        isSelected && !isDark -> ProstoBrandPrimary
        isDark -> DarkOnSurfaceVariant
        else -> ProstoDarkText
    }

    Surface(
        shape = CircleShape,
        color = containerColor,
        border = border,
        shadowElevation = elevation,
        onClick = onClick,
        modifier = modifier.height(32.dp)
    ) {
        Row(
            modifier = Modifier.padding(start = 6.dp, end = 12.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            SearchEngineBadge(
                engine = engine,
                size = 20.dp,
                isDark = isDark
            )

            Text(
                text = engine.displayName,
                style = LocalProstoTypography.current.labelMd.copy(
                    fontSize = 12.sp,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium
                ),
                color = contentColor
            )
        }
    }
}

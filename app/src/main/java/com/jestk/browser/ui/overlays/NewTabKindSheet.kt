package com.jestk.browser.ui.overlays

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.webkit.WebViewFeature
import com.jestk.browser.R
import com.jestk.browser.ui.theme.DarkOnSurface
import com.jestk.browser.ui.theme.DarkSurface
import com.jestk.browser.ui.theme.LocalProstoTypography
import com.jestk.browser.ui.theme.ProstoBrandPrimary
import com.jestk.browser.ui.theme.ProstoDarkText
import com.jestk.browser.ui.theme.ProstoShapes
import com.jestk.browser.ui.viewmodel.BrowserUiEvent
import com.jestk.browser.ui.viewmodel.OpenIncognitoTab

/** Incognito tabs need WebView profiles; without them the "+" long press does nothing extra. */
internal var isIncognitoSupported: () -> Boolean = {
    runCatching { WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE) }.getOrDefault(false)
}

/**
 * Owns the new-tab choice sheet for a "+" button. [content] receives the long-press handler to
 * give the button (null when incognito is unavailable, so the long press does nothing).
 * [onChosen] runs after either choice, e.g. to close the tab grid.
 */
@Composable
fun NewTabKindHost(
    isDark: Boolean,
    onEvent: (BrowserUiEvent) -> Unit,
    onChosen: () -> Unit = {},
    content: @Composable (onLongClick: (() -> Unit)?) -> Unit,
) {
    var showSheet by remember { mutableStateOf(false) }
    val supported = remember { isIncognitoSupported() }
    content(if (supported) ({ showSheet = true }) else null)
    if (showSheet) {
        NewTabKindSheet(
            isDark = isDark,
            onIncognito = {
                showSheet = false
                onEvent(OpenIncognitoTab)
                onChosen()
            },
            onRegular = {
                showSheet = false
                onEvent(BrowserUiEvent.OpenNewTab(""))
                onChosen()
            },
            onDismiss = { showSheet = false },
        )
    }
}

/** The solid round "+" of the tab grid header; a long press opens the new-tab choice. */
@Composable
fun GridNewTabButton(
    onClick: () -> Unit,
    onLongClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(ProstoBrandPrimary)
                .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Default.Add,
            contentDescription = stringResource(R.string.action_new_tab),
            tint = Color.White,
            modifier = Modifier.size(22.dp),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewTabKindSheet(
    isDark: Boolean,
    onIncognito: () -> Unit,
    onRegular: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = if (isDark) DarkSurface else Color.White,
        dragHandle = { FolderDragHandle() },
        modifier = modifier.testTag("new_tab_kind_sheet"),
    ) {
        NewTabKindSheetContent(isDark = isDark, onIncognito = onIncognito, onRegular = onRegular)
    }
}

/** The sheet body, apart from the bottom-sheet window so previews can render it. */
@Composable
internal fun NewTabKindSheetContent(
    isDark: Boolean,
    onIncognito: () -> Unit,
    onRegular: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .navigationBarsPadding()
                .padding(bottom = 16.dp),
    ) {
        Text(
            text = stringResource(R.string.action_new_tab),
            style = LocalProstoTypography.current.headlineSm,
            color = if (isDark) DarkOnSurface else ProstoDarkText,
            fontWeight = FontWeight.Bold,
        )
        Spacer(modifier = Modifier.padding(top = 12.dp))
        NewTabKindRow(
            icon = Icons.Outlined.VisibilityOff,
            label = stringResource(R.string.new_tab_incognito),
            isDark = isDark,
            onClick = onIncognito,
            modifier = Modifier.testTag("new_tab_kind_incognito"),
        )
        NewTabKindRow(
            icon = Icons.Default.Add,
            label = stringResource(R.string.new_tab_regular),
            isDark = isDark,
            onClick = onRegular,
            modifier = Modifier.testTag("new_tab_kind_regular"),
        )
    }
}

@Composable
private fun NewTabKindRow(
    icon: ImageVector,
    label: String,
    isDark: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(ProstoShapes.Pill)
                .clickable(onClick = onClick)
                .heightIn(min = 52.dp)
                .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = ProstoBrandPrimary,
            modifier = Modifier.size(22.dp),
        )
        Text(
            text = label,
            style = LocalProstoTypography.current.bodyMd.copy(fontWeight = FontWeight.Medium),
            color = if (isDark) DarkOnSurface else ProstoDarkText,
        )
    }
}

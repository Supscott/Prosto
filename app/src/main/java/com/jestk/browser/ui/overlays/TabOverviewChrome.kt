package com.jestk.browser.ui.overlays

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBackIos
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jestk.browser.R
import com.jestk.browser.ui.theme.DarkOnSurface
import com.jestk.browser.ui.theme.DarkSurface
import com.jestk.browser.ui.theme.LocalProstoTypography
import com.jestk.browser.ui.theme.ProstoBorders
import com.jestk.browser.ui.theme.ProstoBrandPrimary
import com.jestk.browser.ui.theme.ProstoDarkText
import com.jestk.browser.ui.theme.ProstoMutedSlate
import com.jestk.browser.ui.theme.ProstoShapes
import com.jestk.browser.ui.viewmodel.BrowserUiEvent

internal const val TAB_OVERVIEW_BOTTOM_BAR_TAG = "tab_overview_bottom_bar"

/** Clear space kept between the last grid row and the floating bottom bar at scroll end. */
internal val TAB_OVERVIEW_BOTTOM_BAR_GAP = 20.dp

@Composable
internal fun TabOverviewTopBar(
    isDark: Boolean,
    onEvent: (BrowserUiEvent) -> Unit,
) {
    // 1. Top Navigation Bar
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(
            onClick = { onEvent(BrowserUiEvent.DismissOverlay) },
            modifier = Modifier.size(40.dp),
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBackIos,
                contentDescription = stringResource(R.string.action_back),
                tint = ProstoBrandPrimary,
                modifier = Modifier.size(20.dp),
            )
        }

        Text(
            text = stringResource(R.string.tabs_overview_title),
            style = LocalProstoTypography.current.headlineSm,
            color = if (isDark) DarkOnSurface else ProstoDarkText,
        )

        Spacer(Modifier.size(40.dp))
    }
}

@Composable
internal fun TabOverviewSubHeader(
    tabSearchQuery: String,
    overviewItems: List<OverviewGridItem>,
    totalTabs: Int,
    isSearchVisible: Boolean,
    onToggleSearch: () -> Unit,
    isDark: Boolean,
    onEvent: (BrowserUiEvent) -> Unit,
) {
    // 2. Sub-Header: Tab Count & Controls
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val totalMatchingTabs =
            if (tabSearchQuery.isNotBlank()) {
                overviewItems.sumOf { item ->
                    when (item) {
                        is OverviewGridItem.TabCard -> 1
                        is OverviewGridItem.FolderCard -> item.tabs.size
                        is OverviewGridItem.FolderHeader -> 0
                        is OverviewGridItem.FolderTabCard -> 1
                        is OverviewGridItem.FolderSpacer -> 0
                    }
                }
            } else {
                totalTabs
            }
        val tabCountText =
            if (tabSearchQuery.isNotBlank()) {
                pluralStringResource(R.plurals.tabs_overview_count_filtered, totalTabs, totalMatchingTabs, totalTabs)
            } else {
                pluralStringResource(R.plurals.workspace_tabs_count, totalTabs, totalTabs)
            }
        Text(
            text = tabCountText,
            style = LocalProstoTypography.current.headlineMd,
            color = if (isDark) DarkOnSurface else ProstoDarkText,
        )

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Search in tabs button
            IconButton(
                onClick = onToggleSearch,
                modifier = Modifier.size(38.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = stringResource(R.string.tabs_overview_search),
                    tint = if (isSearchVisible) ProstoBrandPrimary else ProstoMutedSlate,
                    modifier = Modifier.size(22.dp),
                )
            }

            // Solid "+" Add Tab Button
            NewTabKindHost(
                isDark = isDark,
                onEvent = onEvent,
                onChosen = { onEvent(BrowserUiEvent.DismissOverlay) },
                aboveDock = false,
            ) { onLongClick ->
                GridNewTabButton(
                    onClick = {
                        onEvent(BrowserUiEvent.OpenNewTab(""))
                        onEvent(BrowserUiEvent.DismissOverlay)
                    },
                    onLongClick = onLongClick,
                )
            }
        }
    }
}

@Composable
internal fun ColumnScope.TabOverviewSearchBar(
    visible: Boolean,
    query: String,
    onQueryChange: (String) -> Unit,
    focusRequester: FocusRequester,
    onSearchAction: () -> Unit,
    isDark: Boolean,
) {
    // Expandable in-place tab search filter bar
    AnimatedVisibility(visible = visible) {
        Surface(
            shape = ProstoShapes.Pill,
            color = if (isDark) DarkSurface else Color.White,
            border = ProstoBorders.cardBorder(isDark),
            shadowElevation = 2.dp,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
                    .height(44.dp),
        ) {
            Row(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = null,
                    tint = ProstoMutedSlate,
                    modifier = Modifier.size(18.dp),
                )
                BasicTextField(
                    value = query,
                    onValueChange = { onQueryChange(it) },
                    modifier =
                        Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .focusRequester(focusRequester),
                    singleLine = true,
                    textStyle =
                        LocalProstoTypography.current.bodyMd.copy(
                            color = if (isDark) DarkOnSurface else ProstoDarkText,
                        ),
                    cursorBrush = SolidColor(ProstoBrandPrimary),
                    keyboardOptions =
                        KeyboardOptions(
                            imeAction = ImeAction.Search,
                        ),
                    keyboardActions =
                        KeyboardActions(
                            onSearch = { onSearchAction() },
                        ),
                    decorationBox = { innerTextField ->
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.CenterStart,
                        ) {
                            if (query.isEmpty()) {
                                Text(
                                    text = stringResource(R.string.tabs_overview_filter_hint),
                                    style = LocalProstoTypography.current.bodyMd,
                                    color = ProstoMutedSlate,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            innerTextField()
                        }
                    },
                )
                if (query.isNotEmpty()) {
                    IconButton(
                        onClick = {
                            onQueryChange("")
                            focusRequester.requestFocus()
                        },
                        modifier = Modifier.size(24.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = stringResource(R.string.clear_action),
                            tint = ProstoMutedSlate,
                            modifier = Modifier.size(14.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun TabOverviewBottomBar(
    isDark: Boolean,
    onEvent: (BrowserUiEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    // 4. Floating Bottom Action Bar (Done pill + Close All)
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .testTag(TAB_OVERVIEW_BOTTOM_BAR_TAG)
                .padding(horizontal = 20.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Close All Button
        TextButton(
            onClick = { onEvent(BrowserUiEvent.CloseAllTabs) },
        ) {
            Text(
                text = stringResource(R.string.tabs_overview_close_all),
                color = MaterialTheme.colorScheme.error,
                style = LocalProstoTypography.current.labelMd.copy(fontWeight = FontWeight.SemiBold),
            )
        }

        // Floating Centered "Done" Pill Button
        Surface(
            shape = ProstoShapes.Pill,
            color = if (isDark) DarkSurface else Color.White,
            border = ProstoBorders.floatingBorder(isDark),
            shadowElevation = 4.dp,
            modifier =
                Modifier
                    .height(46.dp)
                    .clickable { onEvent(BrowserUiEvent.DismissOverlay) },
        ) {
            Box(
                modifier = Modifier.padding(horizontal = 26.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(R.string.action_done),
                    color = ProstoBrandPrimary,
                    style =
                        LocalProstoTypography.current.labelMd.copy(
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                        ),
                )
            }
        }
    }
}

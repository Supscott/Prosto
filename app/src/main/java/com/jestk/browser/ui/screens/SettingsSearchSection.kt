package com.jestk.browser.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.jestk.browser.R
import com.jestk.browser.domain.model.SearchEngine
import com.jestk.browser.ui.components.ProstoToggleSwitch
import com.jestk.browser.ui.overlays.SearchEngineBadge
import com.jestk.browser.ui.theme.DarkOnSurface
import com.jestk.browser.ui.theme.DarkSurface
import com.jestk.browser.ui.theme.DarkSurfaceVariant
import com.jestk.browser.ui.theme.LocalProstoTypography
import com.jestk.browser.ui.theme.ProstoBorders
import com.jestk.browser.ui.theme.ProstoBrandPrimary
import com.jestk.browser.ui.theme.ProstoDarkText
import com.jestk.browser.ui.theme.ProstoMutedSlate
import com.jestk.browser.ui.theme.ProstoPaleBlue
import com.jestk.browser.ui.theme.ProstoShapes
import com.jestk.browser.ui.viewmodel.BrowserUiEvent
import com.jestk.browser.ui.viewmodel.BrowserUiState

@Composable
internal fun SettingsSearchSection(
    uiState: BrowserUiState,
    onEvent: (BrowserUiEvent) -> Unit,
    isDark: Boolean,
) {
    var showProvidersDialog by remember { mutableStateOf(false) }

    SettingsSectionHeader(title = stringResource(R.string.settings_section_search))
    InsetGroupedCard(isDark = isDark) {
        // Preferred Search Engine Row with Dropdown Menu
        var searchMenuExpanded by remember { mutableStateOf(false) }
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clickable { searchMenuExpanded = true }
                    .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.weight(1f),
            ) {
                Box(
                    modifier = Modifier.width(24.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                        tint = ProstoBrandPrimary,
                        modifier = Modifier.size(22.dp),
                    )
                }

                Column {
                    Text(
                        text = stringResource(R.string.search_engine_title),
                        style = LocalProstoTypography.current.bodyMd.copy(fontWeight = FontWeight.Medium),
                        color = if (isDark) DarkOnSurface else ProstoDarkText,
                    )
                    Text(
                        text = stringResource(R.string.search_engine_summary),
                        style = LocalProstoTypography.current.bodySm,
                        color = ProstoMutedSlate,
                    )
                }
            }

            Box {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (isDark) DarkSurfaceVariant else ProstoPaleBlue,
                    modifier = Modifier.clickable { searchMenuExpanded = true },
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            text = uiState.settings.searchEngine.displayName,
                            style = LocalProstoTypography.current.labelSm.copy(fontWeight = FontWeight.SemiBold),
                            color = ProstoBrandPrimary,
                        )
                        Icon(
                            imageVector = Icons.Default.ArrowDropDown,
                            contentDescription = null,
                            tint = ProstoBrandPrimary,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }

                DropdownMenu(
                    expanded = searchMenuExpanded,
                    onDismissRequest = { searchMenuExpanded = false },
                    modifier = Modifier.background(if (isDark) DarkSurface else Color.White),
                ) {
                    uiState.searchEngines.forEach { engine ->
                        val isSelected = uiState.settings.searchEngine.id == engine.id
                        DropdownMenuItem(
                            text = {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Text(
                                        text = engine.displayName,
                                        style = LocalProstoTypography.current.bodyMd,
                                        color = if (isDark) DarkOnSurface else ProstoDarkText,
                                    )
                                    if (engine.shortcut != null) {
                                        Text(
                                            text = stringResource(R.string.search_engine_shortcut_compact, engine.shortcut),
                                            style = LocalProstoTypography.current.labelSm,
                                            color = ProstoMutedSlate,
                                        )
                                    }
                                    if (isSelected) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = stringResource(R.string.a11y_selected),
                                            tint = ProstoBrandPrimary,
                                            modifier = Modifier.size(16.dp),
                                        )
                                    }
                                }
                            },
                            onClick = {
                                onEvent(BrowserUiEvent.SetSearchEngine(engine))
                                searchMenuExpanded = false
                            },
                        )
                    }
                }
            }
        }

        HorizontalDivider(
            color = if (isDark) DarkSurfaceVariant else Color(0xFFF1F5F9),
            modifier = Modifier.padding(start = 56.dp),
        )

        // Add Search Engine Row
        SettingClickableRow(
            icon = Icons.Default.Add,
            title = stringResource(R.string.add_search_engine_title),
            summary = stringResource(R.string.add_search_engine_summary),
            onClick = { onEvent(BrowserUiEvent.ShowAddSearchEngineDialog()) },
            isDark = isDark,
        )

        HorizontalDivider(
            color = if (isDark) DarkSurfaceVariant else Color(0xFFF1F5F9),
            modifier = Modifier.padding(start = 56.dp),
        )

        // Search Page Providers Row
        SettingClickableRow(
            icon = Icons.Default.Tune,
            title = stringResource(R.string.search_providers_title),
            summary = stringResource(R.string.search_providers_summary),
            onClick = { showProvidersDialog = true },
            isDark = isDark,
        )
    }

    if (showProvidersDialog) {
        SearchPageProvidersDialog(
            engines = uiState.searchEngines,
            hiddenEngineIds = uiState.settings.hiddenSearchPageEngineIds,
            isDark = isDark,
            onToggleEngine = { engineId, isVisible ->
                onEvent(BrowserUiEvent.ToggleSearchPageEngineVisibility(engineId, isVisible))
            },
            onDismiss = { showProvidersDialog = false },
        )
    }
}

@Composable
fun SearchPageProvidersDialog(
    engines: List<SearchEngine>,
    hiddenEngineIds: Set<String>,
    isDark: Boolean,
    onToggleEngine: (String, Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = ProstoShapes.Card,
            color = if (isDark) DarkSurface else Color.White,
            border = ProstoBorders.cardBorder(isDark),
            shadowElevation = 8.dp,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.search_providers_title),
                        style = LocalProstoTypography.current.headlineSm.copy(fontWeight = FontWeight.Bold),
                        color = if (isDark) DarkOnSurface else ProstoDarkText,
                    )
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(32.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = stringResource(R.string.action_close),
                            tint = ProstoMutedSlate,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }

                Text(
                    text = stringResource(R.string.search_providers_summary),
                    style = LocalProstoTypography.current.bodySm,
                    color = ProstoMutedSlate,
                    modifier = Modifier.padding(top = 4.dp, bottom = 16.dp),
                )

                HorizontalDivider(
                    color = if (isDark) DarkSurfaceVariant else Color(0xFFF1F5F9),
                    thickness = 1.dp,
                )

                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .heightIn(max = 320.dp)
                            .verticalScroll(rememberScrollState()),
                ) {
                    engines.forEachIndexed { index, engine ->
                        val isChecked = engine.id !in hiddenEngineIds
                        Row(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.weight(1f),
                            ) {
                                SearchEngineBadge(
                                    engine = engine,
                                    size = 28.dp,
                                    isDark = isDark,
                                )
                                Column {
                                    Text(
                                        text = engine.displayName,
                                        style = LocalProstoTypography.current.bodyMd.copy(fontWeight = FontWeight.Medium),
                                        color = if (isDark) DarkOnSurface else ProstoDarkText,
                                    )
                                    if (engine.shortcut != null) {
                                        Text(
                                            text = stringResource(R.string.search_engine_shortcut_value, engine.shortcut),
                                            style = LocalProstoTypography.current.labelSm,
                                            color = ProstoMutedSlate,
                                        )
                                    }
                                }
                            }

                            ProstoToggleSwitch(
                                checked = isChecked,
                                onCheckedChange = { onToggleEngine(engine.id, it) },
                            )
                        }

                        if (index < engines.lastIndex) {
                            HorizontalDivider(
                                color = if (isDark) DarkSurfaceVariant else Color(0xFFF1F5F9),
                                thickness = 0.5.dp,
                            )
                        }
                    }
                }
            }
        }
    }
}

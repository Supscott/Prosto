package com.jestk.browser.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.jestk.browser.R
import com.jestk.browser.domain.model.SearchEngine
import com.jestk.browser.ui.components.ProstoSwitchRow
import com.jestk.browser.ui.overlays.SearchEngineBadge
import com.jestk.browser.ui.theme.DarkOnSurface
import com.jestk.browser.ui.theme.DarkSurface
import com.jestk.browser.ui.theme.DarkSurfaceVariant
import com.jestk.browser.ui.theme.LocalProstoTypography
import com.jestk.browser.ui.theme.ProstoBorders
import com.jestk.browser.ui.theme.ProstoDarkText
import com.jestk.browser.ui.theme.ProstoMutedSlate
import com.jestk.browser.ui.theme.ProstoShapes

@Composable
fun SearchPageProvidersDialog(
    engines: List<SearchEngine>,
    hiddenEngineIds: Set<String>,
    isDark: Boolean,
    onToggleEngine: (String, Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        SearchPageProvidersDialogContent(
            engines = engines,
            hiddenEngineIds = hiddenEngineIds,
            isDark = isDark,
            onToggleEngine = onToggleEngine,
            onDismiss = onDismiss,
        )
    }
}

/** The dialog card without its own window, so previews can draw it (a [Dialog] window is not captured). */
@Composable
internal fun SearchPageProvidersDialogContent(
    engines: List<SearchEngine>,
    hiddenEngineIds: Set<String>,
    isDark: Boolean,
    onToggleEngine: (String, Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
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
                    ProstoSwitchRow(
                        checked = isChecked,
                        onCheckedChange = { onToggleEngine(engine.id, it) },
                        contentPadding = PaddingValues(vertical = 12.dp),
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

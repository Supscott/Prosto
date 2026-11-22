package com.jestk.browser.ui.screens

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jestk.browser.R
import com.jestk.browser.ui.theme.DarkOnSurface
import com.jestk.browser.ui.theme.DarkSurfaceVariant
import com.jestk.browser.ui.theme.LocalProstoTypography
import com.jestk.browser.ui.theme.ProstoBrandPrimary
import com.jestk.browser.ui.theme.ProstoDarkText
import com.jestk.browser.ui.theme.ProstoMutedSlate
import com.jestk.browser.ui.theme.ProstoPaleBlue
import com.jestk.browser.ui.viewmodel.BrowserUiEvent
import com.jestk.browser.ui.viewmodel.BrowserUiState

/** Top-level Settings categories; each one opens its own sub-screen. */
enum class SettingsCategory(
    val icon: ImageVector,
    @StringRes val titleRes: Int,
    @StringRes val summaryRes: Int,
) {
    GENERAL(Icons.Default.Tune, R.string.settings_category_general, R.string.settings_category_general_summary),
    SEARCH(Icons.Default.Search, R.string.settings_category_search, R.string.settings_category_search_summary),
    PROTECTION(Icons.Default.Shield, R.string.settings_category_protection, R.string.settings_category_protection_summary),
    ABOUT(Icons.Default.Info, R.string.settings_category_about, R.string.settings_category_about_summary),
}

/** Root Settings body: two airy groups of category rows. */
@Composable
internal fun SettingsCategoryList(
    uiState: BrowserUiState,
    onEvent: (BrowserUiEvent) -> Unit,
    isDark: Boolean,
    onSelect: (SettingsCategory) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(top = 8.dp, bottom = 36.dp),
    ) {
        SettingsQuickTiles(uiState = uiState, onEvent = onEvent, isDark = isDark)
        Spacer(modifier = Modifier.height(24.dp))
        CategoryGroup(
            categories = listOf(SettingsCategory.GENERAL, SettingsCategory.SEARCH),
            isDark = isDark,
            onSelect = onSelect,
        )
        Spacer(modifier = Modifier.height(16.dp))
        CategoryGroup(
            categories = listOf(SettingsCategory.PROTECTION, SettingsCategory.ABOUT),
            isDark = isDark,
            onSelect = onSelect,
        )
        Spacer(modifier = Modifier.height(24.dp))
        SettingsFeedbackButton(
            isDark = isDark,
            onClick = { onEvent(BrowserUiEvent.OpenFeedbackSheet) },
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.settings_version, rememberAppVersion()),
            style = LocalProstoTypography.current.labelSm,
            color = ProstoMutedSlate.copy(alpha = 0.7f),
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun CategoryGroup(
    categories: List<SettingsCategory>,
    isDark: Boolean,
    onSelect: (SettingsCategory) -> Unit,
) {
    InsetGroupedCard(isDark = isDark) {
        categories.forEachIndexed { index, category ->
            if (index > 0) {
                HorizontalDivider(
                    color = if (isDark) DarkSurfaceVariant else Color(0xFFF1F5F9),
                    modifier = Modifier.padding(start = 72.dp),
                )
            }
            CategoryRow(category = category, isDark = isDark, onClick = { onSelect(category) })
        }
    }
}

@Composable
private fun CategoryRow(
    category: SettingsCategory,
    isDark: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 72.dp)
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(
            modifier =
                Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (isDark) ProstoBrandPrimary.copy(alpha = 0.18f) else ProstoPaleBlue),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = category.icon,
                contentDescription = null,
                tint = ProstoBrandPrimary,
                modifier = Modifier.size(22.dp),
            )
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(category.titleRes),
                style = LocalProstoTypography.current.bodyMd.copy(fontWeight = FontWeight.Medium),
                color = if (isDark) DarkOnSurface else ProstoDarkText,
            )
            Text(
                text = stringResource(category.summaryRes),
                style = LocalProstoTypography.current.bodySm,
                color = ProstoMutedSlate,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = null,
            tint = ProstoMutedSlate,
            modifier = Modifier.size(20.dp),
        )
    }
}

package com.jestk.browser.ui.overlays

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.jestk.browser.domain.model.Tab
import com.jestk.browser.domain.model.TabFolder
import com.jestk.browser.domain.model.ThemeMode
import com.jestk.browser.ui.theme.DarkBackground
import com.jestk.browser.ui.theme.JestkBrowserTheme

/**
 * Three overview cards while a tab is dragged: the target tab in the grouping state (scale, outline
 * and scrim as in the grid), an untouched active tab, and a folder card in the joining state.
 */
@Composable
internal fun TabOverviewGroupingHintSample(isDark: Boolean) {
    val tabs =
        listOf(
            Tab(id = "1", url = "https://kotlinlang.org", title = "Kotlin Programming", isActive = true),
            Tab(id = "2", url = "https://developer.android.com", title = "Android Developers"),
            Tab(id = "3", url = "https://github.com", title = "GitHub", folderId = "f1"),
            Tab(id = "4", url = "https://duckduckgo.com", title = "DuckDuckGo", folderId = "f1"),
        )
    val folder = TabFolder(id = "f1", title = "Dev", color = 0xFF8B5CF6.toInt())
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(if (isDark) DarkBackground else Color(0xFFF1F5F4))
                .padding(8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        HintCell(hovered = true, joinsFolder = false, isDark = isDark) {
            GridTabCard(tab = tabs[1], isActive = false, isDark = isDark, isHovered = true, onSelect = {}, onClose = {})
        }
        HintCell(hovered = false, joinsFolder = false, isDark = isDark) {
            GridTabCard(tab = tabs[0], isActive = true, isDark = isDark, onSelect = {}, onClose = {})
        }
        HintCell(hovered = true, joinsFolder = true, isDark = isDark) {
            FolderOverviewGridCard(folder = folder, tabs = tabs.drop(2), isDark = isDark, isHovered = true, onClick = {})
        }
    }
}

@Composable
private fun RowScope.HintCell(
    hovered: Boolean,
    joinsFolder: Boolean,
    isDark: Boolean,
    card: @Composable () -> Unit,
) {
    Box(
        modifier =
            Modifier
                .weight(1f)
                .graphicsLayer {
                    scaleX = if (hovered) 0.96f else 1f
                    scaleY = if (hovered) 0.96f else 1f
                },
    ) {
        card()
        GroupingHintScrim(visible = hovered, joinsFolder = joinsFolder, isDark = isDark)
    }
}

@Preview(name = "Grouping hint Light", showBackground = true, widthDp = 412)
@Composable
private fun TabOverviewGroupingHintLightPreview() {
    JestkBrowserTheme(themeMode = ThemeMode.LIGHT) { TabOverviewGroupingHintSample(isDark = false) }
}

@Preview(name = "Grouping hint Dark", showBackground = true, widthDp = 412)
@Composable
private fun TabOverviewGroupingHintDarkPreview() {
    JestkBrowserTheme(themeMode = ThemeMode.DARK) { TabOverviewGroupingHintSample(isDark = true) }
}

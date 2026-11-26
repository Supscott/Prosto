package com.jestk.browser.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.jestk.browser.R
import com.jestk.browser.ui.theme.DarkOnSurface
import com.jestk.browser.ui.theme.LocalProstoTypography
import com.jestk.browser.ui.theme.ProstoDarkText
import com.jestk.browser.ui.theme.ProstoMutedSlate

/** "Sites not offered in apps" row (summary = count); a tap opens the list of excluded sites. */
@Composable
internal fun OpenInAppExcludedSitesRow(
    sites: Set<String>,
    isDark: Boolean,
    onRemove: (String) -> Unit,
) {
    var showList by rememberSaveable { mutableStateOf(false) }
    SettingClickableRow(
        icon = Icons.Default.Block,
        title = stringResource(R.string.open_in_app_excluded_sites_title),
        summary = if (sites.isEmpty()) stringResource(R.string.open_in_app_excluded_sites_none) else sites.size.toString(),
        onClick = { showList = true },
        isDark = isDark,
    )
    if (showList) {
        AlertDialog(
            onDismissRequest = { showList = false },
            title = { Text(stringResource(R.string.open_in_app_excluded_sites_title)) },
            text = { OpenInAppExcludedSitesList(sites = sites, isDark = isDark, onRemove = onRemove) },
            confirmButton = {
                TextButton(onClick = { showList = false }) {
                    Text(stringResource(R.string.open_in_app_excluded_sites_close))
                }
            },
        )
    }
}

/** The excluded sites, one row each with a remove button. */
@Composable
internal fun OpenInAppExcludedSitesList(
    sites: Set<String>,
    isDark: Boolean,
    onRemove: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
        if (sites.isEmpty()) {
            Text(
                text = stringResource(R.string.open_in_app_excluded_sites_empty),
                style = LocalProstoTypography.current.bodySm,
                color = if (isDark) DarkOnSurface.copy(alpha = 0.72f) else ProstoMutedSlate,
                modifier = Modifier.padding(vertical = 4.dp),
            )
        }
        sites.sorted().forEach { site ->
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = site,
                    style = LocalProstoTypography.current.bodyMd,
                    color = if (isDark) DarkOnSurface else ProstoDarkText,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { onRemove(site) }, modifier = Modifier.size(44.dp)) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = stringResource(R.string.open_in_app_excluded_sites_remove, site),
                        tint = if (isDark) DarkOnSurface.copy(alpha = 0.72f) else ProstoMutedSlate,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
    }
}

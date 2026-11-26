package com.jestk.browser.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.jestk.browser.R
import com.jestk.browser.domain.model.SiteResource
import com.jestk.browser.ui.theme.DarkOnSurface
import com.jestk.browser.ui.theme.DarkSurfaceVariant
import com.jestk.browser.ui.theme.LocalProstoTypography
import com.jestk.browser.ui.theme.ProstoBrandPrimary
import com.jestk.browser.ui.theme.ProstoDarkText
import com.jestk.browser.ui.theme.ProstoMutedSlate
import com.jestk.browser.ui.theme.ProstoPaleBlue

/** The host of a grant key (`scheme://host[:port]`), with the port only when it is not the default. */
internal fun siteOriginLabel(origin: String): String = origin.substringAfter("://")

/** "Site permissions" row (summary = count); a tap opens the list of sites with a remembered grant. */
@Composable
internal fun SitePermissionsRow(
    grants: Map<String, Set<SiteResource>>,
    isDark: Boolean,
    onRemove: (String) -> Unit,
    onRemoveAll: () -> Unit,
) {
    var showList by rememberSaveable { mutableStateOf(false) }
    SettingClickableRow(
        icon = Icons.Default.Lock,
        title = stringResource(R.string.site_permissions_settings_title),
        summary = if (grants.isEmpty()) stringResource(R.string.site_permissions_settings_none) else grants.size.toString(),
        onClick = { showList = true },
        isDark = isDark,
    )
    if (showList) {
        AlertDialog(
            onDismissRequest = { showList = false },
            title = { Text(stringResource(R.string.site_permissions_settings_title)) },
            text = {
                SitePermissionsList(grants = grants, isDark = isDark, onRemove = onRemove, onRemoveAll = onRemoveAll)
            },
            confirmButton = {
                TextButton(onClick = { showList = false }) {
                    Text(stringResource(R.string.site_permissions_settings_close))
                }
            },
        )
    }
}

/** The sites, one row each with chips for the allowed resources and a remove button; "Remove all" below. */
@Composable
internal fun SitePermissionsList(
    grants: Map<String, Set<SiteResource>>,
    isDark: Boolean,
    onRemove: (String) -> Unit,
    onRemoveAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
        if (grants.isEmpty()) {
            Text(
                text = stringResource(R.string.site_permissions_settings_empty),
                style = LocalProstoTypography.current.bodySm,
                color = if (isDark) DarkOnSurface.copy(alpha = 0.72f) else ProstoMutedSlate,
                modifier = Modifier.padding(vertical = 4.dp),
            )
            return@Column
        }
        grants.entries.sortedBy { siteOriginLabel(it.key) }.forEach { (origin, resources) ->
            SitePermissionSiteRow(origin = origin, resources = resources, isDark = isDark, onRemove = { onRemove(origin) })
        }
        TextButton(onClick = onRemoveAll, modifier = Modifier.align(Alignment.End)) {
            Text(stringResource(R.string.site_permissions_settings_remove_all), color = MaterialTheme.colorScheme.error)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SitePermissionSiteRow(
    origin: String,
    resources: Set<SiteResource>,
    isDark: Boolean,
    onRemove: () -> Unit,
) {
    val label = siteOriginLabel(origin)
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = label,
                style = LocalProstoTypography.current.bodyMd,
                color = if (isDark) DarkOnSurface else ProstoDarkText,
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                SiteResource.entries.filter { it in resources }.forEach { resource ->
                    ResourceChip(resource = resource, isDark = isDark)
                }
            }
        }
        IconButton(onClick = onRemove, modifier = Modifier.size(44.dp)) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = stringResource(R.string.site_permissions_settings_remove, label),
                tint = if (isDark) DarkOnSurface.copy(alpha = 0.72f) else ProstoMutedSlate,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun ResourceChip(resource: SiteResource, isDark: Boolean) {
    val icon: ImageVector
    val text: String
    when (resource) {
        SiteResource.CAMERA -> {
            icon = Icons.Default.Videocam
            text = stringResource(R.string.site_permissions_resource_camera)
        }
        SiteResource.MICROPHONE -> {
            icon = Icons.Default.Mic
            text = stringResource(R.string.site_permissions_resource_microphone)
        }
    }
    Surface(
        shape = MaterialTheme.shapes.small,
        color = if (isDark) DarkSurfaceVariant else ProstoPaleBlue,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = ProstoBrandPrimary, modifier = Modifier.size(14.dp))
            Text(
                text = text,
                style = LocalProstoTypography.current.labelSm,
                color = if (isDark) DarkOnSurface else ProstoBrandPrimary,
            )
        }
    }
}

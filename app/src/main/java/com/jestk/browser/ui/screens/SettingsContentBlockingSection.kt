package com.jestk.browser.ui.screens

import android.text.format.DateUtils
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jestk.browser.R
import com.jestk.browser.domain.model.ProtectionLevel
import com.jestk.browser.domain.model.SiteAllowlistEntry
import com.jestk.browser.domain.model.Workspace
import com.jestk.browser.engine.adblock.ContentBlockingStatus
import com.jestk.browser.ui.theme.DarkError
import com.jestk.browser.ui.theme.DarkOnSurface
import com.jestk.browser.ui.theme.DarkSurface
import com.jestk.browser.ui.theme.DarkSurfaceVariant
import com.jestk.browser.ui.theme.LocalProstoTypography
import com.jestk.browser.ui.theme.ProstoBorders
import com.jestk.browser.ui.theme.ProstoBrandPrimary
import com.jestk.browser.ui.theme.ProstoDarkText
import com.jestk.browser.ui.theme.ProstoError
import com.jestk.browser.ui.theme.ProstoMutedSlate
import com.jestk.browser.ui.theme.ProstoPaleBlue
import com.jestk.browser.ui.theme.ProstoShapes
import com.jestk.browser.ui.viewmodel.BrowserUiEvent
import com.jestk.browser.ui.viewmodel.BrowserUiState
import com.jestk.browser.ui.viewmodel.RemoveSiteAllowlistEntry
import com.jestk.browser.ui.viewmodel.SetProtectionLevel
import com.jestk.browser.ui.viewmodel.UpdateFilterListsNow
import java.text.NumberFormat

/** Protection level, filter-list updates, the per-site allowlist and the network-only notice (CB-15). */
@Composable
internal fun SettingsContentBlockingSection(
    uiState: BrowserUiState,
    onEvent: (BrowserUiEvent) -> Unit,
    isDark: Boolean,
) {
    val status = uiState.contentBlocking
    SettingsSectionHeader(title = stringResource(R.string.settings_section_content_blocking))

    ProtectionLevelControl(
        selected = status.level,
        isDark = isDark,
        onSelect = { onEvent(SetProtectionLevel(it)) },
    )
    Text(
        text = stringResource(
            if (status.level == ProtectionLevel.STRICT) R.string.cb_level_strict_summary else R.string.cb_level_standard_summary,
        ),
        style = LocalProstoTypography.current.bodySm,
        color = ProstoMutedSlate,
        modifier = Modifier.padding(start = 4.dp, end = 4.dp, top = 8.dp),
    )

    if (status.isStrictPending) {
        Spacer(Modifier.height(12.dp))
        StrictDownloadNotice(status = status, isDark = isDark, onRetry = { onEvent(UpdateFilterListsNow) })
    }
    if (!status.cosmeticSupported) {
        Spacer(Modifier.height(12.dp))
        NoticeBox(text = stringResource(R.string.cb_network_only_notice), isDark = isDark)
    }

    Spacer(Modifier.height(12.dp))

    InsetGroupedCard(isDark = isDark) {
        UpdateListsRow(status = status, isDark = isDark, onClick = { onEvent(UpdateFilterListsNow) })
        HorizontalDivider(
            color = if (isDark) DarkSurfaceVariant else Color(0xFFF1F5F9),
            modifier = Modifier.padding(start = 16.dp),
        )
        AllowlistBlock(
            entries = uiState.siteAllowlist,
            workspaces = uiState.workspaces,
            isDark = isDark,
            onRemove = { onEvent(RemoveSiteAllowlistEntry(it.host, it.workspaceId)) },
        )
    }
}

@Composable
private fun ProtectionLevelControl(
    selected: ProtectionLevel,
    isDark: Boolean,
    onSelect: (ProtectionLevel) -> Unit,
) {
    Surface(
        shape = ProstoShapes.Card,
        color = if (isDark) DarkSurface else Color.White,
        border = ProstoBorders.cardBorder(isDark),
        shadowElevation = 2.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            ProtectionLevel.entries.forEach { level ->
                val isSelected = level == selected
                val shape = RoundedCornerShape(14.dp)
                Surface(
                    shape = shape,
                    color = if (isSelected) (if (isDark) DarkSurfaceVariant else ProstoPaleBlue) else Color.Transparent,
                    border = if (isSelected) BorderStroke(1.5.dp, ProstoBrandPrimary) else null,
                    modifier = Modifier.weight(1f).height(44.dp).clip(shape).clickable { onSelect(level) },
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = stringResource(
                                if (level == ProtectionLevel.STRICT) R.string.cb_level_strict else R.string.cb_level_standard,
                            ),
                            style = LocalProstoTypography.current.labelMd.copy(
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                            ),
                            color = if (isSelected) ProstoBrandPrimary else ProstoMutedSlate,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StrictDownloadNotice(status: ContentBlockingStatus, isDark: Boolean, onRetry: () -> Unit) {
    val failed = status.hasError && !status.isUpdating
    NoticeBox(
        text = stringResource(if (failed) R.string.cb_strict_failed else R.string.cb_strict_downloading),
        isDark = isDark,
        isError = failed,
        showProgress = status.isUpdating,
        action = if (failed) stringResource(R.string.cb_retry) else null,
        onAction = onRetry,
    )
}

@Composable
private fun NoticeBox(
    text: String,
    isDark: Boolean,
    isError: Boolean = false,
    showProgress: Boolean = false,
    action: String? = null,
    onAction: () -> Unit = {},
) {
    val accent = if (isError) errorColor(isDark) else ProstoBrandPrimary
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = if (isDark) DarkSurfaceVariant else ProstoPaleBlue,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (showProgress) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = accent)
            } else {
                Icon(Icons.Default.Info, contentDescription = null, tint = accent, modifier = Modifier.size(18.dp))
            }
            Text(
                text = text,
                style = LocalProstoTypography.current.bodySm,
                color = if (isDark) DarkOnSurface else ProstoDarkText,
                modifier = Modifier.weight(1f),
            )
            if (action != null) {
                TextButton(onClick = onAction) { Text(action, color = ProstoBrandPrimary) }
            }
        }
    }
}

private fun errorColor(isDark: Boolean): Color = if (isDark) DarkError else ProstoError

@Composable
private fun UpdateListsRow(status: ContentBlockingStatus, isDark: Boolean, onClick: () -> Unit) {
    val context = LocalContext.current
    val updated = status.lastUpdatedAt?.let {
        DateUtils.formatDateTime(
            context,
            it,
            DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_TIME or DateUtils.FORMAT_ABBREV_MONTH,
        )
    } ?: stringResource(R.string.cb_never_updated)
    val rules = remember(status.ruleCount) { NumberFormat.getIntegerInstance().format(status.ruleCount) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !status.isUpdating, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(modifier = Modifier.width(24.dp), contentAlignment = Alignment.CenterStart) {
            if (status.isUpdating) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = ProstoBrandPrimary)
            } else {
                Icon(Icons.Default.Refresh, contentDescription = null, tint = ProstoBrandPrimary, modifier = Modifier.size(22.dp))
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(if (status.isUpdating) R.string.cb_updating else R.string.cb_update_now_title),
                style = LocalProstoTypography.current.bodyMd.copy(fontWeight = FontWeight.Medium),
                color = if (isDark) DarkOnSurface else ProstoDarkText,
            )
            Text(
                text = stringResource(R.string.cb_last_updated, updated),
                style = LocalProstoTypography.current.bodySm,
                color = ProstoMutedSlate,
            )
            Text(
                text = stringResource(R.string.cb_rules_count, rules),
                style = LocalProstoTypography.current.bodySm,
                color = ProstoMutedSlate,
            )
            if (status.hasError && !status.isUpdating && !status.isStrictPending) {
                Text(
                    text = stringResource(R.string.cb_update_failed),
                    style = LocalProstoTypography.current.bodySm,
                    color = errorColor(isDark),
                )
            }
        }
    }
}

@Composable
private fun AllowlistBlock(
    entries: List<SiteAllowlistEntry>,
    workspaces: List<Workspace>,
    isDark: Boolean,
    onRemove: (SiteAllowlistEntry) -> Unit,
) {
    val allWorkspaces = stringResource(R.string.cb_scope_all_workspaces)
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text(
            text = stringResource(R.string.cb_allowlist_title),
            style = LocalProstoTypography.current.bodyMd.copy(fontWeight = FontWeight.Medium),
            color = if (isDark) DarkOnSurface else ProstoDarkText,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
        )
        if (entries.isEmpty()) {
            Text(
                text = stringResource(R.string.cb_allowlist_empty),
                style = LocalProstoTypography.current.bodySm,
                color = ProstoMutedSlate,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }
        entries.forEach { entry ->
            val workspaceId = entry.workspaceId
            val scope = if (workspaceId == null) {
                allWorkspaces
            } else {
                val index = workspaces.indexOfFirst { it.id == workspaceId }
                workspaces.getOrNull(index)?.name?.takeIf { it.isNotBlank() }
                    ?: stringResource(R.string.cb_scope_workspace_number, index + 1)
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f).padding(vertical = 6.dp)) {
                    Text(
                        text = entry.host,
                        style = LocalProstoTypography.current.bodyMd,
                        color = if (isDark) DarkOnSurface else ProstoDarkText,
                    )
                    Text(text = scope, style = LocalProstoTypography.current.bodySm, color = ProstoMutedSlate)
                }
                IconButton(onClick = { onRemove(entry) }, modifier = Modifier.size(44.dp)) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = stringResource(R.string.cb_allowlist_remove, entry.host),
                        tint = ProstoMutedSlate,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
    }
}

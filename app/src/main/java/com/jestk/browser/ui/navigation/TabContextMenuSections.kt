package com.jestk.browser.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jestk.browser.R
import com.jestk.browser.ui.theme.DarkOnSurfaceVariant
import com.jestk.browser.ui.theme.LocalProstoTypography
import com.jestk.browser.ui.theme.ProstoAccent
import com.jestk.browser.ui.theme.ProstoBrandPrimary
import com.jestk.browser.ui.theme.ProstoDarkText
import com.jestk.browser.ui.theme.ProstoMutedSlate

/** Hairline between the icon row and the first group, and between rows of a picker. */
@Composable
internal fun TabMenuHairline(isDark: Boolean) {
    HorizontalDivider(
        modifier = Modifier.padding(vertical = 2.dp),
        color = if (isDark) Color(0xFF334155) else Color(0xFFF1F5F9),
        thickness = 1.dp,
    )
}

/** Wider separation between the Site, Organize and Close groups. */
@Composable
internal fun TabMenuGroupGap(isDark: Boolean) {
    HorizontalDivider(
        modifier = Modifier.padding(vertical = 6.dp),
        color = if (isDark) Color(0xFF334155) else Color(0xFFE2E8F0),
        thickness = 1.dp,
    )
}

/**
 * Icon-only quick actions on top of the tab menu: Copy link, Share, Favorite toggle, Reload.
 * A null callback hides its button; the remaining buttons stay evenly spaced.
 */
@Composable
internal fun TabMenuActionRow(
    isDark: Boolean,
    onCopyUrl: () -> Unit,
    onReload: (() -> Unit)?,
    onShareLink: (() -> Unit)?,
    isFavorite: Boolean,
    onToggleFavorite: (() -> Unit)?,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TabMenuIconButton(Icons.Default.ContentCopy, stringResource(R.string.copy_link_url), false, isDark, onCopyUrl, "tab_context_copy_link")
        if (onShareLink != null) {
            TabMenuIconButton(Icons.Default.Share, stringResource(R.string.share_link), false, isDark, onShareLink, "tab_context_share")
        }
        if (onToggleFavorite != null) {
            TabMenuIconButton(
                icon = if (isFavorite) Icons.Filled.Star else Icons.Outlined.StarBorder,
                description = stringResource(if (isFavorite) R.string.menu_remove_from_favorites else R.string.menu_add_to_favorites),
                isActive = isFavorite,
                isDark = isDark,
                onClick = onToggleFavorite,
                testTag = "tab_context_favorite",
            )
        }
        if (onReload != null) {
            TabMenuIconButton(Icons.Default.Refresh, stringResource(R.string.reload_tab), false, isDark, onReload, "tab_context_reload")
        }
    }
}

/** 48dp touch target with the dock icon look: muted tint, accent tint with no tile behind it when active (a filled star). */
@Composable
private fun TabMenuIconButton(
    icon: ImageVector,
    description: String,
    isActive: Boolean,
    isDark: Boolean,
    onClick: () -> Unit,
    testTag: String,
) {
    val activeTint = if (isDark) ProstoAccent else ProstoBrandPrimary
    val normalTint = if (isDark) DarkOnSurfaceVariant else ProstoMutedSlate
    Box(
        modifier =
            Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(12.dp))
                .clickable(role = Role.Button, onClick = onClick)
                .testTag(testTag),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = if (isActive) activeTint else normalTint,
            modifier = Modifier.size(22.dp),
        )
    }
}

/** One choice of a picker sub-menu (folder or workspace): colour dot plus name. */
@Composable
internal fun TabMenuPickerRow(
    color: Int,
    title: String,
    isDark: Boolean,
    testTag: String,
    onClick: () -> Unit,
) {
    val typography = LocalProstoTypography.current
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = Color.Transparent,
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .clip(RoundedCornerShape(12.dp))
                .clickable(role = Role.Button, onClick = onClick)
                .testTag(testTag),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(Color(color)))
            Text(
                text = title,
                style = typography.bodyMd.copy(fontWeight = FontWeight.Medium),
                color = if (isDark) Color(0xFFF1F5F9) else ProstoDarkText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

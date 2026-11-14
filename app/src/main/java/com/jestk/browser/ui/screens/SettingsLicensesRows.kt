package com.jestk.browser.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jestk.browser.R
import com.jestk.browser.ui.theme.DarkOnSurface
import com.jestk.browser.ui.theme.DarkSurfaceVariant
import com.jestk.browser.ui.theme.LocalProstoTypography
import com.jestk.browser.ui.theme.ProstoDarkText
import com.jestk.browser.ui.theme.ProstoMutedSlate

/** One third-party component. Names and licence identifiers are proper nouns and are not translated. */
internal data class Attribution(val name: String, val licence: String, val url: String)

/**
 * Mirrors `LICENSES/filter-lists.md` and `NOTICE`. The adblock-rust version is the one pinned in
 * `native/adblock-jni/Cargo.toml`.
 */
internal val ATTRIBUTIONS: List<Attribution> = listOf(
    Attribution("adblock-rust 0.13.5", "MPL-2.0", "https://crates.io/crates/adblock/0.13.5"),
    Attribution("uBlock Origin resources", "GPL-3.0", "https://github.com/gorhill/uBlock"),
    Attribution("EasyList, EasyPrivacy, EasyList Cookie", "GPL-3.0-or-later / CC BY-SA 3.0", "https://easylist.to/"),
    Attribution("RU AdList", "CC BY 3.0", "https://forums.lanik.us/viewforum.php?f=102"),
    Attribution("uBlock filters (uAssets)", "GPL-3.0", "https://github.com/uBlockOrigin/uAssets"),
    Attribution("AdGuard Mobile Ads, Tracking Protection", "GPL-3.0", "https://github.com/AdguardTeam/AdguardFilters"),
)

/** A collapsible "Open-source licences" entry for the About card; each line opens the component's page. */
@Composable
internal fun LicensesRows(isDark: Boolean, context: Context) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    HyperlinkRow(
        icon = Icons.Default.Description,
        title = stringResource(R.string.about_licenses_title),
        subtitle = stringResource(R.string.about_licenses_summary),
        onClick = { expanded = !expanded },
        isDark = isDark,
    )
    if (expanded) {
        Column(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
            ATTRIBUTIONS.forEach { item ->
                HorizontalDivider(
                    color = if (isDark) DarkSurfaceVariant else Color(0xFFF1F5F9),
                    modifier = Modifier.padding(start = 56.dp),
                )
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { openLink(context, item.url) }
                        .padding(start = 56.dp, end = 16.dp, top = 10.dp, bottom = 10.dp),
                ) {
                    Text(
                        text = item.name,
                        style = LocalProstoTypography.current.bodyMd.copy(fontWeight = FontWeight.Medium),
                        color = if (isDark) DarkOnSurface else ProstoDarkText,
                    )
                    Text(text = item.licence, style = LocalProstoTypography.current.bodySm, color = ProstoMutedSlate)
                }
            }
        }
    }
}

private fun openLink(context: Context, url: String) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    } catch (_: Exception) {
        Toast.makeText(context, R.string.error_cannot_open_link, Toast.LENGTH_SHORT).show()
    }
}

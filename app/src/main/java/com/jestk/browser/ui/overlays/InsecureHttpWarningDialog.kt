package com.jestk.browser.ui.overlays

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jestk.browser.R
import com.jestk.browser.ui.components.DockPromptAction
import com.jestk.browser.ui.components.DockPromptCard
import com.jestk.browser.ui.theme.DarkError
import com.jestk.browser.ui.theme.DarkOnSurface
import com.jestk.browser.ui.theme.DarkOnSurfaceVariant
import com.jestk.browser.ui.theme.DarkOutlineVariant
import com.jestk.browser.ui.theme.DarkSurfaceVariant
import com.jestk.browser.ui.theme.LocalProstoTypography
import com.jestk.browser.ui.theme.ProstoCanvasFloor
import com.jestk.browser.ui.theme.ProstoDarkText
import com.jestk.browser.ui.theme.ProstoError
import com.jestk.browser.ui.theme.ProstoMutedSlate
import com.jestk.browser.ui.theme.ProstoOutlineVariant
import com.jestk.browser.util.UrlSecurityUtils

/**
 * Warning shown before navigating to an insecure cleartext HTTP website.
 * Primary safe choice (Go back) vs deliberate secondary choice (Continue anyway).
 */
@Composable
fun InsecureHttpWarningDialog(
    isDark: Boolean,
    url: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    val typography = LocalProstoTypography.current
    val host = UrlSecurityUtils.extractDisplayHost(url).ifEmpty { url }
    val textColor = if (isDark) DarkOnSurface else ProstoDarkText
    val mutedColor = if (isDark) DarkOnSurfaceVariant else ProstoMutedSlate
    val badgeBg = if (isDark) DarkSurfaceVariant else ProstoCanvasFloor
    val errorColor = if (isDark) DarkError else ProstoError
    val fieldBorderColor = if (isDark) DarkOutlineVariant else ProstoOutlineVariant

    DockPromptCard(
        title = stringResource(R.string.insecure_site_warning_title),
        isDark = isDark,
        onDismiss = onDismiss,
        actions = listOf(
            DockPromptAction(stringResource(R.string.insecure_site_go_back_button), onDismiss, primary = true),
            DockPromptAction(stringResource(R.string.insecure_site_continue_button), onConfirm, color = errorColor),
        ),
        content = {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = badgeBg,
                border = BorderStroke(1.dp, fieldBorderColor),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = errorColor,
                        modifier = Modifier.size(18.dp),
                    )
                    val urlText = buildAnnotatedString {
                        withStyle(SpanStyle(color = errorColor, fontWeight = FontWeight.Bold)) { append("http://") }
                        withStyle(SpanStyle(color = textColor, fontWeight = FontWeight.Medium)) { append(host) }
                    }
                    Text(
                        text = urlText,
                        style = typography.bodySm,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Text(
                text = stringResource(R.string.insecure_site_warning_message, host),
                style = typography.bodyMd,
                color = mutedColor,
                lineHeight = 20.sp,
                modifier = Modifier.padding(top = 12.dp),
            )
        },
    )
}

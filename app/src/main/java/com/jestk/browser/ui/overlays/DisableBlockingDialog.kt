package com.jestk.browser.ui.overlays

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.jestk.browser.R
import com.jestk.browser.ui.components.ProstoToggleSwitch
import com.jestk.browser.ui.theme.DarkOnSurface
import com.jestk.browser.ui.theme.DarkSurface
import com.jestk.browser.ui.theme.LocalProstoTypography
import com.jestk.browser.ui.theme.ProstoBorders
import com.jestk.browser.ui.theme.ProstoBrandPrimary
import com.jestk.browser.ui.theme.ProstoDarkText
import com.jestk.browser.ui.theme.ProstoMutedSlate
import com.jestk.browser.ui.theme.ProstoShapes

/**
 * Asks before content blocking is switched off for [host]. The entry covers the active
 * workspace unless "Apply to all workspaces" is on. Solid surface, no blur.
 */
@Composable
fun DisableBlockingDialog(
    isDark: Boolean,
    host: String,
    onDismiss: () -> Unit,
    onConfirm: (allWorkspaces: Boolean) -> Unit,
) {
    var allWorkspaces by remember { mutableStateOf(false) }
    val textColor = if (isDark) DarkOnSurface else ProstoDarkText
    val typography = LocalProstoTypography.current

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            shape = ProstoShapes.Card,
            color = if (isDark) DarkSurface else Color.White,
            border = ProstoBorders.floatingBorder(isDark),
            shadowElevation = 8.dp,
            modifier = Modifier.padding(horizontal = 24.dp).fillMaxWidth(),
        ) {
            Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(
                    text = stringResource(R.string.disable_blocking_title),
                    style = typography.headlineSm.copy(fontWeight = FontWeight.Bold),
                    color = textColor,
                )
                Text(
                    text = stringResource(R.string.disable_blocking_message, host),
                    style = typography.bodyMd,
                    color = ProstoMutedSlate,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        text = stringResource(R.string.disable_blocking_all_workspaces),
                        style = typography.bodyMd,
                        color = textColor,
                        modifier = Modifier.weight(1f),
                    )
                    ProstoToggleSwitch(checked = allWorkspaces, onCheckedChange = { allWorkspaces = it })
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = onDismiss,
                        shape = ProstoShapes.Pill,
                        border = BorderStroke(1.dp, if (isDark) Color(0xFF334155) else Color(0xFFE2E8F0)),
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(
                            text = stringResource(R.string.cancel),
                            style = typography.labelMd.copy(fontWeight = FontWeight.SemiBold),
                            color = ProstoMutedSlate,
                        )
                    }
                    Button(
                        onClick = { onConfirm(allWorkspaces) },
                        shape = ProstoShapes.Pill,
                        colors = ButtonDefaults.buttonColors(containerColor = ProstoBrandPrimary, contentColor = Color.White),
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(
                            text = stringResource(R.string.disable_blocking_confirm),
                            style = typography.labelMd.copy(fontWeight = FontWeight.Bold),
                            color = Color.White,
                        )
                    }
                }
            }
        }
    }
}

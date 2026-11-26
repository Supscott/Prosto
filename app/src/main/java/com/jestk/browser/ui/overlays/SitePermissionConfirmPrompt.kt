package com.jestk.browser.ui.overlays

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.jestk.browser.R
import com.jestk.browser.domain.model.SitePermissionChoice
import com.jestk.browser.domain.model.SiteResource
import com.jestk.browser.ui.components.DockPromptCard
import com.jestk.browser.ui.theme.DarkOnSurface
import com.jestk.browser.ui.theme.DarkOnSurfaceVariant
import com.jestk.browser.ui.theme.DarkSurfaceVariant
import com.jestk.browser.ui.theme.LocalProstoTypography
import com.jestk.browser.ui.theme.ProstoBrandPrimary
import com.jestk.browser.ui.theme.ProstoDarkText
import com.jestk.browser.ui.theme.ProstoMutedSlate
import com.jestk.browser.ui.theme.ProstoShapes

/**
 * Asks whether [host] may use the camera and / or microphone. Three full-width buttons, stacked:
 * "Allow always" (only when [canRemember]), "Allow this time" and "Block". Back and a tap outside
 * the card count as Block.
 */
@Composable
internal fun SitePermissionConfirmPrompt(
    host: String,
    resources: Set<SiteResource>,
    canRemember: Boolean,
    isDark: Boolean,
    onChoice: (SitePermissionChoice) -> Unit,
) {
    val title = stringResource(
        when {
            SiteResource.CAMERA in resources && SiteResource.MICROPHONE in resources ->
                R.string.site_permission_title_camera_microphone
            SiteResource.CAMERA in resources -> R.string.site_permission_title_camera
            else -> R.string.site_permission_title_microphone
        },
        breakableHost(host),
    )
    DockPromptCard(
        title = title,
        isDark = isDark,
        onDismiss = { onChoice(SitePermissionChoice.BLOCK) },
        actions = emptyList(),
        content = {
            Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (canRemember) {
                    PermissionButton(
                        text = stringResource(R.string.site_permission_allow_always),
                        kind = ButtonKind.Primary,
                        isDark = isDark,
                        testTag = "site_permission_allow_always",
                    ) { onChoice(SitePermissionChoice.ALLOW_ALWAYS) }
                }
                PermissionButton(
                    text = stringResource(R.string.site_permission_allow_once),
                    kind = if (canRemember) ButtonKind.Tonal else ButtonKind.Primary,
                    isDark = isDark,
                    testTag = "site_permission_allow_once",
                ) { onChoice(SitePermissionChoice.ALLOW_ONCE) }
                PermissionButton(
                    text = stringResource(R.string.site_permission_block),
                    kind = ButtonKind.Text,
                    isDark = isDark,
                    testTag = "site_permission_block",
                ) { onChoice(SitePermissionChoice.BLOCK) }
            }
        },
    )
}

/** A long host wraps after a dot instead of in the middle of a label (zero-width space). */
internal fun breakableHost(host: String): String = host.replace(".", ".​")

private enum class ButtonKind { Primary, Tonal, Text }

@Composable
private fun PermissionButton(
    text: String,
    kind: ButtonKind,
    isDark: Boolean,
    testTag: String,
    onClick: () -> Unit,
) {
    val typography = LocalProstoTypography.current
    val modifier = Modifier.fillMaxWidth().testTag(testTag)
    val label: @Composable (Color?) -> Unit = { color ->
        Text(
            text = text,
            style = typography.labelMd,
            fontWeight = if (kind == ButtonKind.Text) null else FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            color = color ?: Color.Unspecified,
        )
    }
    when (kind) {
        ButtonKind.Primary -> Button(
            onClick = onClick,
            shape = ProstoShapes.Pill,
            colors = ButtonDefaults.buttonColors(containerColor = ProstoBrandPrimary, contentColor = Color.White),
            modifier = modifier,
        ) { label(null) }

        ButtonKind.Tonal -> FilledTonalButton(
            onClick = onClick,
            shape = ProstoShapes.Pill,
            colors = ButtonDefaults.filledTonalButtonColors(
                containerColor = if (isDark) DarkSurfaceVariant else Color(0xFFE2E8F0),
                contentColor = if (isDark) DarkOnSurface else ProstoDarkText,
            ),
            modifier = modifier,
        ) { label(null) }

        ButtonKind.Text -> TextButton(onClick = onClick, modifier = modifier) {
            label(if (isDark) DarkOnSurfaceVariant else ProstoMutedSlate)
        }
    }
}

package com.jestk.browser.ui.overlays

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.jestk.browser.R
import com.jestk.browser.ui.theme.DarkError
import com.jestk.browser.ui.theme.DarkErrorContainer
import com.jestk.browser.ui.theme.DarkOnErrorContainer
import com.jestk.browser.ui.theme.DarkOnSurface
import com.jestk.browser.ui.theme.DarkOnSurfaceVariant
import com.jestk.browser.ui.theme.DarkOutlineVariant
import com.jestk.browser.ui.theme.DarkSurface
import com.jestk.browser.ui.theme.DarkSurfaceVariant
import com.jestk.browser.ui.theme.JestkBrowserTheme
import com.jestk.browser.ui.theme.LocalProstoTypography
import com.jestk.browser.ui.theme.ProstoBorders
import com.jestk.browser.ui.theme.ProstoBrandPrimary
import com.jestk.browser.ui.theme.ProstoCanvasFloor
import com.jestk.browser.ui.theme.ProstoDarkText
import com.jestk.browser.ui.theme.ProstoError
import com.jestk.browser.ui.theme.ProstoErrorContainer
import com.jestk.browser.ui.theme.ProstoMutedSlate
import com.jestk.browser.ui.theme.ProstoOutlineVariant
import com.jestk.browser.ui.theme.ProstoShapes
import com.jestk.browser.util.UrlSecurityUtils

/**
 * Warning modal displayed before navigating to an insecure cleartext HTTP website.
 * Follows the Prosto Canvas design system:
 * - Solid surface with crisp borders, strictly NO BLUR.
 * - Clear visual hierarchy emphasizing security and user intent.
 * - Primary safe choice (Go back) vs deliberate secondary choice (Continue anyway).
 */
@Composable
fun InsecureHttpWarningDialog(
    isDark: Boolean,
    url: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        InsecureHttpWarningDialogContent(
            isDark = isDark,
            url = url,
            onDismiss = onDismiss,
            onConfirm = onConfirm,
            modifier = modifier,
        )
    }
}

@Composable
fun InsecureHttpWarningDialogContent(
    isDark: Boolean,
    url: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val typography = LocalProstoTypography.current
    val host = UrlSecurityUtils.extractDisplayHost(url).ifEmpty { url }

    val dialogBg = if (isDark) DarkSurface else Color.White
    val textColor = if (isDark) DarkOnSurface else ProstoDarkText
    val mutedColor = if (isDark) DarkOnSurfaceVariant else ProstoMutedSlate
    val badgeBg = if (isDark) DarkSurfaceVariant else ProstoCanvasFloor
    val errorIconBg = if (isDark) DarkErrorContainer else ProstoErrorContainer
    val errorIconColor = if (isDark) DarkOnErrorContainer else ProstoError
    val fieldBorderColor = if (isDark) DarkOutlineVariant else ProstoOutlineVariant

    Surface(
        modifier = modifier
            .fillMaxWidth(0.92f)
            .padding(vertical = 24.dp),
        shape = ProstoShapes.Card,
        color = dialogBg,
        border = ProstoBorders.floatingBorder(isDark),
        shadowElevation = 8.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Warning Icon
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .background(errorIconBg, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = errorIconColor,
                    modifier = Modifier.size(28.dp),
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Title
            Text(
                text = stringResource(R.string.insecure_site_warning_title),
                style = typography.headlineMd,
                fontWeight = FontWeight.Bold,
                color = textColor,
                textAlign = TextAlign.Center,
            )

            Spacer(modifier = Modifier.height(12.dp))

            // URL badge
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = badgeBg,
                border = BorderStroke(1.dp, fieldBorderColor),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val urlAnnotatedString = buildAnnotatedString {
                        withStyle(
                            style = SpanStyle(
                                color = if (isDark) DarkError else ProstoError,
                                fontWeight = FontWeight.Bold,
                            )
                        ) {
                            append("http://")
                        }
                        withStyle(
                            style = SpanStyle(
                                color = textColor,
                                fontWeight = FontWeight.Medium,
                            )
                        ) {
                            append(host)
                        }
                    }
                    Text(
                        text = urlAnnotatedString,
                        style = typography.bodySm,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Detailed warning message
            Text(
                text = stringResource(R.string.insecure_site_warning_message, host),
                style = typography.bodyMd,
                color = mutedColor,
                textAlign = TextAlign.Center,
                lineHeight = 20.sp,
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Actions: Primary (Go Back) and Secondary (Continue)
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                // Safe button (Go back)
                Button(
                    onClick = onDismiss,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = ProstoShapes.ButtonPill,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ProstoBrandPrimary,
                        contentColor = Color.White,
                    ),
                ) {
                    Text(
                        text = stringResource(R.string.insecure_site_go_back_button),
                        style = typography.labelMd,
                        fontWeight = FontWeight.SemiBold,
                    )
                }

                // Unsafe proceed button (Continue anyway)
                OutlinedButton(
                    onClick = onConfirm,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = ProstoShapes.ButtonPill,
                    border = BorderStroke(1.dp, fieldBorderColor),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = if (isDark) DarkError else ProstoError,
                    ),
                ) {
                    Text(
                        text = stringResource(R.string.insecure_site_continue_button),
                        style = typography.labelMd,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
        }
    }
}

@Preview(name = "InsecureHttpWarningDialog Light", showBackground = true)
@Composable
fun InsecureHttpWarningDialogLightPreview() {
    JestkBrowserTheme(themeMode = com.jestk.browser.domain.model.ThemeMode.LIGHT) {
        InsecureHttpWarningDialogContent(
            isDark = false,
            url = "http://example.com/login",
            onDismiss = {},
            onConfirm = {},
        )
    }
}

@Preview(name = "InsecureHttpWarningDialog Dark", showBackground = true)
@Composable
fun InsecureHttpWarningDialogDarkPreview() {
    JestkBrowserTheme(themeMode = com.jestk.browser.domain.model.ThemeMode.DARK) {
        InsecureHttpWarningDialogContent(
            isDark = true,
            url = "http://example.com/login",
            onDismiss = {},
            onConfirm = {},
        )
    }
}

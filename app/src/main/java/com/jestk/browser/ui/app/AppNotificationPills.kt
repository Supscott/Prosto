package com.jestk.browser.ui.app

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jestk.browser.R
import com.jestk.browser.domain.model.isVisible
import com.jestk.browser.ui.components.tabDisplayTitle
import com.jestk.browser.ui.launchExternalApp
import com.jestk.browser.ui.overlays.BlockedAdNavigationBanner
import com.jestk.browser.ui.overlays.ExternalAppConfirmDialog
import com.jestk.browser.ui.overlays.ExternalAppNotificationPill
import com.jestk.browser.ui.overlays.UpdateNotificationCard
import com.jestk.browser.ui.theme.DarkOnSurface
import com.jestk.browser.ui.theme.DarkSurface
import com.jestk.browser.ui.theme.ProstoBorders
import com.jestk.browser.ui.theme.ProstoBrandPrimary
import com.jestk.browser.ui.theme.ProstoDarkText
import com.jestk.browser.ui.theme.ProstoShapes
import com.jestk.browser.ui.viewmodel.BrowserUiEvent
import com.jestk.browser.ui.viewmodel.BrowserUiState
import com.jestk.browser.ui.viewmodel.ExternalAppDismissReason
import com.jestk.browser.util.DownloadFileOpener
import kotlinx.coroutines.delay

@Composable
internal fun BoxScope.AppNotificationPills(
    uiState: BrowserUiState,
    onEvent: (BrowserUiEvent) -> Unit,
    isDark: Boolean,
    activeWorkspaceColor: Color?,
    isBottomBarVisible: Boolean,
) {
    val context = LocalContext.current
    val snackbarBaseBottomOffset by animateDpAsState(
        targetValue = if (isBottomBarVisible) 76.dp else 16.dp,
        animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
        label = "SnackbarBaseBottomOffset",
    )

    // Undo Closed Tab Floating Notification Pill
    AnimatedVisibility(
        visible = uiState.showUndoTabCloseSnackbar && uiState.recentlyClosedTab != null,
        enter =
            fadeIn(tween(220, easing = FastOutSlowInEasing)) + slideInVertically(tween(260, easing = FastOutSlowInEasing)) { it },
        exit =
            fadeOut(tween(180, easing = FastOutSlowInEasing)) + slideOutVertically(tween(220, easing = FastOutSlowInEasing)) { it },
        modifier =
            Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = snackbarBaseBottomOffset, start = 16.dp, end = 16.dp),
    ) {
        val tab = uiState.recentlyClosedTab
        val tabTitle = tab?.let { tabDisplayTitle(it) }.orEmpty()
        val undoText = stringResource(R.string.undo)
        val closedText = stringResource(R.string.tab_closed)

        LaunchedEffect(uiState.showUndoTabCloseSnackbar, tab?.id) {
            if (uiState.showUndoTabCloseSnackbar) {
                kotlinx.coroutines.delay(4500)
                onEvent(BrowserUiEvent.DismissUndoTabCloseSnackbar)
            }
        }

        Surface(
            shape = ProstoShapes.Pill,
            color = if (isDark) DarkSurface else Color.White,
            border = ProstoBorders.floatingBorder(isDark, workspaceColor = activeWorkspaceColor),
            shadowElevation = 8.dp,
            modifier = Modifier.wrapContentSize(),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                val closedMessage = "$closedText: $tabTitle"
                Text(
                    text = closedMessage,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                    color = if (isDark) DarkOnSurface else ProstoDarkText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                val undoAccent = activeWorkspaceColor ?: ProstoBrandPrimary
                Text(
                    text = undoText,
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold, color = undoAccent),
                    modifier =
                        Modifier
                            .clickable {
                                onEvent(BrowserUiEvent.UndoCloseTab)
                            }.padding(horizontal = 4.dp, vertical = 2.dp),
                )
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.outline,
                    modifier =
                        Modifier
                            .size(16.dp)
                            .clickable {
                                onEvent(BrowserUiEvent.DismissUndoTabCloseSnackbar)
                            },
                )
            }
        }
    }

    // Download Floating Notification Pill
    AnimatedVisibility(
        visible = uiState.showDownloadSnackbar && uiState.lastDownloadNotification != null,
        enter =
            fadeIn(tween(220, easing = FastOutSlowInEasing)) + slideInVertically(tween(260, easing = FastOutSlowInEasing)) { it },
        exit =
            fadeOut(tween(180, easing = FastOutSlowInEasing)) + slideOutVertically(tween(220, easing = FastOutSlowInEasing)) { it },
        modifier =
            Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(
                    bottom = if (uiState.showUndoTabCloseSnackbar && uiState.recentlyClosedTab != null) {
                        snackbarBaseBottomOffset + 56.dp
                    } else {
                        snackbarBaseBottomOffset
                    },
                    start = 16.dp,
                    end = 16.dp,
                ),
    ) {
        val download = uiState.lastDownloadNotification
        val defaultFileName = stringResource(R.string.download_default_filename)
        val fileName = download?.fileName?.ifEmpty { defaultFileName } ?: defaultFileName

        LaunchedEffect(uiState.showDownloadSnackbar, download?.id, download?.timestamp) {
            if (uiState.showDownloadSnackbar) {
                kotlinx.coroutines.delay(5000)
                onEvent(BrowserUiEvent.DismissDownloadSnackbar)
            }
        }

        val downloadAccent = activeWorkspaceColor ?: ProstoBrandPrimary
        Surface(
            shape = ProstoShapes.Pill,
            color = if (isDark) DarkSurface else Color.White,
            border = ProstoBorders.floatingBorder(isDark, workspaceColor = activeWorkspaceColor),
            shadowElevation = 8.dp,
            modifier = Modifier.wrapContentSize(),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Download,
                    contentDescription = null,
                    tint = downloadAccent,
                    modifier = Modifier.size(18.dp),
                )
                val isCompleted = download?.status == com.jestk.browser.domain.model.DownloadStatus.COMPLETED
                Text(
                    text = if (isCompleted) {
                        stringResource(R.string.download_completed_notification, fileName)
                    } else {
                        stringResource(R.string.download_in_progress_notification, fileName)
                    },
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                    color = if (isDark) DarkOnSurface else ProstoDarkText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Text(
                    text = if (isCompleted) {
                        stringResource(R.string.download_action_open)
                    } else {
                        stringResource(R.string.download_action_details)
                    },
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold, color = downloadAccent),
                    modifier =
                        Modifier
                            .clickable {
                                if (isCompleted) {
                                    download?.let { DownloadFileOpener.openDownloadedFile(context, it) }
                                } else {
                                    onEvent(BrowserUiEvent.OpenDownloads)
                                }
                                onEvent(BrowserUiEvent.DismissDownloadSnackbar)
                            }.padding(horizontal = 4.dp, vertical = 2.dp),
                )
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.outline,
                    modifier =
                        Modifier
                            .size(16.dp)
                            .clickable {
                                onEvent(BrowserUiEvent.DismissDownloadSnackbar)
                            },
                )
            }
        }
    }

    // External App Floating Notification Pill
    AnimatedVisibility(
        visible = uiState.externalAppPrompt?.requiresConfirmation == false,
        enter = fadeIn(tween(220, easing = FastOutSlowInEasing)) + slideInVertically(tween(260, easing = FastOutSlowInEasing)) { it },
        exit = fadeOut(tween(180, easing = FastOutSlowInEasing)) + slideOutVertically(tween(220, easing = FastOutSlowInEasing)) { it },
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .navigationBarsPadding()
            .padding(
                bottom = snackbarBaseBottomOffset +
                    (if (uiState.showUndoTabCloseSnackbar && uiState.recentlyClosedTab != null) 56.dp else 0.dp) +
                    (if (uiState.showDownloadSnackbar && uiState.lastDownloadNotification != null) 56.dp else 0.dp),
                start = 16.dp,
                end = 16.dp
            ),
    ) {
        uiState.externalAppPrompt?.takeIf { !it.requiresConfirmation }?.let { prompt ->
            ExternalAppNotificationPill(
                prompt = prompt,
                onOpen = {
                    onEvent(BrowserUiEvent.OpenExternalApp(prompt))
                    context.launchExternalApp(prompt)
                },
                onDismiss = {
                    onEvent(BrowserUiEvent.DismissExternalAppPrompt(ExternalAppDismissReason.EXPLICIT))
                },
                onTimeout = {
                    // A hint the user ignored must not block a later deliberate tap on the
                    // site's own "open in app" control.
                    onEvent(
                        BrowserUiEvent.DismissExternalAppPrompt(ExternalAppDismissReason.TIMEOUT)
                    )
                }
            )
        }
    }

    // Confirmation for a deliberate tap on the site's own "Open app" control. Cancel and
    // Close tab dismiss it transiently so a later tap is offered again.
    uiState.externalAppPrompt?.takeIf { it.requiresConfirmation }?.let { prompt ->
        ExternalAppConfirmDialog(
            isDark = isDark,
            onOpen = {
                onEvent(BrowserUiEvent.OpenExternalApp(prompt))
                context.launchExternalApp(prompt)
            },
            onCloseTab = {
                onEvent(BrowserUiEvent.DismissExternalAppPrompt(ExternalAppDismissReason.TRANSIENT))
                onEvent(BrowserUiEvent.CloseTab(prompt.tabId))
            },
            onCancel = {
                onEvent(BrowserUiEvent.DismissExternalAppPrompt(ExternalAppDismissReason.TRANSIENT))
            },
        )
    }

    // Update Floating Notification Banner
    AnimatedVisibility(
        visible = uiState.updateState.isVisible,
        enter = fadeIn(tween(220, easing = FastOutSlowInEasing)) + slideInVertically(tween(260, easing = FastOutSlowInEasing)) { it },
        exit = fadeOut(tween(180, easing = FastOutSlowInEasing)) + slideOutVertically(tween(220, easing = FastOutSlowInEasing)) { it },
        modifier =
            Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(
                    bottom = snackbarBaseBottomOffset +
                        (if (uiState.showUndoTabCloseSnackbar && uiState.recentlyClosedTab != null) 56.dp else 0.dp) +
                        (if (uiState.showDownloadSnackbar && uiState.lastDownloadNotification != null) 56.dp else 0.dp),
                    start = 16.dp,
                    end = 16.dp,
                ),
    ) {
        UpdateNotificationCard(
            state = uiState.updateState,
            onRedirect = { onEvent(BrowserUiEvent.RedirectToUpdate) },
            onDismiss = { onEvent(BrowserUiEvent.DismissUpdate) },
            onSkip = { onEvent(BrowserUiEvent.SkipUpdate) },
            isDark = isDark,
        )
    }

    // Blocked Ad Navigation Floating Banner
    val blockedNav = uiState.blockedAdNavigation
    AnimatedVisibility(
        visible = blockedNav != null,
        enter = slideInVertically(
            initialOffsetY = { it + 200 },
            animationSpec = spring(
                dampingRatio = 0.58f,
                stiffness = Spring.StiffnessMediumLow,
            ),
        ) + fadeIn(animationSpec = tween(120)),
        exit = slideOutVertically(
            targetOffsetY = { it + 200 },
            animationSpec = tween(180, easing = FastOutSlowInEasing),
        ) + fadeOut(animationSpec = tween(150)),
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .navigationBarsPadding()
            .padding(
                bottom = snackbarBaseBottomOffset +
                    (if (uiState.showUndoTabCloseSnackbar && uiState.recentlyClosedTab != null) 56.dp else 0.dp) +
                    (if (uiState.showDownloadSnackbar && uiState.lastDownloadNotification != null) 56.dp else 0.dp) +
                    8.dp,
                start = 16.dp,
                end = 16.dp,
            ),
    ) {
        if (blockedNav != null) {
            BlockedAdNavigationBanner(
                isDark = isDark,
                url = blockedNav.url,
                onDismiss = { onEvent(BrowserUiEvent.DismissBlockedAdNavigation) },
                onConfirm = { onEvent(BrowserUiEvent.AllowBlockedAdNavigation) },
            )
        }
    }
}

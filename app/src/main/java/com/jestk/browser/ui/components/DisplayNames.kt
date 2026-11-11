package com.jestk.browser.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.jestk.browser.R
import com.jestk.browser.domain.model.Tab
import com.jestk.browser.domain.model.Workspace
import com.jestk.browser.util.UrlSecurityUtils

/**
 * The title shown for [tab]. Home and not-yet-loaded tabs store an empty title; their labels come
 * from resources here so that stored data never carries English placeholder text.
 */
@Composable
fun tabDisplayTitle(tab: Tab): String =
    when {
        tab.isHomeTab -> stringResource(R.string.tab_home_title)
        tab.title.isNotBlank() -> tab.title
        else -> UrlSecurityUtils.extractHost(tab.url).removePrefix("www.").ifBlank { stringResource(R.string.action_new_tab) }
    }

/** The name shown for [workspace]. A blank stored name means "use the localized default". */
@Composable
fun workspaceDisplayName(workspace: Workspace): String =
    workspace.name.ifBlank {
        stringResource(
            when (workspace.id) {
                Workspace.DEFAULT_PERSONAL_ID -> R.string.workspace_personal
                Workspace.DEFAULT_WORK_ID -> R.string.workspace_work
                else -> R.string.workspace_default_title
            },
        )
    }

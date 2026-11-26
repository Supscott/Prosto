package com.jestk.browser.ui.viewmodel.events

import com.jestk.browser.domain.model.SiteAllowlistEntry
import com.jestk.browser.domain.model.covers
import com.jestk.browser.ui.viewmodel.BrowserUiState
import com.jestk.browser.ui.viewmodel.allowlistSiteOf
import com.jestk.browser.ui.viewmodel.webPageUrl

/**
 * The ids of the open web tabs, in any workspace and active or not, whose blocking state differs
 * between the [before] and [after] allowlists: those are the pages that must reload to pick up
 * the change. Tabs of other sites, and tabs the change does not reach (another workspace), stay out.
 */
internal fun tabsAffectedByAllowlistChange(
    state: BrowserUiState,
    before: List<SiteAllowlistEntry>,
    after: List<SiteAllowlistEntry>,
): List<String> =
    state.allTabs.ifEmpty { state.tabs }.mapNotNull { tab ->
        val site = state.webPageUrl(tab)?.let(::allowlistSiteOf) ?: return@mapNotNull null
        tab.id.takeIf { before.covers(site, tab.workspaceId) != after.covers(site, tab.workspaceId) }
    }

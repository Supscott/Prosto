package com.jestk.browser.engine.adblock

import com.jestk.browser.domain.repository.SiteAllowlistRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** Keeps a [SiteAllowlist] snapshot equal to the persisted entries. */
object SiteAllowlistSync {
    fun start(
        scope: CoroutineScope,
        repository: SiteAllowlistRepository,
        target: SiteAllowlist = SiteAllowlist.shared,
    ): Job = scope.launch {
        repository.observe().collect { target.update(it) }
    }
}

package com.jestk.browser.ui.viewmodel.tabs

import com.jestk.browser.domain.model.ExternalAppPrompt
import com.jestk.browser.ui.viewmodel.ExternalAppDismissReason
import java.util.concurrent.ConcurrentHashMap

internal class ExternalAppPrompts(
    ctx: TabCoordinatorContext,
) {
    private val getCurrentState = ctx.getCurrentState
    private val updateUiState = ctx.updateUiState
    private val dismissedExternalAppDomains = ctx.dismissedExternalAppDomains
    private val pendingExternalAppPrompts = ctx.pendingExternalAppPrompts
    private val timedOutExternalAppDomains = ctx.timedOutExternalAppDomains

    fun handleExternalAppOffered(
        tabId: String,
        prompt: ExternalAppPrompt,
        userInitiated: Boolean = false
    ) {
        val current = getCurrentState()
        if (current.tabs.none { it.id == tabId }) return

        if (isExternalAppDomainSuppressed(tabId, prompt.domain, userInitiated = userInitiated)) {
            return
        }

        if (current.activeTabId != tabId) {
            // Keep the offer for a background tab instead of dropping it: the user may switch
            // to that tab right after the link was opened in the background.
            // A confirmation dialog must not pop up later over a different screen.
            pendingExternalAppPrompts.putIfAbsent(tabId, prompt.copy(requiresConfirmation = false))
            return
        }

        val existing = current.externalAppPrompt
        if (existing != null) {
            // A passive hint never replaces a pending confirmation; a confirmation upgrades a hint.
            if (existing.requiresConfirmation && !prompt.requiresConfirmation) return
            if (existing.requiresConfirmation == prompt.requiresConfirmation &&
                areExternalAppDomainsEqual(existing.domain, prompt.domain)
            ) {
                return
            }
        }
        updateUiState { it.copy(externalAppPrompt = prompt) }
    }

    private fun areExternalAppDomainsEqual(domain1: String, domain2: String): Boolean =
        domain1 == domain2 ||
            com.jestk.browser.engine.ExternalAppResolver.areDomainAliases(domain1, domain2)

    private fun isExternalAppDomainSuppressed(
        tabId: String,
        domain: String,
        userInitiated: Boolean
    ): Boolean {
        // Explicit dismissal suppresses regardless of whether the navigation was user-initiated.
        if (isDomainInSuppressionMap(tabId, domain, dismissedExternalAppDomains)) {
            return true
        }
        // Timeout suppression suppresses passive offers, but deliberate user gestures bypass it.
        if (!userInitiated && isDomainInSuppressionMap(tabId, domain, timedOutExternalAppDomains)) {
            return true
        }
        return false
    }

    private fun isDomainInSuppressionMap(
        tabId: String,
        domain: String,
        suppression: MutableMap<String, MutableSet<String>>
    ): Boolean {
        val suppressed = suppression[tabId] ?: return false
        return suppressed.contains(domain) ||
            suppressed.any {
                com.jestk.browser.engine.ExternalAppResolver.areDomainAliases(it, domain)
            }
    }

    fun publishPendingExternalAppPrompt(tabId: String) {
        val prompt = pendingExternalAppPrompts.remove(tabId) ?: return
        if (isExternalAppDomainSuppressed(tabId, prompt.domain, userInitiated = false)) return
        updateUiState { it.copy(externalAppPrompt = prompt) }
    }

    fun dismissExternalAppPrompt(reason: ExternalAppDismissReason = ExternalAppDismissReason.EXPLICIT) {
        val current = getCurrentState()
        val prompt = current.externalAppPrompt
        if (prompt != null) {
            when (reason) {
                ExternalAppDismissReason.TIMEOUT -> timedOutExternalAppDomains
                    .getOrPut(prompt.tabId) { ConcurrentHashMap.newKeySet() }
                    .add(prompt.domain)
                ExternalAppDismissReason.EXPLICIT -> dismissedExternalAppDomains
                    .getOrPut(prompt.tabId) { ConcurrentHashMap.newKeySet() }
                    .add(prompt.domain)
                ExternalAppDismissReason.TRANSIENT -> Unit
            }
            pendingExternalAppPrompts.remove(prompt.tabId)
        }
        updateUiState { it.copy(externalAppPrompt = null) }
    }
}

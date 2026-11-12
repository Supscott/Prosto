package com.jestk.browser.domain.model

import java.util.UUID

/**
 * Domain representation of an external application offered by a website or intent.
 */
data class ExternalAppPrompt(
    val id: String = UUID.randomUUID().toString(),
    val tabId: String,
    val appName: String,
    val appPackage: String? = null,
    val appIcon: AppIcon? = null,
    val intentUri: String,
    val isInstalled: Boolean = true,
    val domain: String,
    /**
     * True when the user deliberately tapped a site's own "Open app" control: the handoff is shown
     * as a confirmation dialog instead of the passive, auto-dismissing hint.
     */
    val requiresConfirmation: Boolean = false,
    /**
     * The web page the tap was going to, when the handoff was a web link held back for the
     * confirmation: Cancel loads it in the browser.
     */
    val webFallbackUrl: String? = null,
)

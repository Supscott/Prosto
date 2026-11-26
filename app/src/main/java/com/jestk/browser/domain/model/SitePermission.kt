package com.jestk.browser.domain.model

/** A device capability a page can ask for with `getUserMedia`. */
enum class SiteResource { CAMERA, MICROPHONE }

/** The answer to a site permission prompt. */
enum class SitePermissionChoice { ALLOW_ALWAYS, ALLOW_ONCE, BLOCK }

/**
 * What the camera / microphone prompt shows. [canRemember] is false in an incognito tab, which
 * hides "Allow always" because nothing is stored there.
 */
data class SitePermissionPrompt(
    val tabId: String,
    val host: String,
    val resources: Set<SiteResource>,
    val canRemember: Boolean,
)

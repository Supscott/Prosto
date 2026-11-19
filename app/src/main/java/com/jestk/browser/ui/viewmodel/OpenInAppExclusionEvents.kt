package com.jestk.browser.ui.viewmodel

/** The "Don't offer to open <domain> in the app?" question, shown in [tabId] after repeated dismissals. */
data class OpenInAppExclusionPrompt(val tabId: String, val domain: String)

/** The user swiped the open-in-app pill of [domain] away; counts toward the "don't offer" question. */
data class RecordOpenInAppDismissal(val domain: String) : SettingsUiEvent

/** "Don't offer" on the question pill: [domain] is never offered in an app again (passive pill only). */
data class ExcludeOpenInAppSite(val domain: String) : SettingsUiEvent

/** Removes [site] from the excluded list in Settings. */
data class IncludeOpenInAppSite(val site: String) : SettingsUiEvent

/** The question pill was swiped away or timed out; the dismissal counter starts over. */
data class DismissOpenInAppExclusionPrompt(val domain: String) : SettingsUiEvent

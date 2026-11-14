package com.jestk.browser.domain.model

/** The site whose popup was just blocked; drives the "Popup blocked" pill. */
data class PopupBlockedPrompt(val openerUrl: String)

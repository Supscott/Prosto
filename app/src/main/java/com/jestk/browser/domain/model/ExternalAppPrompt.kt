package com.jestk.browser.domain.model

import android.graphics.Bitmap
import java.util.UUID

/**
 * Domain representation of an external application offered by a website or intent.
 */
data class ExternalAppPrompt(
    val id: String = UUID.randomUUID().toString(),
    val tabId: String,
    val appName: String,
    val appPackage: String? = null,
    val appIcon: Bitmap? = null,
    val intentUri: String,
    val isInstalled: Boolean = true,
    val domain: String
)

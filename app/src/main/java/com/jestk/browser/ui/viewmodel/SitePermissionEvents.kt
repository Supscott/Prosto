package com.jestk.browser.ui.viewmodel

import com.jestk.browser.domain.model.SitePermissionChoice
import com.jestk.browser.engine.SitePermissionRequest

sealed interface SitePermissionUiEvent : SafetyUiEvent

/** A page of [tabId] asked for the camera and / or microphone. */
data class OnSitePermissionRequest(val tabId: String, val request: SitePermissionRequest) : SitePermissionUiEvent

/** The page withdrew [request] (navigation or closed tab); its prompt goes away. */
data class OnSitePermissionCanceled(val request: SitePermissionRequest) : SitePermissionUiEvent

/** A button of the camera / microphone prompt (Back and a tap outside send [SitePermissionChoice.BLOCK]). */
data class ResolveSitePermissionPrompt(val choice: SitePermissionChoice) : SitePermissionUiEvent

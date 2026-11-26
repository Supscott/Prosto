package com.jestk.browser.ui.viewmodel

sealed interface NavigationCommand {
    data class LoadUrl(val url: String) : NavigationCommand
    data object GoBack : NavigationCommand
    data object GoForward : NavigationCommand
    data object Reload : NavigationCommand

    /** Reloads the WebView of [tabId], which need not be the active tab. */
    data class ReloadTab(val tabId: String) : NavigationCommand
    data object StopLoading : NavigationCommand
    data object ExitApp : NavigationCommand

    /** Sends the browser to the background, back to the app that opened the link. */
    data object ReturnToCallerApp : NavigationCommand
    data object ExitFullscreen : NavigationCommand
}

package com.jestk.browser.ui.viewmodel

sealed interface NavigationCommand {
    data class LoadUrl(val url: String) : NavigationCommand
    data object GoBack : NavigationCommand
    data object GoForward : NavigationCommand
    data object Reload : NavigationCommand
    data object StopLoading : NavigationCommand
}

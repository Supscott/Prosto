package com.jestk.browser.ui.viewmodel

/** The "group N tabs from a site into a folder" suggestion chip above the dock. */
sealed interface DomainGroupUiEvent : TabUiEvent

/** "Create" on the chip: puts the ungrouped tabs of [domain] in the active workspace into a new folder. */
data class CreateDomainGroup(val domain: String) : DomainGroupUiEvent

/** The chip was dismissed with [tabCount] ungrouped tabs; it stays hidden until the count grows past that. */
data class DismissDomainGroup(val domain: String, val tabCount: Int) : DomainGroupUiEvent

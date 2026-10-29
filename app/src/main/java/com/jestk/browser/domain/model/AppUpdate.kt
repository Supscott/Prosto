package com.jestk.browser.domain.model

/**
 * A GitHub release available for the user to view and download.
 */
data class AppUpdate(
    val versionName: String,
    val releaseNotes: String,
    val releaseUrl: String,
)

/**
 * UI-facing state of the update notification flow.
 */
sealed interface AppUpdateUiState {
    data object None : AppUpdateUiState

    /** A newer release exists and can be offered to the user. */
    data class Available(
        val update: AppUpdate,
    ) : AppUpdateUiState
}

val AppUpdateUiState.isVisible: Boolean
    get() = this !is AppUpdateUiState.None

private data class ParsedVersion(
    val numbers: List<Int>,
    val preRelease: String,
)

/**
 * Compares two version strings ("v1.2.0", "1.2", "1.2.0-beta1").
 * Returns a positive number when [left] is newer than [right],
 * 0 when equal, and negative when [left] is older than [right].
 */
fun compareVersions(
    left: String,
    right: String,
): Int {
    val l = parseVersion(left)
    val r = parseVersion(right)
    val size = maxOf(l.numbers.size, r.numbers.size)
    for (i in 0 until size) {
        val diff = l.numbers.getOrElse(i) { 0 }.compareTo(r.numbers.getOrElse(i) { 0 })
        if (diff != 0) return diff
    }
    return when {
        l.preRelease == r.preRelease -> 0
        l.preRelease.isEmpty() -> 1
        r.preRelease.isEmpty() -> -1
        else -> l.preRelease.compareTo(r.preRelease)
    }
}

private fun parseVersion(raw: String): ParsedVersion {
    val trimmed = raw.trim().removePrefix("v").removePrefix("V")
    val main = trimmed.substringBefore('-').substringBefore('+')
    val preRelease = trimmed.substringAfter('-', "").substringBefore('+')
    return ParsedVersion(
        numbers = main.split('.').map { it.toIntOrNull() ?: 0 },
        preRelease = preRelease,
    )
}

package com.jestk.browser.domain.model

/**
 * A GitHub release that ships an installable APK.
 */
data class AppUpdate(
    val versionName: String,
    val releaseNotes: String,
    val apkUrl: String,
    val releaseUrl: String,
)

/**
 * UI-facing state of the self-update flow.
 */
sealed interface AppUpdateUiState {
    data object None : AppUpdateUiState

    /** A newer release exists but was not downloaded yet (auto-update disabled). */
    data class Available(
        val update: AppUpdate,
    ) : AppUpdateUiState

    /** The APK is being downloaded in the background. */
    data class Downloading(
        val versionName: String,
        val progress: Int,
    ) : AppUpdateUiState

    /** The APK is on disk and can be handed over to the system installer. */
    data class ReadyToInstall(
        val versionName: String,
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
 * Returns a positive number when [left] is newer than [right].
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

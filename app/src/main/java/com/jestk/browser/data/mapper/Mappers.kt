package com.jestk.browser.data.mapper

import com.jestk.browser.data.local.entity.FavoriteEntity
import com.jestk.browser.data.local.entity.HistoryEntity
import com.jestk.browser.data.local.entity.SettingEntity
import com.jestk.browser.data.local.entity.SettingKeys
import com.jestk.browser.data.local.entity.TabEntity
import com.jestk.browser.domain.model.BrowserSettings
import com.jestk.browser.domain.model.FavoriteItem
import com.jestk.browser.domain.model.HistoryItem
import com.jestk.browser.domain.model.SearchEngine
import com.jestk.browser.domain.model.Tab
import com.jestk.browser.domain.model.ThemeMode

fun TabEntity.toDomain(): Tab = Tab(
    id = id,
    url = url,
    title = title,
    lastActiveTimestamp = lastActiveTimestamp,
    snapshotPath = snapshotPath,
    position = position,
    faviconUrl = faviconUrl,
    themeColor = themeColor,
    isIncognito = isIncognito,
    isActive = isActive,
    canGoBack = canGoBack,
    canGoForward = canGoForward
)

fun Tab.toEntity(): TabEntity = TabEntity(
    id = id,
    url = url,
    title = title,
    lastActiveTimestamp = lastActiveTimestamp,
    snapshotPath = snapshotPath,
    position = position,
    faviconUrl = faviconUrl,
    themeColor = themeColor,
    isIncognito = isIncognito,
    isActive = isActive,
    canGoBack = canGoBack,
    canGoForward = canGoForward
)

fun HistoryEntity.toDomain(): HistoryItem = HistoryItem(
    id = id,
    url = url,
    title = title,
    timestamp = timestamp,
    visitCount = visitCount,
    faviconUrl = faviconUrl
)

fun HistoryItem.toEntity(): HistoryEntity = HistoryEntity(
    id = id,
    url = url,
    title = title,
    timestamp = timestamp,
    visitCount = visitCount,
    faviconUrl = faviconUrl
)

fun FavoriteEntity.toDomain(): FavoriteItem = FavoriteItem(
    id = id,
    title = title,
    url = url,
    position = position,
    iconUrl = iconUrl,
    createdAt = createdAt
)

fun FavoriteItem.toEntity(): FavoriteEntity = FavoriteEntity(
    id = id,
    url = url,
    title = title,
    position = position,
    iconUrl = iconUrl,
    createdAt = createdAt
)

fun List<SettingEntity>.toBrowserSettings(): BrowserSettings {
    val map = associate { it.key to it.value }
    val themeMode = map[SettingKeys.THEME_MODE]?.let { raw ->
        runCatching { ThemeMode.valueOf(raw) }.getOrDefault(ThemeMode.SYSTEM)
    } ?: ThemeMode.SYSTEM

    val searchEngine = map[SettingKeys.SEARCH_ENGINE]?.let { raw ->
        runCatching { SearchEngine.valueOf(raw) }.getOrDefault(SearchEngine.DUCKDUCKGO)
    } ?: SearchEngine.DUCKDUCKGO

    val isAdBlockerEnabled = map[SettingKeys.AD_BLOCKER_ENABLED]?.toBooleanStrictOrNull() ?: true
    val isMenuExpandedOnWeb = map[SettingKeys.MENU_EXPANDED_ON_WEB]?.toBooleanStrictOrNull() ?: false

    return BrowserSettings(
        themeMode = themeMode,
        searchEngine = searchEngine,
        isAdBlockerEnabled = isAdBlockerEnabled,
        isMenuExpandedOnWeb = isMenuExpandedOnWeb
    )
}

fun com.jestk.browser.data.local.entity.RecentTabEntity.toDomain(): com.jestk.browser.domain.model.RecentTabItem = com.jestk.browser.domain.model.RecentTabItem(
    url = url,
    title = title,
    snapshotPath = snapshotPath,
    faviconUrl = faviconUrl,
    lastVisitedTimestamp = lastVisitedTimestamp
)

fun com.jestk.browser.domain.model.RecentTabItem.toEntity(): com.jestk.browser.data.local.entity.RecentTabEntity = com.jestk.browser.data.local.entity.RecentTabEntity(
    url = url,
    title = title,
    snapshotPath = snapshotPath,
    faviconUrl = faviconUrl,
    lastVisitedTimestamp = lastVisitedTimestamp
)

fun com.jestk.browser.data.local.entity.DownloadEntity.toDomain(): com.jestk.browser.domain.model.DownloadItem = com.jestk.browser.domain.model.DownloadItem(
    id = id,
    url = url,
    fileName = fileName,
    filePath = filePath,
    mimeType = mimeType,
    contentLength = contentLength,
    timestamp = timestamp,
    downloadId = downloadId,
    status = runCatching { com.jestk.browser.domain.model.DownloadStatus.valueOf(status) }.getOrDefault(com.jestk.browser.domain.model.DownloadStatus.COMPLETED),
    bytesDownloaded = bytesDownloaded,
    totalBytes = if (totalBytes > 0L) totalBytes else contentLength,
    errorMessage = errorMessage,
    etag = etag
)

fun com.jestk.browser.domain.model.DownloadItem.toEntity(): com.jestk.browser.data.local.entity.DownloadEntity = com.jestk.browser.data.local.entity.DownloadEntity(
    id = id,
    url = url,
    fileName = fileName,
    filePath = filePath,
    mimeType = mimeType,
    contentLength = contentLength,
    timestamp = timestamp,
    downloadId = downloadId,
    status = status.name,
    bytesDownloaded = bytesDownloaded,
    totalBytes = totalBytes,
    errorMessage = errorMessage,
    etag = etag
)

fun com.jestk.browser.data.local.entity.SearchEngineEntity.toDomain(): com.jestk.browser.domain.model.SearchEngine = com.jestk.browser.domain.model.SearchEngine(
    id = id,
    displayName = displayName,
    searchUrlTemplate = searchUrlTemplate,
    iconUrl = iconUrl,
    shortcut = shortcut,
    orderPosition = orderPosition,
    isBuiltIn = isBuiltIn
)

fun com.jestk.browser.domain.model.SearchEngine.toEntity(): com.jestk.browser.data.local.entity.SearchEngineEntity = com.jestk.browser.data.local.entity.SearchEngineEntity(
    id = id,
    displayName = displayName,
    searchUrlTemplate = searchUrlTemplate,
    iconUrl = iconUrl,
    shortcut = shortcut,
    orderPosition = orderPosition,
    isBuiltIn = isBuiltIn
)


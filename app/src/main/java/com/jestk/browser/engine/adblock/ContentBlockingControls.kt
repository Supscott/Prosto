package com.jestk.browser.engine.adblock

import android.content.Context
import com.jestk.browser.domain.model.ProtectionLevel
import com.jestk.browser.engine.AdBlockerEngine
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** What the content-blocking settings show. Plain types only, so the UI state stays free of engine classes. */
data class ContentBlockingStatus(
    val level: ProtectionLevel = ProtectionLevel.STANDARD,
    val lastUpdatedAt: Long? = null,
    val ruleCount: Int = 0,
    val isUpdating: Boolean = false,
    val hasError: Boolean = false,
    val strictReady: Boolean = false,
    val cosmeticSupported: Boolean = true,
) {
    /** Strict is chosen but its lists are not all on disk yet, so Standard protection is still active. */
    val isStrictPending: Boolean get() = level == ProtectionLevel.STRICT && !strictReady
}

/** The settings screen's view of the filter-list machinery. */
interface ContentBlockingControls {
    val status: Flow<ContentBlockingStatus>

    /** Persists the level; leaving Strict drops its lists, picking it does not download them (see [updateNow]). */
    suspend fun setLevel(level: ProtectionLevel)

    /** Schedules a refresh of the active lists on any network. */
    fun updateNow()
}

/** [ContentBlockingControls] backed by the real [FilterListController] and WorkManager. */
internal class FilterListControls(
    private val context: Context,
    private val controller: FilterListController,
    private val cosmeticSupported: () -> Boolean = ContentBlockingSetup::isCosmeticSupported,
    private val fallbackRuleCount: () -> Int = AdBlockerEngine::getRulesCount,
) : ContentBlockingControls {
    override val status: Flow<ContentBlockingStatus> =
        controller.state.map { state ->
            ContentBlockingStatus(
                level = if (state.preset == ProtectionPreset.STRICT) ProtectionLevel.STRICT else ProtectionLevel.STANDARD,
                lastUpdatedAt = state.lastUpdatedAt,
                ruleCount = state.ruleCount.takeIf { it > 0 } ?: fallbackRuleCount(),
                isUpdating = state.isUpdating,
                hasError = state.lastError != null,
                strictReady = state.strictReady,
                cosmeticSupported = cosmeticSupported(),
            )
        }

    override suspend fun setLevel(level: ProtectionLevel) {
        controller.setPreset(if (level == ProtectionLevel.STRICT) ProtectionPreset.STRICT else ProtectionPreset.STANDARD)
    }

    override fun updateNow() = FilterListScheduler.updateNow(context)

    companion object {
        fun create(context: Context): ContentBlockingControls =
            FilterListControls(context.applicationContext, FilterListServices.controller(context))
    }
}

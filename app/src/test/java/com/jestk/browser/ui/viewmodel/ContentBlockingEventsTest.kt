package com.jestk.browser.ui.viewmodel

import com.google.common.truth.Truth.assertThat
import com.jestk.browser.domain.model.ProtectionLevel
import com.jestk.browser.engine.adblock.ContentBlockingControls
import com.jestk.browser.engine.adblock.ContentBlockingStatus
import com.jestk.browser.ui.viewmodel.events.ContentBlockingEventHandler
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Test

/** Level changes, "update now" and the status mirror of CB-15, driven through the event handler. */
@OptIn(ExperimentalCoroutinesApi::class)
class ContentBlockingEventsTest {
    private val dispatcher = StandardTestDispatcher()

    private class FakeControls : ContentBlockingControls {
        val flow = MutableStateFlow(ContentBlockingStatus())
        val calls = mutableListOf<String>()
        override val status: Flow<ContentBlockingStatus> = flow

        override suspend fun setLevel(level: ProtectionLevel) {
            calls += "setLevel:$level"
            flow.value = flow.value.copy(level = level)
        }

        override fun updateNow() {
            calls += "updateNow"
        }
    }

    private var state = BrowserUiState()

    private fun TestScope.handler(
        controls: ContentBlockingControls?,
        scope: kotlinx.coroutines.CoroutineScope = this,
    ) = ContentBlockingEventHandler(
        controls = controls,
        updateUiState = { transform -> state = transform(state) },
        scope = scope,
        ioDispatcher = dispatcher,
    )

    @Test
    fun strictSavesTheLevelThenStartsTheDownload() = runTest(dispatcher) {
        val controls = FakeControls()
        handler(controls).handle(SetProtectionLevel(ProtectionLevel.STRICT))
        advanceUntilIdle()

        assertThat(controls.calls).containsExactly("setLevel:STRICT", "updateNow").inOrder()
    }

    @Test
    fun standardOnlyRecompilesWithoutDownloading() = runTest(dispatcher) {
        val controls = FakeControls()
        handler(controls).handle(SetProtectionLevel(ProtectionLevel.STANDARD))
        advanceUntilIdle()

        assertThat(controls.calls).containsExactly("setLevel:STANDARD")
    }

    @Test
    fun updateNowSchedulesTheWorker() = runTest(dispatcher) {
        val controls = FakeControls()
        handler(controls).handle(UpdateFilterListsNow)

        assertThat(controls.calls).containsExactly("updateNow")
    }

    @Test
    fun statusIsMirroredIntoTheUiState() = runTest(dispatcher) {
        val controls = FakeControls()
        val observerScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + dispatcher)
        handler(controls, observerScope).observe()
        advanceUntilIdle()
        controls.flow.value = ContentBlockingStatus(
            level = ProtectionLevel.STRICT,
            ruleCount = 120_000,
            isUpdating = true,
            lastUpdatedAt = 5L,
        )
        advanceUntilIdle()
        observerScope.cancel()

        assertThat(state.contentBlocking.level).isEqualTo(ProtectionLevel.STRICT)
        assertThat(state.contentBlocking.ruleCount).isEqualTo(120_000)
        assertThat(state.contentBlocking.isStrictPending).isTrue()
    }

    @Test
    fun strictIsNotPendingOnceTheListsAreReady() {
        val status = ContentBlockingStatus(level = ProtectionLevel.STRICT, strictReady = true)

        assertThat(status.isStrictPending).isFalse()
    }

    @Test
    fun withoutControlsEventsAreIgnored() = runTest(dispatcher) {
        val handler = handler(null, backgroundScope)
        handler.observe()
        handler.handle(UpdateFilterListsNow)
        handler.handle(SetProtectionLevel(ProtectionLevel.STRICT))
        advanceUntilIdle()

        assertThat(state.contentBlocking).isEqualTo(ContentBlockingStatus())
    }
}

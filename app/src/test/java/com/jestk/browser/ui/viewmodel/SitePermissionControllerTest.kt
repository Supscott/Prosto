package com.jestk.browser.ui.viewmodel

import com.google.common.truth.Truth.assertThat
import com.jestk.browser.domain.model.OverlayType
import com.jestk.browser.domain.model.SitePermissionChoice
import com.jestk.browser.domain.model.SiteResource
import com.jestk.browser.domain.model.Tab
import com.jestk.browser.domain.repository.SitePermissionStore
import com.jestk.browser.engine.SitePermissionRequest
import org.junit.Test

private const val ORIGIN = "https://example.com"

private class FakeRequest(
    override val resources: Set<SiteResource>,
    override val origin: String = ORIGIN,
    override val host: String = "example.com",
) : SitePermissionRequest {
    var granted: Set<SiteResource>? = null
    var denied = false

    override fun grant(allowed: Set<SiteResource>) {
        granted = allowed
    }

    override fun deny() {
        denied = true
    }
}

private class FakeStore(initial: Map<String, Set<SiteResource>> = emptyMap()) : SitePermissionStore {
    val grants = initial.toMutableMap()
    var reads = 0
    var writes = 0

    override fun isAllowed(origin: String, resource: SiteResource): Boolean {
        reads++
        return grants[origin].orEmpty().contains(resource)
    }

    override fun allow(origin: String, resources: Set<SiteResource>) {
        writes++
        grants[origin] = grants[origin].orEmpty() + resources
    }

    override fun clear(origin: String) {
        grants.remove(origin)
    }

    override fun clearAll() = grants.clear()
}

private class FakeOs(
    private val held: MutableSet<SiteResource> = mutableSetOf(),
    private val userGrants: Set<SiteResource> = emptySet(),
    private val answerAtOnce: Boolean = true,
) : SiteOsPermissions {
    var asked: Set<SiteResource>? = null
    private var pendingResult: ((Set<SiteResource>) -> Unit)? = null

    override fun isHeld(resource: SiteResource) = resource in held

    override fun request(missing: Set<SiteResource>, onResult: (granted: Set<SiteResource>) -> Unit) {
        asked = missing
        if (answerAtOnce) onResult(missing.intersect(userGrants)) else pendingResult = onResult
    }

    fun answerNow() = pendingResult?.invoke(asked.orEmpty().intersect(userGrants))
}

class SitePermissionControllerTest {
    private val both = setOf(SiteResource.CAMERA, SiteResource.MICROPHONE)
    private var state = BrowserUiState(tabs = listOf(Tab(id = "t1")), activeTabId = "t1")
    private var store: FakeStore? = FakeStore()

    private fun controller(os: SiteOsPermissions? = FakeOs(held = both.toMutableSet())) =
        SitePermissionController(store, { state }, { transform -> state = transform(state) })
            .also { it.osPermissions = os }

    @Test
    fun storedGrant_isGrantedWithoutPrompt() {
        store = FakeStore(mapOf(ORIGIN to both))
        val request = FakeRequest(both)

        controller().onRequest("t1", request)

        assertThat(request.granted).isEqualTo(both)
        assertThat(state.sitePermissionPrompt).isNull()
        assertThat(store!!.writes).isEqualTo(0)
    }

    @Test
    fun storedGrantForOneResource_stillPromptsForBoth() {
        store = FakeStore(mapOf(ORIGIN to setOf(SiteResource.CAMERA)))
        val request = FakeRequest(both)

        controller().onRequest("t1", request)

        assertThat(request.granted).isNull()
        assertThat(request.denied).isFalse()
        assertThat(state.sitePermissionPrompt?.resources).isEqualTo(both)
        assertThat(state.sitePermissionPrompt?.canRemember).isTrue()
    }

    @Test
    fun noStoredGrant_showsPromptWithHostAndWaits() {
        val request = FakeRequest(setOf(SiteResource.MICROPHONE))

        controller().onRequest("t1", request)

        val prompt = state.sitePermissionPrompt!!
        assertThat(prompt.host).isEqualTo("example.com")
        assertThat(prompt.tabId).isEqualTo("t1")
        assertThat(prompt.resources).containsExactly(SiteResource.MICROPHONE)
        assertThat(request.granted).isNull()
        assertThat(request.denied).isFalse()
    }

    @Test
    fun incognitoTab_neverReadsOrWritesTheStore() {
        store = FakeStore(mapOf(ORIGIN to both))
        state = state.copy(tabs = listOf(Tab(id = "t1", isIncognito = true)))
        val request = FakeRequest(both)
        val controller = controller()

        controller.onRequest("t1", request)

        assertThat(state.sitePermissionPrompt?.canRemember).isFalse()
        assertThat(request.granted).isNull()

        controller.resolve(SitePermissionChoice.ALLOW_ALWAYS)

        assertThat(request.granted).isEqualTo(both)
        assertThat(store!!.reads).isEqualTo(0)
        assertThat(store!!.writes).isEqualTo(0)
    }

    @Test
    fun backgroundTab_isDeniedWithoutPrompt() {
        state = state.copy(tabs = listOf(Tab(id = "t1"), Tab(id = "t2")))
        val request = FakeRequest(both)

        controller().onRequest("t2", request)

        assertThat(request.denied).isTrue()
        assertThat(state.sitePermissionPrompt).isNull()
    }

    @Test
    fun hiddenPage_isDenied() {
        state = state.copy(activeOverlay = OverlayType.entries.first { it != OverlayType.NONE })
        val request = FakeRequest(both)

        controller().onRequest("t1", request)

        assertThat(request.denied).isTrue()
    }

    @Test
    fun unknownOrigin_andEmptyResources_areDenied() {
        val noOrigin = FakeRequest(both, origin = "")
        val noResources = FakeRequest(emptySet())
        val controller = controller()

        controller.onRequest("t1", noOrigin)
        controller.onRequest("t1", noResources)

        assertThat(noOrigin.denied).isTrue()
        assertThat(noResources.denied).isTrue()
    }

    @Test
    fun secondRequest_whilePromptShowing_isDenied_andFirstKeepsWaiting() {
        val first = FakeRequest(both)
        val second = FakeRequest(setOf(SiteResource.CAMERA))
        val controller = controller()

        controller.onRequest("t1", first)
        controller.onRequest("t1", second)

        assertThat(second.denied).isTrue()
        assertThat(first.denied).isFalse()
        assertThat(state.sitePermissionPrompt?.resources).isEqualTo(both)
    }

    @Test
    fun block_deniesThisRequestOnly_andRemembersNothing() {
        val first = FakeRequest(both)
        val controller = controller()
        controller.onRequest("t1", first)

        controller.resolve(SitePermissionChoice.BLOCK)

        assertThat(first.denied).isTrue()
        assertThat(state.sitePermissionPrompt).isNull()
        assertThat(store!!.writes).isEqualTo(0)
        val next = FakeRequest(both)
        controller.onRequest("t1", next)
        assertThat(state.sitePermissionPrompt).isNotNull()
        assertThat(next.denied).isFalse()
    }

    @Test
    fun allowOnce_grantsWithoutStoring() {
        val request = FakeRequest(both)
        val controller = controller()
        controller.onRequest("t1", request)

        controller.resolve(SitePermissionChoice.ALLOW_ONCE)

        assertThat(request.granted).isEqualTo(both)
        assertThat(state.sitePermissionPrompt).isNull()
        assertThat(store!!.writes).isEqualTo(0)
    }

    @Test
    fun allowAlways_grantsAndStoresPerOrigin() {
        val request = FakeRequest(setOf(SiteResource.CAMERA))
        val controller = controller()
        controller.onRequest("t1", request)

        controller.resolve(SitePermissionChoice.ALLOW_ALWAYS)

        assertThat(request.granted).containsExactly(SiteResource.CAMERA)
        assertThat(store!!.isAllowed(ORIGIN, SiteResource.CAMERA)).isTrue()
        assertThat(store!!.isAllowed(ORIGIN, SiteResource.MICROPHONE)).isFalse()
        assertThat(store!!.isAllowed("https://other.com", SiteResource.CAMERA)).isFalse()
    }

    @Test
    fun osDenial_deniesAndDoesNotPersist() {
        val os = FakeOs(userGrants = emptySet())
        val request = FakeRequest(both)
        val controller = controller(os)
        controller.onRequest("t1", request)

        controller.resolve(SitePermissionChoice.ALLOW_ALWAYS)

        assertThat(os.asked).isEqualTo(both)
        assertThat(request.denied).isTrue()
        assertThat(request.granted).isNull()
        assertThat(store!!.writes).isEqualTo(0)
    }

    @Test
    fun mixedOsResult_grantsTheAllowedSubset_andStoresOnlyThat() {
        val os = FakeOs(held = mutableSetOf(SiteResource.CAMERA), userGrants = emptySet())
        val request = FakeRequest(both)
        val controller = controller(os)
        controller.onRequest("t1", request)

        controller.resolve(SitePermissionChoice.ALLOW_ALWAYS)

        assertThat(os.asked).containsExactly(SiteResource.MICROPHONE)
        assertThat(request.granted).containsExactly(SiteResource.CAMERA)
        assertThat(store!!.grants[ORIGIN]).containsExactly(SiteResource.CAMERA)
    }

    @Test
    fun osAsked_onlyForTheMissingResource() {
        val os = FakeOs(held = mutableSetOf(SiteResource.MICROPHONE), userGrants = setOf(SiteResource.CAMERA))
        val request = FakeRequest(both)
        val controller = controller(os)
        controller.onRequest("t1", request)

        controller.resolve(SitePermissionChoice.ALLOW_ONCE)

        assertThat(os.asked).containsExactly(SiteResource.CAMERA)
        assertThat(request.granted).isEqualTo(both)
    }

    @Test
    fun withoutOsGateway_requestIsDenied() {
        val request = FakeRequest(both)
        val controller = controller(os = null)
        controller.onRequest("t1", request)

        controller.resolve(SitePermissionChoice.ALLOW_ONCE)

        assertThat(request.denied).isTrue()
    }

    @Test
    fun cancelledRequest_dropsThePrompt_andNothingIsAnswered() {
        val request = FakeRequest(both)
        val controller = controller()
        controller.onRequest("t1", request)

        controller.onCanceled(request)
        controller.resolve(SitePermissionChoice.ALLOW_ONCE)

        assertThat(state.sitePermissionPrompt).isNull()
        assertThat(request.granted).isNull()
        assertThat(request.denied).isFalse()
        val next = FakeRequest(both)
        controller.onRequest("t1", next)
        assertThat(state.sitePermissionPrompt).isNotNull()
    }

    @Test
    fun cancelledWhileSystemDialogIsUp_ignoresTheLateResult() {
        val os = FakeOs(userGrants = both, answerAtOnce = false)
        val request = FakeRequest(both)
        val controller = controller(os)
        controller.onRequest("t1", request)
        controller.resolve(SitePermissionChoice.ALLOW_ONCE)

        controller.onCanceled(request)
        os.answerNow()

        assertThat(request.granted).isNull()
        assertThat(request.denied).isFalse()
    }

    @Test
    fun replacedOsGateway_deniesTheRequestWaitingOnTheSystemDialog() {
        val os = FakeOs(userGrants = both, answerAtOnce = false)
        val request = FakeRequest(both)
        val controller = controller(os)
        controller.onRequest("t1", request)
        controller.resolve(SitePermissionChoice.ALLOW_ONCE)

        controller.osPermissions = FakeOs()
        os.answerNow()

        assertThat(request.denied).isTrue()
        assertThat(request.granted).isNull()
    }

    @Test
    fun tabClosedOrSwitchedAway_deniesTheOpenPrompt() {
        val request = FakeRequest(both)
        val controller = controller()
        controller.onRequest("t1", request)

        controller.onStateChanged(state.copy(activeTabId = "t2", tabs = listOf(Tab(id = "t1"), Tab(id = "t2"))))

        assertThat(request.denied).isTrue()
        assertThat(state.sitePermissionPrompt).isNull()
    }

    @Test
    fun unrelatedStateChange_keepsThePrompt() {
        val request = FakeRequest(both)
        val controller = controller()
        controller.onRequest("t1", request)

        controller.onStateChanged(state.copy(loadingProgress = 0.5f))

        assertThat(request.denied).isFalse()
        assertThat(state.sitePermissionPrompt).isNotNull()
    }
}

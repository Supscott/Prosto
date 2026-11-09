package com.jestk.browser.engine

import android.app.Activity
import android.content.Context
import android.webkit.CookieManager
import android.webkit.GeolocationPermissions
import android.webkit.ServiceWorkerController
import android.webkit.WebStorage
import androidx.test.core.app.ApplicationProvider
import androidx.webkit.Profile
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WebViewPoolMultiProfileTest {

    private lateinit var appContext: Context
    private lateinit var webViewPool: WebViewPool

    private class FakeProfile(
        private val name: String = "fake_profile"
    ) : Profile {
        override fun getName(): String = name
        override fun getCookieManager(): CookieManager = CookieManager.getInstance()
        override fun getWebStorage(): WebStorage = WebStorage.getInstance()
        override fun getGeolocationPermissions(): GeolocationPermissions = GeolocationPermissions.getInstance()
        override fun getServiceWorkerController(): ServiceWorkerController = ServiceWorkerController.getInstance()
    }

    @Before
    fun setUp() {
        appContext = ApplicationProvider.getApplicationContext()
        webViewPool = WebViewPool(appContext)
    }

    @Test
    fun getOrCreateWebView_whenMultiProfileSupported_retrievesAndAppliesProfile() {
        var requestedProfileName: String? = null
        var appliedProfileName: String? = null
        val fakeProfile = FakeProfile("work_profile")

        webViewPool.isMultiProfileSupported = { true }
        webViewPool.profileProvider = { name ->
            requestedProfileName = name
            fakeProfile
        }
        webViewPool.profileApplier = { _, name ->
            appliedProfileName = name
        }

        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val webView = webViewPool.getOrCreateWebView("tab-work-1", activity, profileName = "work_profile")

        assertThat(webView).isNotNull()
        assertThat(requestedProfileName).isEqualTo("work_profile")
        assertThat(appliedProfileName).isEqualTo("work_profile")
        assertThat(webViewPool.getProfileForTab("tab-work-1")).isEqualTo("work_profile")
    }

    @Test
    fun getOrCreateWebView_whenMultiProfileUnsupported_skipsProfileAssignmentGracefully() {
        var providerInvoked = false
        var applierInvoked = false

        webViewPool.isMultiProfileSupported = { false }
        webViewPool.profileProvider = { _ ->
            providerInvoked = true
            null
        }
        webViewPool.profileApplier = { _, _ ->
            applierInvoked = true
        }

        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val webView = webViewPool.getOrCreateWebView("tab-unsupported", activity, profileName = "work_profile")

        assertThat(webView).isNotNull()
        assertThat(providerInvoked).isFalse()
        assertThat(applierInvoked).isFalse()
    }

    @Test
    fun getOrCreateWebView_whenProfileNameNull_doesNotInvokeProfileHooks() {
        var providerInvoked = false
        var applierInvoked = false

        webViewPool.isMultiProfileSupported = { true }
        webViewPool.profileProvider = { _ ->
            providerInvoked = true
            null
        }
        webViewPool.profileApplier = { _, _ ->
            applierInvoked = true
        }

        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val webView = webViewPool.getOrCreateWebView("tab-default", activity, profileName = null)

        assertThat(webView).isNotNull()
        assertThat(providerInvoked).isFalse()
        assertThat(applierInvoked).isFalse()
        assertThat(webViewPool.getProfileForTab("tab-default")).isNull()
    }

    @Test
    fun getOrCreateWebView_whenProfileApplierThrows_degradesGracefullyWithoutCrashing() {
        webViewPool.isMultiProfileSupported = { true }
        webViewPool.profileProvider = { FakeProfile() }
        webViewPool.profileApplier = { _, _ ->
            throw IllegalStateException("Simulated profile assignment failure")
        }

        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val webView = webViewPool.getOrCreateWebView("tab-throws", activity, profileName = "throw_profile")

        assertThat(webView).isNotNull()
        assertThat(webViewPool.getWebView("tab-throws")).isSameInstanceAs(webView)
    }

    @Test
    fun deleteProfile_whenMultiProfileSupported_evictsAssociatedWebViewsAndDeletesProfile() {
        val deletedProfiles = mutableListOf<String>()
        webViewPool.isMultiProfileSupported = { true }
        webViewPool.profileProvider = { FakeProfile(it) }
        webViewPool.profileApplier = { _, _ -> }
        webViewPool.profileDeleter = { name ->
            deletedProfiles.add(name)
            true
        }

        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        webViewPool.getOrCreateWebView("tab-work-1", activity, profileName = "work_profile")
        webViewPool.getOrCreateWebView("tab-work-2", activity, profileName = "work_profile")
        webViewPool.getOrCreateWebView("tab-personal-1", activity, profileName = "personal_profile")

        assertThat(webViewPool.size()).isEqualTo(3)
        assertThat(webViewPool.getWebView("tab-work-1")).isNotNull()
        assertThat(webViewPool.getWebView("tab-work-2")).isNotNull()
        assertThat(webViewPool.getWebView("tab-personal-1")).isNotNull()

        val success = webViewPool.deleteProfile("work_profile")

        assertThat(success).isTrue()
        assertThat(deletedProfiles).containsExactly("work_profile")
        assertThat(webViewPool.size()).isEqualTo(1)
        assertThat(webViewPool.getWebView("tab-work-1")).isNull()
        assertThat(webViewPool.getWebView("tab-work-2")).isNull()
        assertThat(webViewPool.getWebView("tab-personal-1")).isNotNull()
        assertThat(webViewPool.getProfileForTab("tab-work-1")).isNull()
        assertThat(webViewPool.getProfileForTab("tab-personal-1")).isEqualTo("personal_profile")
    }

    @Test
    fun deleteProfile_whenMultiProfileUnsupported_returnsFalseAndDoesNotCallDeleter() {
        var deleterInvoked = false
        webViewPool.isMultiProfileSupported = { false }
        webViewPool.profileDeleter = {
            deleterInvoked = true
            true
        }

        val success = webViewPool.deleteProfile("work_profile")

        assertThat(success).isFalse()
        assertThat(deleterInvoked).isFalse()
    }

    @Test
    fun deleteProfile_whenProfileDeleterThrows_returnsFalseGracefully() {
        webViewPool.isMultiProfileSupported = { true }
        webViewPool.profileDeleter = {
            throw IllegalArgumentException("Cannot delete default profile")
        }

        val success = webViewPool.deleteProfile("default")

        assertThat(success).isFalse()
    }

    @Test
    fun getOrCreateWebView_whenProfileChangedForSameTabId_recreatesWebView() {
        webViewPool.isMultiProfileSupported = { true }
        webViewPool.profileProvider = { FakeProfile(it) }
        webViewPool.profileApplier = { _, _ -> }

        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val originalWebView = webViewPool.getOrCreateWebView("tab-dynamic", activity, profileName = "personal_profile")
        assertThat(webViewPool.getProfileForTab("tab-dynamic")).isEqualTo("personal_profile")

        // Re-request with same profile: returns cached instance
        val reusedWebView = webViewPool.getOrCreateWebView("tab-dynamic", activity, profileName = "personal_profile")
        assertThat(reusedWebView).isSameInstanceAs(originalWebView)

        // Re-request with different profile: evicts old and creates fresh instance
        val recreatedWebView = webViewPool.getOrCreateWebView("tab-dynamic", activity, profileName = "work_profile")
        assertThat(recreatedWebView).isNotSameInstanceAs(originalWebView)
        assertThat(webViewPool.getProfileForTab("tab-dynamic")).isEqualTo("work_profile")
    }
}
